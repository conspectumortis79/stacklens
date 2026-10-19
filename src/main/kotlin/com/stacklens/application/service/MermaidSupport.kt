
package com.stacklens.application.service

internal object MermaidSupport {
    private val validKeywords =
        setOf(
            "graph",
            "flowchart",
            "sequencediagram",
            "classdiagram",
            "statediagram",
            "erdiagram",
            "journey",
            "gantt",
            "pie",
            "gitgraph",
            "requirementdiagram",
            "mindmap",
            "timeline",
            "quadrantchart",
            "xychart-beta",
            "sankey-beta",
            "block-beta",
            "architecture-beta",
            "packet-beta",
            "c4context",
            "c4container",
            "c4component",
            "c4dynamic",
            "c4deployment",
        )

    fun sanitize(code: String): String {
        var result = code
        result = result.replace("\\\"", "")
        result =
            Regex(""";[ \t]*(classDef|class)[ \t]""", RegexOption.IGNORE_CASE)
                .replace(result) { mr -> "\n${mr.groupValues[1]} " }

        result =
            result.lines().joinToString("\n") { line ->
                if (Regex("""^\s*(classDef|style)\s""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                    line.replace(Regex("[^\\x00-\\x7F]"), "")
                } else {
                    line
                }
            }
        result =
            result.replace(Regex("""(?<!\[")(?<!")(\b[A-Za-z0-9]+)-([A-Za-z0-9]+\b)(?!")(?!\]")""")) { mr ->
                mr.groupValues[1] + mr.groupValues[2]
            }
        result = result.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{2,}"), "\n")
        if (isFlowchart(result)) {
            result = result.lines().joinToString("\n") { normalizeFlowchartClassLine(it) }
        }
        return result.trim()
    }

    fun extract(text: String): String? {
        val regex = Regex("""```mermaid[ \t\r\n]*([\s\S]*?)```""", setOf(RegexOption.IGNORE_CASE))
        val extracted =
            regex
                .find(text)
                ?.let { mr -> mr.groupValues[1].trim() }
                ?.takeIf { it.isNotBlank() }
                ?.let { sanitize(it) }
                ?: return null
        val firstWord =
            extracted
                .trimStart()
                .split(Regex("\\s+"), limit = 2)
                .first()
                .lowercase()
        return if (firstWord in validKeywords) extracted else null
    }

    private fun isFlowchart(code: String): Boolean {
        val firstWord =
            code
                .trimStart()
                .split(Regex("\\s+"), limit = 2)
                .first()
                .lowercase()
        return firstWord == "graph" || firstWord == "flowchart"
    }

    private fun normalizeFlowchartClassLine(line: String): String {
        val match =
            Regex("""^(\s*class\s+)([A-Za-z0-9_,\s]+?)\s+([A-Za-z0-9_]+)\s*;?\s*$""")
                .matchEntire(line)
                ?: return line
        val ids =
            match.groupValues[2]
                .trim()
                .split(Regex("""\s*,\s*|\s+"""))
                .filter { it.isNotBlank() }
                .joinToString(",")
        return "${match.groupValues[1]}$ids ${match.groupValues[3]};"
    }
}
