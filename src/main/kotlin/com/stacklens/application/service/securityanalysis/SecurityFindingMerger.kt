package com.stacklens.application.service.securityanalysis

import com.stacklens.application.port.out.AiJsonRepairerPort
import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import com.stacklens.application.port.out.JsonReaderPort
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class SecurityFindingMerger(
    private val jsonCodec: JsonReaderPort,
    private val aiJsonRepairer: AiJsonRepairerPort,
    private val aiJsonExtractor: AiResponseJsonExtractorPort,
) {
    private val log = LoggerFactory.getLogger(SecurityFindingMerger::class.java)

    fun mergeFindings(
        existing: Map<String, SecurityFinding>,
        newFindings: List<SecurityFinding>,
        iteration: Int,
    ): Map<String, SecurityFinding> {
        val result = existing.toMutableMap()
        newFindings.forEach { f ->
            val key = f.id
            val prev = result[key]
            if (prev == null) {
                result[key] = f
            } else {
                result[key] =
                    prev.copy(
                        severity = if (f.severity.isNotBlank()) f.severity else prev.severity,
                        description = if (f.description.isNotBlank()) f.description else prev.description,
                        suggestion = if (f.suggestion.isNotBlank()) f.suggestion else prev.suggestion,
                        suggestedFix = f.suggestedFix ?: prev.suggestedFix,
                        cwe = f.cwe ?: prev.cwe,
                        exploitPayload = f.exploitPayload ?: prev.exploitPayload,
                        attackVector = f.attackVector ?: prev.attackVector,
                        attackSteps = f.attackSteps ?: prev.attackSteps,
                        proofOfConcept = f.proofOfConcept ?: prev.proofOfConcept,
                        cvssScore = f.cvssScore ?: prev.cvssScore,
                        exploitability = f.exploitability ?: prev.exploitability,
                        confirmed = f.confirmed || prev.confirmed,
                    )
            }
        }
        return result
    }

    fun parseFindings(
        rawOutput: String,
        iteration: Int,
    ): List<SecurityFinding> {
        val json = extractFindingsJson(rawOutput)
        if (json.isNullOrBlank()) {
            log.warn("[SecurityAnalysis] No FINDINGS block found (iter {})", iteration)
            return emptyList()
        }
        val parsed = decodeFindings(json, iteration) ?: salvageFindings(json, iteration)
        return parsed.map { if (it.iterationFound == 1 && iteration > 1) it.copy(iterationFound = iteration) else it }
    }

    private fun extractFindingsJson(rawOutput: String): String? {
        val fenced = Regex("""(?s)FINDINGS:\s*\n```(?:json)?\s*\n(.*?)\n```""").find(rawOutput)
        fenced?.let { return it.groupValues[1].trim() }
        return aiJsonExtractor.extractJsonArray(rawOutput)?.trim()
    }

    private fun decodeFindings(
        json: String,
        iteration: Int,
    ): List<SecurityFinding>? {
        val sanitized = aiJsonRepairer.sanitizeAiJson(json)
        val candidates =
            listOf(
                json,
                sanitized,
                aiJsonRepairer.repairUnbalancedQuotes(sanitized),
                aiJsonRepairer.tryRepairTruncatedJson(sanitized),
                aiJsonRepairer.tryRepairTruncatedJson(aiJsonRepairer.repairUnbalancedQuotes(sanitized)),
            )
        var lastError: Exception = IllegalArgumentException("no decodable FINDINGS JSON")
        for (candidate in candidates.filterNotNull()) {
            try {
                return jsonCodec.decodeList(candidate, SecurityFinding::class)
            } catch (e: Exception) {
                lastError = e
            }
        }
        log.warn("[SecurityAnalysis] Error parsing FINDINGS (iter {}): {}", iteration, lastError.message)
        return null
    }

    private fun salvageFindings(
        json: String,
        iteration: Int,
    ): List<SecurityFinding> {
        val salvaged = mutableListOf<SecurityFinding>()
        var index = 0
        while (index < json.length) {
            if (json[index] != '{') {
                index++
                continue
            }
            val end = matchingBrace(json, index)
            if (end < 0) {
                index++
                continue
            }
            val fragment = json.substring(index, end + 1)
            val decoded = runCatching { jsonCodec.decode(fragment, SecurityFinding::class) }.getOrNull()
            if (decoded != null && decoded.id.isNotBlank()) {
                salvaged += decoded
                index = end + 1
            } else {
                index++
            }
        }
        if (salvaged.isNotEmpty()) {
            log.warn("[SecurityAnalysis] Salvaged {} finding(s) from malformed FINDINGS (iter {})", salvaged.size, iteration)
        }
        return salvaged
    }

    private fun matchingBrace(
        text: String,
        start: Int,
    ): Int {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    fun isAiSafetyRefusal(message: String): Boolean {
        val n = message.lowercase()
        return listOf(
            "defensive auswertung",
            "defensive analysis",
            "exploit-payload",
            "exploit payload",
            "proof-of-concept",
            "proof of concept",
            "poc",
            "ausnutzbar",
            "weaponizable",
            "schädlich",
            "harmful",
        ).any { n.contains(it) }
    }
}
