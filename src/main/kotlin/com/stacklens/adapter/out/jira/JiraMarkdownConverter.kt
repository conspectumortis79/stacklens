package com.stacklens.adapter.out.jira

import org.springframework.stereotype.Component

@Component
class JiraMarkdownConverter {
    fun looksLikeMarkdown(text: String): Boolean {
        if (text.isBlank()) return false
        val lines = text.lines()
        val hasAtxHeading = lines.any { ATX_HEADING.containsMatchIn(it) }
        val hasFencedCode = text.contains("```")
        val hasMdBullet = lines.any { MD_BULLET.containsMatchIn(it) }
        val hasMdBold = MD_BOLD.containsMatchIn(text)
        val hasMdLink = MD_LINK.containsMatchIn(text)
        return hasAtxHeading || hasFencedCode || hasMdBullet || hasMdBold || hasMdLink
    }

    fun markdownToJiraWiki(markdown: String): String {
        val sb = StringBuilder()
        val bulletBuf = mutableListOf<String>()
        val orderedBuf = mutableListOf<String>()

        fun flushBullets() {
            bulletBuf.forEach { sb.append("* $it\n") }
            bulletBuf.clear()
        }

        fun flushOrdered() {
            orderedBuf.forEach { sb.append("# $it\n") }
            orderedBuf.clear()
        }

        fun flushLists() {
            flushBullets()
            flushOrdered()
        }

        for (line in markdown.lines()) {
            when {
                line.startsWith("### ") -> {
                    flushLists()
                    sb.append("h3. ${inlineToWiki(line.removePrefix("### "))}\n")
                }
                line.startsWith("## ") || line.startsWith("# ") -> {
                    flushLists()
                    sb.append("h2. ${inlineToWiki(line.replace(HEADING_PREFIX, ""))}\n")
                }
                line.trim() in listOf("---", "***") -> {
                    flushLists()
                    sb.append("----\n")
                }
                line.startsWith("> ") -> {
                    flushLists()
                    sb.append("{quote}${inlineToWiki(line.removePrefix("> "))}{quote}\n")
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flushOrdered()
                    val content =
                        line
                            .removePrefix("- ")
                            .removePrefix("* ")
                            .removePrefix("[ ] ")
                            .removePrefix("[x] ")
                            .removePrefix("[X] ")
                    bulletBuf.add(inlineToWiki(content))
                }
                ORDERED_PREFIX.containsMatchIn(line) -> {
                    flushBullets()
                    orderedBuf.add(inlineToWiki(line.replace(ORDERED_PREFIX, "")))
                }
                line.isBlank() -> {
                    flushLists()
                    sb.append("\n")
                }
                else -> {
                    flushLists()
                    sb.append("${inlineToWiki(line)}\n")
                }
            }
        }
        flushBullets()
        flushOrdered()
        return sb.toString().trimEnd()
    }

    fun inlineToWiki(text: String): String =
        text
            .replace(BOLD_MARKER, "*$1*")
            .replace(ITALIC_MARKER, "_$1_")
            .replace(CODE_MARKER, "{{$1}}")
            .replace(LINK_MARKER, "[$1|$2]")

    fun markdownToAdf(markdown: String): Map<String, Any> {
        val content = mutableListOf<Map<String, Any>>()
        val bulletBuffer = mutableListOf<Map<String, Any>>()
        val orderedBuffer = mutableListOf<Map<String, Any>>()

        fun flushBullets() {
            if (bulletBuffer.isNotEmpty()) {
                content.add(mapOf("type" to "bulletList", "content" to bulletBuffer.toList()))
                bulletBuffer.clear()
            }
        }

        fun flushOrdered() {
            if (orderedBuffer.isNotEmpty()) {
                content.add(mapOf("type" to "orderedList", "content" to orderedBuffer.toList()))
                orderedBuffer.clear()
            }
        }

        fun flushLists() {
            flushBullets()
            flushOrdered()
        }

        fun heading(
            level: Int,
            text: String,
        ) {
            flushLists()
            content.add(
                mapOf(
                    "type" to "heading",
                    "attrs" to mapOf("level" to level),
                    "content" to listOf(mapOf("type" to "text", "text" to text)),
                ),
            )
        }

        fun listItem(text: String) =
            mapOf(
                "type" to "listItem",
                "content" to listOf(mapOf("type" to "paragraph", "content" to inlineToAdf(text))),
            )

        for (line in markdown.lines()) {
            val ordered = ORDERED_LINE.matchEntire(line)
            when {
                line.startsWith("### ") -> heading(3, line.removePrefix("### "))
                line.startsWith("## ") -> heading(2, line.removePrefix("## "))
                line.startsWith("# ") -> heading(1, line.removePrefix("# "))
                line.trim() in listOf("---", "***") -> {
                    flushLists()
                    content.add(mapOf("type" to "rule"))
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flushOrdered()
                    val raw = line.removePrefix("- ").removePrefix("* ")
                    bulletBuffer.add(listItem(raw.removePrefix("[ ] ").removePrefix("[x] ").removePrefix("[X] ")))
                }
                ordered != null -> {
                    flushBullets()
                    orderedBuffer.add(listItem(ordered.groupValues[1]))
                }
                line.isBlank() -> flushLists()
                else -> {
                    flushLists()
                    content.add(mapOf("type" to "paragraph", "content" to inlineToAdf(line)))
                }
            }
        }
        flushLists()
        if (content.isEmpty()) {
            content.add(mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to ""))))
        }
        return mapOf("type" to "doc", "version" to 1, "content" to content)
    }

    fun inlineToAdf(text: String): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        val inlineRegex = Regex("""\*\*(.+?)\*\*|`([^`]+)`|_([^_]+)_""")
        var last = 0
        for (m in inlineRegex.findAll(text)) {
            if (m.range.first > last) {
                result.add(mapOf("type" to "text", "text" to text.substring(last, m.range.first)))
            }
            val (group, mark) =
                when {
                    m.groupValues[1].isNotEmpty() -> 1 to "strong"
                    m.groupValues[2].isNotEmpty() -> 2 to "code"
                    else -> 3 to "em"
                }
            result.add(
                mapOf(
                    "type" to "text",
                    "text" to m.groupValues[group],
                    "marks" to listOf(mapOf("type" to mark)),
                ),
            )
            last = m.range.last + 1
        }
        if (last < text.length) result.add(mapOf("type" to "text", "text" to text.substring(last)))
        return result.ifEmpty { listOf(mapOf("type" to "text", "text" to text)) }
    }

    private companion object {
        private val ATX_HEADING = Regex("^#{1,6} \\S")
        private val MD_BULLET = Regex("^- \\S")
        private val MD_BOLD = Regex("\\*\\*\\S")
        private val MD_LINK = Regex("\\[[^\\]]+\\]\\(https?://")
        private val BOLD_MARKER = Regex("""\*\*(.+?)\*\*""")
        private val ITALIC_MARKER = Regex("""(?<![*])_(.+?)_(?![*])""")
        private val CODE_MARKER = Regex("""`(.+?)`""")
        private val LINK_MARKER = Regex("""\[([^\]]+)]\(([^)]+)\)""")
        private val HEADING_PREFIX = Regex("^#{1,2} ")
        private val ORDERED_PREFIX = Regex("""^\d+\. """)
        private val ORDERED_LINE = Regex("""^\d+\. (.*)""")
    }
}
