
package com.stacklens.application.service.grafana

object JsonObjectExtractor {
    fun extractFirstJsonObject(text: String): String? {
        val blockStart = text.indexOf("```json")
        if (blockStart >= 0) {
            val contentStart = text.indexOf('\n', blockStart) + 1
            val blockEnd = text.indexOf("```", contentStart)
            if (blockEnd > contentStart) {
                val candidate = text.substring(contentStart, blockEnd).trim()
                if (candidate.startsWith("{")) return candidate
            }
        }
        val firstBrace = text.indexOf('{')
        if (firstBrace < 0) return null
        var depth = 0
        for (i in firstBrace until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(firstBrace, i + 1)
                }
            }
        }
        return null
    }
}
