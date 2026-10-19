
package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class AdfToHtmlConverter(
    private val objectMapper: ObjectMapper,
) {
    fun render(adfJson: String?): String? {
        if (adfJson.isNullOrBlank()) return null
        return runCatching {
            val node = objectMapper.readTree(adfJson)
            renderBlock(node).ifBlank { null }
        }.getOrNull()
    }

    private fun renderBlock(node: JsonNode): String {
        val content = node.path("content")
        return when (node.path("type").asText()) {
            "doc" -> content.joinToString("") { renderBlock(it) }

            "paragraph" -> {
                val inner = renderInline(content)
                if (inner.isBlank()) "" else "<p class=\"sp-jira-p\">$inner</p>"
            }

            "heading" -> {
                val level = node.path("attrs").path("level").asInt(2)
                val cls = if (level <= 2) "sp-jira-h2" else "sp-jira-h3"
                "<div class=\"$cls\">${renderInline(content)}</div>"
            }

            "bulletList" -> "<ul class=\"sp-jira-ul\">${content.joinToString("") { renderBlock(it) }}</ul>"

            "orderedList" -> "<ol class=\"sp-jira-ol\">${content.joinToString("") { renderBlock(it) }}</ol>"

            "listItem" ->
                if (content.size() == 1 && content[0].path("type").asText() == "paragraph") {
                    "<li>${renderInline(content[0].path("content"))}</li>"
                } else {
                    "<li>${content.joinToString("") { renderBlock(it) }}</li>"
                }

            "blockquote" -> "<div class=\"sp-jira-bq\">${content.joinToString("") { renderBlock(it) }}</div>"

            "rule" -> "<hr class=\"sp-jira-hr\">"

            "codeBlock" -> {
                val lang = node.path("attrs").path("language").asText("")
                val langAttr = if (lang.isNotBlank()) " data-lang=\"${escHtml(lang)}\"" else ""
                val code = content.joinToString("") { escHtml(it.path("text").asText("")) }
                "<pre class=\"sp-jira-codeblock\"$langAttr><code>$code</code></pre>"
            }

            "panel" -> {
                val panelType = node.path("attrs").path("panelType").asText("info")
                "<div class=\"sp-jira-panel sp-jira-panel--${escHtml(panelType)}\">" +
                    "${content.joinToString("") { renderBlock(it) }}</div>"
            }

            "table" ->
                "<table class=\"sp-jira-table\"><tbody>" +
                    "${content.joinToString("") { renderBlock(it) }}</tbody></table>"

            "tableRow" -> "<tr>${content.joinToString("") { renderBlock(it) }}</tr>"

            "tableHeader" -> "<th class=\"sp-jira-th\">${content.joinToString("") { renderBlock(it) }}</th>"

            "tableCell" -> "<td class=\"sp-jira-td\">${content.joinToString("") { renderBlock(it) }}</td>"

            "mediaSingle", "media" -> "<div class=\"sp-jira-media-skip\">[Bild/Anhang]</div>"

            else -> if (content.size() > 0) content.joinToString("") { renderBlock(it) } else ""
        }
    }

    private fun renderInline(nodes: JsonNode): String {
        if (nodes.isMissingNode || !nodes.isArray) return ""
        return nodes.joinToString("") { renderInlineNode(it) }
    }

    private fun renderInlineNode(node: JsonNode): String =
        when (node.path("type").asText()) {
            "text" -> applyMarks(escHtml(node.path("text").asText("")), node.path("marks"))
            "hardBreak" -> "<br>"
            "mention" -> {
                val attrs = node.path("attrs")
                val text = attrs.path("text").asText("").ifBlank { attrs.path("id").asText("") }
                "<span class=\"sp-jira-mention\">@${escHtml(text)}</span>"
            }
            "emoji" -> {
                val attrs = node.path("attrs")
                escHtml(attrs.path("text").asText("").ifBlank { attrs.path("shortName").asText("") })
            }
            "inlineCard" -> {
                val url = node.path("attrs").path("url").asText("")
                "<a href=\"${safeUrl(url)}\" target=\"_blank\" rel=\"noopener\">${escHtml(url)}</a>"
            }
            else -> renderInline(node.path("content"))
        }

    private fun applyMarks(
        html: String,
        marks: JsonNode,
    ): String {
        if (marks.isMissingNode || !marks.isArray || marks.isEmpty) return html
        var result = html
        marks.toList().asReversed().forEach { mark ->
            result =
                when (mark.path("type").asText()) {
                    "strong" -> "<strong>$result</strong>"
                    "em" -> "<em>$result</em>"
                    "code" -> "<code class=\"sp-jira-code\">$result</code>"
                    "underline" -> "<span style=\"text-decoration:underline\">$result</span>"
                    "strike" -> "<s>$result</s>"
                    "subsup" ->
                        if (mark.path("attrs").path("type").asText() == "sub") {
                            "<sub>$result</sub>"
                        } else {
                            "<sup>$result</sup>"
                        }
                    "textColor" -> {
                        val color = mark.path("attrs").path("color").asText("")
                        if (color.isNotBlank()) "<span style=\"color:${escHtml(color)}\">$result</span>" else result
                    }
                    "link" -> {
                        val href = mark.path("attrs").path("href").asText("#")
                        "<a href=\"${safeUrl(href)}\" target=\"_blank\" rel=\"noopener\">$result</a>"
                    }
                    else -> result
                }
        }
        return result
    }

    private fun escHtml(s: String): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun safeUrl(url: String): String {
        val trimmed = url.trim()
        val lower = trimmed.lowercase()
        val allowed =
            lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("/") ||
                lower.startsWith("#") ||
                lower.startsWith("./") ||
                lower.startsWith("../") ||
                trimmed.isEmpty()
        return if (allowed) escHtml(trimmed) else "#"
    }
}
