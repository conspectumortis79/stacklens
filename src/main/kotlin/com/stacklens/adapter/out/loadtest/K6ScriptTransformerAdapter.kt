package com.stacklens.adapter.out.loadtest

import com.stacklens.application.port.out.loadtest.K6ScriptPort
import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class K6ScriptTransformerAdapter(
    private val localBaseUrl: LocalBaseUrlPort,
) : K6ScriptPort {
    private val log = LoggerFactory.getLogger(K6ScriptTransformerAdapter::class.java)

    override fun patchScriptForLocalDefault(
        script: String,
        forcedReplacement: String?,
    ): String {
        val current =
            extractBaseUrlLiteral(script) ?: return script.also {
                log.debug("[k6] patchScriptForLocalDefault: no BASE_URL literal found in script")
            }
        val replacement = forcedReplacement ?: localBaseUrl.currentBaseUrl()
        if (replacement == current) {
            log.info("[k6] BASE_URL already matches local instance URL '{}' — no patch needed", current)
            return script
        }
        val suspicious = isSuspiciousTemplateDefault(current)
        log.info(
            "[k6] Patching AI-generated BASE_URL '{}' (suspicious={}) to local instance URL '{}'",
            current,
            suspicious,
            replacement,
        )
        val patched =
            script.replace(
                Regex("""(?m)(const|let|var)\s+BASE_URL\s*=\s*(?:__ENV\.BASE_URL\s*\|\|\s*)?['"][^'"]*['"]"""),
                "const BASE_URL = '$replacement'",
            )
        log.info(
            "[k6] Patched BASE_URL verification: expected='{}' actual='{}'",
            replacement,
            verifyPatchedBaseUrl(patched),
        )
        return patched
    }

    internal fun verifyPatchedBaseUrl(patched: String): String =
        Regex("""const\s+BASE_URL\s*=\s*['"]([^'"]*)['"]""")
            .find(patched)
            ?.groupValues
            ?.get(1)
            ?: "<not found>"

    override fun extractBaseUrlLiteral(script: String): String? {
        val match =
            Regex(
                """(?m)(?:const|let|var)\s+BASE_URL\s*=\s*(?:__ENV\.BASE_URL\s*\|\|\s*)?['"]([^'"]*)['"]""",
            ).find(script) ?: return null
        return match.groupValues[1].ifBlank { null }
    }

    override fun isSuspiciousTemplateDefault(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return true
        val match = Regex("""^(https?)://([^/:]+)(?::(\d+))?(/.*)?$""").matchEntire(trimmed) ?: return true
        val (_, host, port, _) = match.destructured
        if (host != "localhost" && host != "127.0.0.1" && host != "invalid") return false
        val portStr = port.ifEmpty { "80" }
        if (portStr == PRODUCT_DEFAULT_PORT || portStr == "0" || portStr == "80" || portStr == "443") {
            return true
        }
        val localPort = extractLocalPortOrNull()
        if (localPort != null && portStr != localPort) return true
        return false
    }

    private fun extractLocalPortOrNull(): String? {
        val url = localBaseUrl.currentBaseUrl()
        return Regex(""":(\d+)/?$""").find(url)?.groupValues?.get(1)
    }

    override fun moveTopLevelStagesIntoDefaultScenario(script: String): String {
        val openRegex = Regex("""(export\s+const\s+options\s*=\s*\{\s*\n)""")
        val openMatch = openRegex.find(script) ?: return script
        val bodyStart = openMatch.range.last

        val closeRegex = Regex("""^\}\s*;\s*$""", RegexOption.MULTILINE)
        val closeMatch = closeRegex.find(script, bodyStart) ?: return script
        val bodyEnd = closeMatch.range.first
        val body = script.substring(bodyStart, bodyEnd)
        val hasTopStages = Regex("""(?m)^\s*stages\s*:""").containsMatchIn(body)
        val hasTopScenarios = Regex("""(?m)^\s*scenarios\s*:""").containsMatchIn(body)
        if (!hasTopStages || !hasTopScenarios) return script

        val hasDefaultScenario = Regex("""(?m)^\s*default\s*:\s*\{""").containsMatchIn(body)
        if (hasDefaultScenario) return script

        val scenarioFields =
            listOf(
                "stages",
                "startVUs",
                "vus",
                "duration",
                "iterations",
                "startTime",
                "exec",
                "executor",
            )
        val bodyLines = body.split('\n').toMutableList()
        val extracted = mutableListOf<String>()
        val remaining = mutableListOf<String>()
        var i = 0
        var inScenariosBlock = false
        var scenariosDepth = 0
        while (i < bodyLines.size) {
            val line = bodyLines[i]
            if (!inScenariosBlock &&
                Regex("""^\s*scenarios\s*:\s*\{""").containsMatchIn(line)
            ) {
                inScenariosBlock = true
                scenariosDepth = 1
                remaining.add(line)
                i++
                continue
            }
            if (inScenariosBlock) {
                scenariosDepth += line.count { it == '{' } - line.count { it == '}' }
                if (scenariosDepth <= 0) inScenariosBlock = false
                remaining.add(line)
                i++
                continue
            }
            val keyMatch = Regex("""^\s*(\w+)\s*:""").find(line)
            if (keyMatch == null || keyMatch.groupValues[1] !in scenarioFields) {
                remaining.add(line)
                i++
                continue
            }
            val collected = mutableListOf(line)
            val startsArray = line.substringAfter(":").contains('[')
            val startsObject = line.substringAfter(":").contains('{')
            if (startsArray || startsObject) {
                var depth =
                    line.count { it == '[' } - line.count { it == ']' } +
                        line.count { it == '{' } - line.count { it == '}' }
                while (depth > 0 && i + 1 < bodyLines.size) {
                    i++
                    val next = bodyLines[i]
                    collected.add(next)
                    depth +=
                        next.count { it == '[' } - next.count { it == ']' } +
                        next.count { it == '{' } - next.count { it == '}' }
                }
            }
            extracted.addAll(collected)
            i++
        }
        if (extracted.isEmpty()) return script

        val executorLine =
            extracted.firstOrNull { it.trim().startsWith("executor") }
        val resolvedExecutor =
            when {
                executorLine != null -> executorLine
                extracted.any { it.trim().startsWith("stages") } ->
                    "    executor: 'ramping-vus',"
                extracted.any { it.trim().startsWith("vus") || it.trim().startsWith("duration") } ->
                    "    executor: 'constant-vus',"
                else -> "    executor: 'ramping-vus',"
            }

        val scenarioBody =
            buildList {
                if (executorLine != null) add(executorLine) else add(resolvedExecutor)
                for (line in extracted) {
                    if (line === executorLine) continue
                    if (line.trim().startsWith("executor")) continue
                    if (line.trim().startsWith("exec")) continue
                    add(line)
                }
                if (extracted.none { it.trim().startsWith("exec") }) {
                    add("    exec: 'default',")
                }
            }
        val bodyWithoutExtracted = remaining.joinToString("\n")

        val scenariosOpenRegex = Regex("""(?m)^\s*scenarios\s*:\s*\{""")
        val scenariosOpen = scenariosOpenRegex.find(bodyWithoutExtracted) ?: return script
        val scenariosStart = scenariosOpen.range.last + 1
        var depth = 1
        var pos = scenariosStart
        while (pos < bodyWithoutExtracted.length && depth > 0) {
            val ch = bodyWithoutExtracted[pos]
            if (ch == '{') {
                depth++
            } else if (ch == '}') {
                depth--
            }
            pos++
        }

        val inner = bodyWithoutExtracted.substring(scenariosStart, pos - 1)
        val afterScenarios = bodyWithoutExtracted.substring(pos)
        val innerLastNonWs = inner.lineSequence().lastOrNull { it.isNotBlank() } ?: ""
        val needsTrailingComma = inner.isNotBlank() && !innerLastNonWs.trimEnd().endsWith(',')
        val newBody =
            buildString {
                append(bodyWithoutExtracted, 0, scenariosStart)
                append('\n')
                append(inner)
                if (needsTrailingComma) append(',')
                append("\n    default: {\n")
                for (line in scenarioBody) append(line).append('\n')
                append("    },\n}")
                append(afterScenarios)
            }

        log.info(
            "[k6] Moved {} top-level executor field(s) into `scenarios.default` — k6 v2.x rejects `stages` + `scenarios` coexisting on the top level (exit 104, 0-byte output).",
            scenarioBody.size,
        )
        return script.substring(0, bodyStart) + newBody + script.substring(bodyEnd)
    }

    override fun sanitizeScriptForK6V2(script: String): String {
        var result = script

        result = moveTopLevelStagesIntoDefaultScenario(result)

        val scenarioRampDowns =
            Regex(
                """(?m)^\s*gracefulRampDown\s*:\s*['"]([^'"]+)['"]\s*,?\s*$""",
            ).findAll(result)
                .map { it.groupValues[1] }
                .toList()

        if (scenarioRampDowns.isNotEmpty()) {
            val before = result.length
            result =
                result.replace(
                    Regex(
                        """(?m)[ \t]*gracefulRampDown\s*:\s*['"][^'"]+['"][ \t]*,?[ \t]*\r?\n""",
                    ),
                    "",
                )

            result =
                result.replace(
                    Regex(""",(\s*\n[ \t]*[}\]])"""),
                    "\$1",
                )
            log.info(
                "[k6] Stripped {} per-scenario `gracefulRampDown` ({} chars removed) — k6 v2.0 rejected it with exit 104 in the previous run.",
                scenarioRampDowns.size,
                before - result.length,
            )
        }

        if (scenarioRampDowns.isNotEmpty() && !hasTopLevelGracefulRampDown(result)) {
            val promoted = scenarioRampDowns.maxBy { parseK6DurationSeconds(it) ?: 0.0 }
            result = promoteGracefulRampDownToTopLevel(result, promoted)
        }

        if (!hasTopLevelGracefulStop(result)) {
            result = injectTopLevelGracefulStop(result, "5s")
        }

        return result
    }

    private fun hasTopLevelGracefulRampDown(script: String): Boolean =
        Regex(
            """(?m)^\s*gracefulRampDown\s*:\s*['"][^'"]+['"]""",
        ).containsMatchIn(script)

    private fun hasTopLevelGracefulStop(script: String): Boolean =
        Regex(
            """(?m)^\s*gracefulStop\s*:\s*['"][^'"]+['"]""",
        ).containsMatchIn(script)

    private fun injectTopLevelGracefulStop(
        script: String,
        value: String,
    ): String {
        val match = Regex("""(export\s+const\s+options\s*=\s*\{\s*\n)""").find(script) ?: return script
        val insertAt = match.range.last + 1
        val insertion = "  gracefulStop: '$value',\n"
        return script.substring(0, insertAt) + insertion + script.substring(insertAt)
    }

    private fun promoteGracefulRampDownToTopLevel(
        script: String,
        value: String,
    ): String {
        val match = Regex("""(export\s+const\s+options\s*=\s*\{\s*\n)""").find(script) ?: return script
        val insertAt = match.range.last + 1
        val insertion = "  gracefulRampDown: '$value',\n"
        return script.substring(0, insertAt) + insertion + script.substring(insertAt)
    }

    fun parseK6DurationSeconds(value: String): Double? {
        val regex = Regex("""^\s*(\d+)\s*(ms|s|m|h)\s*$""")
        val total =
            regex.findAll(value).fold(0.0) { acc, m ->
                val n = m.groupValues[1].toDouble()
                acc +
                    when (m.groupValues[2]) {
                        "ms" -> n / 1000.0
                        "s" -> n
                        "m" -> n * 60.0
                        else -> n * 3600.0
                    }
            }
        return if (total > 0.0) total else null
    }

    private companion object {
        const val PRODUCT_DEFAULT_PORT = "8085"
    }
}
