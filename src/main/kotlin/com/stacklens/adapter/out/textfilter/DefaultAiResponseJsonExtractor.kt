package com.stacklens.adapter.out.textfilter

import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import org.springframework.stereotype.Component

@Component
class DefaultAiResponseJsonExtractor : AiResponseJsonExtractorPort {
    override fun extractSection(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String {
        val start = text.indexOf(startMarker)
        if (start < 0) return ""
        val end = text.indexOf(endMarker, start + startMarker.length)
        return if (end < 0) text.substring(start + startMarker.length) else text.substring(start + startMarker.length, end)
    }

    override fun extractTrimmedSection(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String {
        val start = text.indexOf(startMarker).takeIf { it >= 0 } ?: return ""
        val after = text.substring(start + startMarker.length)
        val end = after.indexOf(endMarker).takeIf { it >= 0 } ?: return after.trim()
        return after.substring(0, end).trim()
    }

    override fun extractLooseSection(
        text: String,
        startWords: List<String>,
        endWords: List<String>,
    ): String {
        val lower = text.lowercase()
        val starts =
            startWords
                .mapNotNull { word ->
                    val index = lower.indexOf(word.lowercase())
                    if (index >= 0) index to word else null
                }.minByOrNull { it.first } ?: return ""
        var cursor = starts.first + starts.second.length
        while (cursor < text.length && (text[cursor] == ':' || text[cursor] == '*' || text[cursor].isWhitespace())) cursor++
        val tail = text.substring(cursor)
        val tailLower = tail.lowercase()
        val end =
            endWords
                .mapNotNull { word ->
                    val index = tailLower.indexOf(word.lowercase())
                    if (index >= 0) index else null
                }.minOrNull() ?: tail.length
        return tail.substring(0, end)
    }

    override fun extractJsonBlock(
        text: String,
        startMarker: String,
        endMarker: String,
    ): String? {
        val section = extractSection(text, startMarker, endMarker)
        val begin = section.indexOf('[')
        val end = section.lastIndexOf(']')
        if (begin < 0 || end < 0 || end <= begin) return null
        return section.substring(begin, end + 1)
    }

    override fun extractJsonBlock(text: String): String {
        val fenced =
            Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```", RegexOption.MULTILINE)
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        if (fenced != null) return fenced
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end >= start) return text.substring(start, end + 1)
        return text
    }

    override fun extractJsonArray(text: String): String? {
        val fromBlock = extractCodeBlock(text, "json", "")
        if (fromBlock?.trimStart()?.startsWith("[") == true) return fromBlock.trim()
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start >= 0 && end > start) return text.substring(start, end + 1)
        return null
    }

    override fun extractCodeBlock(
        text: String,
        vararg langs: String,
    ): String? {
        for (lang in langs) {
            val regex = Regex("```$lang\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            val m = regex.find(text) ?: continue
            return m.groupValues[1].trim()
        }
        val fallbackMatch = Regex("```[^\\n]*\\n([\\s\\S]*?)```").find(text) ?: return null
        return fallbackMatch.groupValues[1].trim()
    }

    override fun extractJsonBlockAfter(
        text: String,
        startMarker: String,
    ): String? {
        val section = extractSection(text, startMarker, "```")
        val begin = section.indexOf('{')
        val end = section.lastIndexOf('}')
        if (begin < 0 || end < 0 || end <= begin) return null
        return section.substring(begin, end + 1)
    }

    override fun extractBalancedJsonObject(
        text: String,
        startPos: Int,
    ): String? {
        val end = findMatchingBrace(text, startPos)
        return if (end >= 0) text.substring(startPos, end + 1) else null
    }

    private fun findMatchingBrace(
        s: String,
        openPos: Int,
    ): Int {
        var depth = 0
        var inString = false
        var escape = false
        for (i in openPos until s.length) {
            val c = s[i]
            when {
                escape -> escape = false
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> if (--depth == 0) return i
            }
        }
        return -1
    }
}
