package com.stacklens.application.service.securityanalysis

import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class SecurityAiOutputParser(
    private val jsonCodec: JsonCodecPort,
) {
    private val log = LoggerFactory.getLogger(SecurityAiOutputParser::class.java)

    fun extractSummary(output: String): String? {
        if (output.isBlank()) return null
        val firstMatch = Regex("""(?s)SUMMARY:\s*\n(.*?)(?=\nFINDINGS:|\Z)""").find(output)
        if (firstMatch != null) {
            val s = firstMatch.groupValues[1].trim().take(2000)
            if (s.length > 0) return s
        }
        val secondMatch = Regex("""(?is)\b(?:summary|zusammenfassung)\b[:*\s]*\n?(.*?)(?=\b(?:findings|befunde)\b|\Z)""").find(output)
        if (secondMatch != null) {
            val s = secondMatch.groupValues[1].trim().take(2000)
            if (s.isNotBlank()) return s
        }
        val f = output.indexOf("```")
        val raw =
            if (f > 0) {
                output.substring(0, f)
            } else {
                output
            }
        return raw
            .lineSequence()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
            .take(2000)
            .ifBlank { null }
    }

    fun parseFixOutput(rawOutput: String): ParsedFix {
        val text = rawOutput.replace("\r\n", "\n")
        val explanation =
            Regex(
                """(?is)(?:^|\n)\s*(?:#{1,3}\s*)?(?:\*\*)?ANALYSIS(?:\*\*)?\s*[:：]?\s*\n+(.*?)(?=\n\s*(?:#{1,3}\s*)?(?:\*\*)?FILES(?:\*\*)?\s*[:：]|\n\s*[`~]{3}|\Z)""",
            ).find(text)?.groupValues?.getOrNull(1)?.trim()
                ?: ""
        val payload =
            Regex(
                """(?is)(?:#{1,3}\s*)?(?:\*\*)?FILES(?:\*\*)?\s*[:：]?\s*\n+[`~]{3}\s*[A-Za-z0-9]*\s*\n(.*?)\n[`~]{3}""",
            ).find(text)?.groupValues?.getOrNull(1)
                ?: Regex("""(?is)[`~]{3}\s*[A-Za-z0-9]*\s*\n(\s*\[[\s\S]*?\])\s*\n[`~]{3}""").find(text)?.groupValues?.getOrNull(1)
                ?: Regex("""(?s)(\[\s*\{[\s\S]*?\}\s*])""").find(text)?.groupValues?.getOrNull(1)
        val changes =
            if (payload != null) {
                try {
                    jsonCodec
                        .decodeList(payload.trim(), ParsedFileChange::class)
                        .filter { it.path.isNotBlank() }
                } catch (e: Exception) {
                    log.warn("FILES-JSON parse: {}", e.message)
                    emptyList()
                }
            } else {
                emptyList()
            }
        return ParsedFix(explanation, changes)
    }
}
