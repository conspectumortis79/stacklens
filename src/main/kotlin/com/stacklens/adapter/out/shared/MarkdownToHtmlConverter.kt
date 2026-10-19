package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.MarkdownConvertPort
import org.springframework.stereotype.Component

@Component
object MarkdownToHtmlConverter : MarkdownConvertPort {
    data class SectionStyle(
        val icon: String,
        val cls: String,
    )

    val KNOWN_SECTIONS: Map<String, SectionStyle> =
        mapOf(
            "überblick" to SectionStyle("🧭", "overview"),
            "uberblick" to SectionStyle("🧭", "overview"),
            "overview" to SectionStyle("🧭", "overview"),
            "summary" to SectionStyle("🧭", "overview"),
            "zusammenfassung" to SectionStyle("🧭", "overview"),
            "anforderungen" to SectionStyle("📌", "requirements"),
            "requirements" to SectionStyle("📌", "requirements"),
            "akzeptanzkriterien" to SectionStyle("✅", "ac"),
            "acceptance criteria" to SectionStyle("✅", "ac"),
            "ak" to SectionStyle("✅", "ac"),
            "diskussionsverlauf" to SectionStyle("💬", "discussion"),
            "discussion" to SectionStyle("💬", "discussion"),
            "offene punkte" to SectionStyle("❓", "open"),
            "open points" to SectionStyle("❓", "open"),
            "nächste schritte" to SectionStyle("➡️", "next"),
            "naechste schritte" to SectionStyle("➡️", "next"),
            "next steps" to SectionStyle("➡️", "next"),
            "risiken" to SectionStyle("⚠️", "risks"),
            "risks" to SectionStyle("⚠️", "risks"),
            "annahmen" to SectionStyle("🧠", "assumptions"),
            "assumptions" to SectionStyle("🧠", "assumptions"),
        )

    private val BULLET = Regex("^[\\-\\*]\\s+\\S.*")
    private val ORDERED = Regex("^\\d+\\.\\s+\\S.*")
    private val H1 = Regex("^#\\s+\\S.*")
    private val H2 = Regex("^##\\s+\\S.*")
    private val H3 = Regex("^###\\s+\\S.*")
    private val HR = Regex("^---+$")
    private val BQ = Regex("^>\\s+.*")
    private val BOLD = Regex("^\\*\\*(.+?)\\*\\*")
    private val ITALIC = Regex("^_([^_]+)_")
    private val CODE = Regex("^`([^`]+)`")

    private val FENCE_FIND = Regex("```(?:markdown|md)?\\r?\\n([\\s\\S]*?)\\n```", RegexOption.MULTILINE)

    override fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val s = raw.trim()
        val m = FENCE_FIND.find(s)
        if (m != null) return m.groupValues[1].trim()
        val lines = s.lines().toMutableList()
        var mutated = false
        if (lines.first().trim().startsWith("```")) {
            lines.removeAt(0)
            mutated = true
        }
        if (lines.isNotEmpty() && lines.last().trim() == "```") {
            lines.removeAt(lines.size - 1)
            mutated = true
        }
        return if (mutated) lines.joinToString("\n").trim() else s
    }

    override fun render(markdown: String?): String {
        if (markdown.isNullOrBlank()) return ""
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val out = StringBuilder()
        out.append("<div class=\"ts-doc\">")
        var inBullet = false
        var inOrdered = false
        var inSection = false

        fun closeLists() {
            if (inBullet) {
                out.append("</ul>")
                inBullet = false
            }
            if (inOrdered) {
                out.append("</ol>")
                inOrdered = false
            }
        }

        fun closeSection() {
            closeLists()
            if (inSection) {
                out.append("</div></div>")
                inSection = false
            }
        }
        for (line in lines) {
            val isBullet = BULLET.matches(line)
            val isOrdered = ORDERED.matches(line)
            if (!isBullet && inBullet) {
                out.append("</ul>")
                inBullet = false
            }
            if (!isOrdered && inOrdered) {
                out.append("</ol>")
                inOrdered = false
            }
            if (H2.matches(line) || H1.matches(line)) {
                closeSection()
                val heading = line.replace(Regex("^#{1,2}\\s+"), "").trim()
                val meta = KNOWN_SECTIONS[heading.lowercase()]
                if (meta != null) {
                    inSection = true
                    out.append("<div class=\"ts-doc-section ts-doc-section--").append(meta.cls).append("\">")
                    out.append("<div class=\"ts-doc-section-head\">")
                    out.append("<span class=\"ts-doc-icon\">").append(escapeHtml(meta.icon)).append("</span>")
                    out.append("<span class=\"ts-doc-title\">").append(escapeHtml(heading)).append("</span>")
                    out.append("</div>")
                    out.append("<div class=\"ts-doc-section-body\">")
                } else {
                    out.append("<div class=\"ts-doc-h2\">").append(applyInline(heading)).append("</div>")
                }
            } else if (H3.matches(line)) {
                closeLists()
                out.append("<div class=\"ts-doc-h3\">").append(applyInline(line.replace(Regex("^###\\s+"), "").trim())).append("</div>")
            } else if (HR.matches(line.trim())) {
                closeLists()
                out.append("<hr class=\"ts-doc-hr\">")
            } else if (BQ.matches(line)) {
                closeLists()
                out.append("<div class=\"ts-doc-bq\">").append(applyInline(line.replace(Regex("^>\\s+"), ""))).append("</div>")
            } else if (isBullet) {
                if (!inBullet) {
                    out.append("<ul class=\"ts-doc-ul\">")
                    inBullet = true
                }
                val text = line.replace(Regex("^[\\-\\*]\\s+"), "")
                out.append("<li>").append(applyInline(stripCheckbox(text))).append("</li>")
            } else if (isOrdered) {
                if (!inOrdered) {
                    out.append("<ol class=\"ts-doc-ol\">")
                    inOrdered = true
                }
                val text = line.replace(Regex("^\\d+\\.\\s+"), "")
                out.append("<li>").append(applyInline(text)).append("</li>")
            } else if (line.isBlank()) {
                closeLists()
            } else {
                closeLists()
                out.append("<p class=\"ts-doc-p\">").append(applyInline(line)).append("</p>")
            }
        }
        closeSection()
        out.append("</div>")
        return out.toString()
    }

    fun escapeHtml(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun stripCheckbox(text: String): String = text.replace(Regex("^\\[[ xX]\\]\\s+"), "")

    private fun applyInline(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val rest = text.substring(i)
            val bold = BOLD.find(rest)
            val em = if (bold == null) ITALIC.find(rest) else null
            val code = if (bold == null && em == null) CODE.find(rest) else null
            when {
                bold != null -> {
                    sb.append("<strong>").append(escapeHtml(bold.groupValues[1])).append("</strong>")
                    i += bold.value.length
                }
                em != null -> {
                    sb.append("<em>").append(escapeHtml(em.groupValues[1])).append("</em>")
                    i += em.value.length
                }
                code != null -> {
                    sb.append("<code class=\"ts-doc-code\">").append(escapeHtml(code.groupValues[1])).append("</code>")
                    i += code.value.length
                }
                else -> {
                    sb.append(escapeHtml(text[i].toString()))
                    i++
                }
            }
        }
        return sb.toString()
    }
}
