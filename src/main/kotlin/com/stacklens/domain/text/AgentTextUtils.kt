package com.stacklens.domain.text

object AgentTextUtils {
    val agentOutputBlockRe: Regex =
        Regex("""###[ \t]+(\S+)\s*\n```[a-zA-Z]*\n([\s\S]*?)```""")

    data class ParsedFile(
        val path: String,
        val content: String,
    )

    fun cleanPath(raw: String): String =
        raw
            .trim()
            .trim('`', '\'', '"')
            .trimEnd(':', ',', ';')
            .trim()

    fun parseAgentOutput(output: String): List<ParsedFile> =
        agentOutputBlockRe
            .findAll(output)
            .map { ParsedFile(cleanPath(it.groupValues[1]), it.groupValues[2]) }
            .filter { pf ->
                pf.path.isNotBlank() &&
                    !pf.path.startsWith("/") &&
                    java.nio.file.Path
                        .of(pf.path)
                        .normalize()
                        .none { it.toString() == ".." }
            }.toList()

    fun sanitiseDetectedTicketKey(raw: String): String? {
        val firstLine =
            raw
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("```") }
                .firstOrNull()
                ?: return null
        val cleaned =
            firstLine
                .removePrefix("Output:")
                .removePrefix("output:")
                .trim()
                .trim('"', '\'', '`', '.', ',', ';', ':', '!', '?', '(', ')', '[', ']')
                .trim()
        if (cleaned.isBlank()) return null
        if (cleaned.equals("NONE", ignoreCase = true)) return null
        if (cleaned.length > 80 || cleaned.contains(Regex("\\s"))) return null
        if (cleaned.contains('/') || cleaned.contains('\\') || cleaned.contains("..")) return null
        return cleaned
    }

    fun generateFeatureSlug(rawText: String): String {
        val words =
            rawText
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(6)
                .map { it.lowercase().replace(Regex("[^a-z0-9]"), "") }
                .filter { it.isNotEmpty() }
        val slug = joinWordsWithLimit(words, 60)
        return slug.ifBlank { "feature" }
    }

    fun joinWordsWithLimit(
        words: List<String>,
        maxLen: Int,
    ): String {
        if (words.isEmpty()) return ""
        val sb = StringBuilder()
        for (w in words) {
            val candidate = if (sb.isEmpty()) w else "$sb-$w"
            if (candidate.length > maxLen) break
            sb.clear()
            sb.append(candidate)
        }
        if (sb.isEmpty() && words.first().isNotEmpty()) {
            sb.append(words.first().take(maxLen))
        }
        return sb.toString().trimEnd('-')
    }

    fun extractSlugFromNoraSummary(noraOutput: String): String {
        val summaryText =
            Regex(
                """(?:^|\n)(?:\*\*Summary\*\*[:：]?\s*|##\s+Summary\s*\n)([^\n]+)""",
                RegexOption.IGNORE_CASE,
            ).find(noraOutput)?.groupValues?.getOrNull(1)?.trim() ?: return ""
        val words =
            summaryText
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(6)
                .map { it.lowercase().replace(Regex("[^a-z0-9]"), "") }
                .filter { it.isNotEmpty() }
        return joinWordsWithLimit(words, 60)
    }

    fun parseCoveragePercent(output: String): Double? =
        Regex("""Lines\s*:\s*([\d.]+)%""")
            .find(output)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()

    fun normaliseBadgeToFirstLine(output: String): String {
        val badgeEmojis = listOf("🧪", "🚀", "🔀", "🗑️")
        val lines = output.lines()
        val firstNonBlankIdx = lines.indexOfFirst { it.isNotBlank() }
        if (firstNonBlankIdx == -1) return output
        if (badgeEmojis.any { lines[firstNonBlankIdx].contains(it) }) return output
        val badgeLineIdx = lines.indexOfFirst { line -> badgeEmojis.any { line.contains(it) } }
        if (badgeLineIdx == -1) return output
        val mutable = lines.toMutableList()
        val badgeLine = mutable.removeAt(badgeLineIdx)
        mutable.add(0, badgeLine)
        return mutable.joinToString("\n")
    }
}
