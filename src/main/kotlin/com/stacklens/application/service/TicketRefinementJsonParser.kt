package com.stacklens.application.service

import com.stacklens.application.port.out.AiJsonRepairerPort
import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.SubtaskSuggestion

@ApplicationService
class TicketRefinementJsonParser(
    private val jsonCodec: JsonCodecPort,
    private val aiJsonRepairer: AiJsonRepairerPort,
    private val aiJsonExtractor: AiResponseJsonExtractorPort,
) {
    internal data class ParsedReview(
        val improvedDescription: String,
        val openPoints: List<String>,
        val improvedNative: String = "",
        val improvedFormat: DescriptionFormat = DescriptionFormat.MARKDOWN,
        val improvedSummary: String = "",
        val suggestedSubtasks: List<SubtaskSuggestion> = emptyList(),
    )

    internal fun parseAiResponse(
        raw: String,
        sourceFormat: DescriptionFormat = DescriptionFormat.MARKDOWN,
        acceptanceLabel: String = SubtaskBodyFormatter.GERMAN_LABEL,
    ): ParsedReview {
        val trimmed = raw.trim()
        val jsonText = extractJsonBlock(trimmed)
        val map: Map<String, Any?>? =
            runCatching { jsonCodec.decodeMap(jsonText) }
                .recoverCatching { jsonCodec.decodeMap(sanitizeAiJson(jsonText)) }
                .recoverCatching { jsonCodec.decodeMap(repairUnbalancedQuotes(sanitizeAiJson(jsonText))) }
                .recoverCatching {
                    val repaired =
                        tryRepairTruncatedJson(jsonText)
                            ?: throw IllegalStateException(
                                "not a repairable truncated JSON",
                            )
                    jsonCodec.decodeMap(repaired)
                }.getOrNull()
        if (map != null) {
            @Suppress("UNCHECKED_CAST")
            val pointsRaw = map["openPoints"] as? List<Any?> ?: emptyList()
            val points = pointsRaw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
            val improvedSummary = (map["improvedSummary"] as? String).orEmpty().trim()
            val suggestedSubtasks = parseSubtasks(map["subtasks"], acceptanceLabel)
            val adfNode = map["improvedDescriptionAdf"]
            if (adfNode is Map<*, *> && adfNode["type"] == "doc") {
                val nativeJson = jsonCodec.encode(adfNode)
                val plain = flattenAdfPlain(adfNode)
                return ParsedReview(plain, points, nativeJson, DescriptionFormat.ADF, improvedSummary, suggestedSubtasks)
            }

            return when (sourceFormat) {
                DescriptionFormat.WIKI -> {
                    val wiki = (map["improvedDescriptionWiki"] as? String).orEmpty().trim()
                    if (wiki.isNotBlank()) {
                        ParsedReview(wiki, points, wiki, DescriptionFormat.WIKI, improvedSummary, suggestedSubtasks)
                    } else {
                        markdownFallback(map, trimmed, points, improvedSummary, suggestedSubtasks)
                    }
                }
                DescriptionFormat.ADF, DescriptionFormat.MARKDOWN -> markdownFallback(map, trimmed, points, improvedSummary, suggestedSubtasks)
            }
        }
        if (sourceFormat == DescriptionFormat.ADF) {
            val adfBlock = extractAdfBlock(jsonText) ?: extractAdfBlock(sanitizeAiJson(jsonText))
            if (adfBlock != null) {
                val sanitisedAdf = sanitizeAiJson(adfBlock)
                val adfMap = runCatching { jsonCodec.decodeMap(sanitisedAdf) }.getOrNull()
                if (adfMap != null && adfMap["type"] == "doc") {
                    val nativeJson = jsonCodec.encode(adfMap)
                    val plain = flattenAdfPlain(adfMap)
                    return ParsedReview(plain, emptyList(), nativeJson, DescriptionFormat.ADF)
                }
            }
        }
        val descMatch = Regex(""""improvedDescription"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(jsonText)
        val improved =
            descMatch
                ?.groupValues
                ?.getOrNull(1)
                ?.replace("\\n", "\n")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                ?.trim()
        return ParsedReview(improved ?: trimmed, emptyList(), "", DescriptionFormat.MARKDOWN)
    }

    private fun markdownFallback(
        map: Map<String, Any?>,
        rawTrimmed: String,
        points: List<String>,
        improvedSummary: String,
        suggestedSubtasks: List<SubtaskSuggestion> = emptyList(),
    ): ParsedReview {
        val improved = (map["improvedDescription"] as? String).orEmpty().trim()
        return ParsedReview(improved.ifBlank { rawTrimmed }, points, "", DescriptionFormat.MARKDOWN, improvedSummary, suggestedSubtasks)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseSubtasks(
        raw: Any?,
        acceptanceLabel: String = SubtaskBodyFormatter.GERMAN_LABEL,
    ): List<SubtaskSuggestion> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val summary = (m["summary"] as? String)?.trim().orEmpty()
            if (summary.isBlank()) return@mapNotNull null
            val description = SubtaskBodyFormatter.format((m["description"] as? String).orEmpty(), acceptanceLabel)
            val issueType = (m["issueType"] as? String)?.trim()?.ifBlank { "Subtask" } ?: "Subtask"
            val priority = (m["priority"] as? String)?.trim()?.ifBlank { "Minor" } ?: "Minor"
            val labels =
                (m["labels"] as? List<*>)
                    ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotBlank() } }
                    ?: emptyList()
            val existingKey = (m["existingKey"] as? String)?.trim().orEmpty()
            SubtaskSuggestion(
                summary = summary,
                description = description,
                issueType = issueType,
                priority = priority,
                labels = labels,
                existingKey = existingKey,
            )
        }
    }

    internal fun sanitizeAiJson(s: String): String = aiJsonRepairer.sanitizeAiJson(s)

    internal fun repairUnbalancedQuotes(s: String): String = aiJsonRepairer.repairUnbalancedQuotes(s)

    internal fun tryRepairTruncatedJson(s: String): String? = aiJsonRepairer.tryRepairTruncatedJson(s)

    internal fun extractAdfBlock(json: String): String? {
        val keyIdx = json.indexOf("\"improvedDescriptionAdf\"")
        if (keyIdx < 0) return null
        val colonIdx = json.indexOf(':', keyIdx)
        if (colonIdx < 0) return null
        var i = colonIdx + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '{') return null
        val start = i
        var depth = 0
        var inString = false
        var escape = false
        while (i < json.length) {
            val c = json[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return json.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        return null
    }

    internal fun extractJsonBlock(s: String): String {
        val fenceOpen = Regex("```(?:json|JSON)?[ \\t]*\\n")
        val openMatch = fenceOpen.find(s)
        if (openMatch != null) {
            val start = openMatch.range.last + 1
            val closeIdx = s.indexOf("\n```", start)
            val block = if (closeIdx >= 0) s.substring(start, closeIdx) else s.substring(start)
            val first = block.indexOf('{')
            if (first >= 0) {
                return aiJsonExtractor.extractBalancedJsonObject(block, first)?.trim() ?: block
            }
        }
        val first = s.indexOf('{')
        if (first < 0) return s
        return aiJsonExtractor.extractBalancedJsonObject(s, first) ?: s
    }

    internal fun flattenAdfPlain(node: Any?): String {
        if (node is String) return node
        if (node !is Map<*, *>) return ""
        val sb = StringBuilder()
        if (node["type"] == "text") {
            sb.append((node["text"] as? String) ?: "")
        }
        val content = node["content"]
        if (content is List<*>) {
            content.forEach { sb.append(flattenAdfPlain(it)).append(' ') }
        }
        return sb.toString().trim()
    }
}
