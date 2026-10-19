
package com.stacklens.domain.search

object KeywordContext {
    fun findKeywordInContent(
        content: String,
        keyword: String,
    ): List<Pair<Int, String>> {
        val lines = content.lines()
        return lines.mapIndexedNotNull { idx, line ->
            if (line.contains(keyword, ignoreCase = true)) {
                val start = maxOf(0, idx - 2)
                val end = minOf(lines.size - 1, idx + 2)
                val ctx = lines.subList(start, end + 1).joinToString("\n")
                Pair(idx + 1, ctx)
            } else {
                null
            }
        }
    }

    fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
