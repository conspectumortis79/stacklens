package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownToHtmlConverterTest {
    @Test
    fun `sanitize returns empty for null input`() {
        assertEquals("", MarkdownToHtmlConverter.sanitize(null))
    }

    @Test
    fun `sanitize returns empty for blank input`() {
        assertEquals("", MarkdownToHtmlConverter.sanitize("   "))
        assertEquals("", MarkdownToHtmlConverter.sanitize("\n\n"))
    }

    @Test
    fun `sanitize extracts content from markdown fence`() {
        val raw = "```markdown\n# Heading\nContent\n```"
        assertEquals("# Heading\nContent", MarkdownToHtmlConverter.sanitize(raw))
    }

    @Test
    fun `sanitize extracts content from md fence`() {
        val raw = "```md\nbody\n```"
        assertEquals("body", MarkdownToHtmlConverter.sanitize(raw))
    }

    @Test
    fun `sanitize extracts content from bare fence`() {
        val raw = "```\nbody content\n```"
        assertEquals("body content", MarkdownToHtmlConverter.sanitize(raw))
    }

    @Test
    fun `sanitize strips lone opening fence line`() {
        val raw = "```markdown\nbody without closing fence"
        val result = MarkdownToHtmlConverter.sanitize(raw)
        assertEquals("body without closing fence", result)
    }

    @Test
    fun `sanitize strips lone closing fence line`() {
        val raw = "body without opening fence\n```"
        val result = MarkdownToHtmlConverter.sanitize(raw)
        assertEquals("body without opening fence", result)
    }

    @Test
    fun `sanitize returns trimmed input unchanged when no fence`() {
        val raw = "  ## Heading\nContent  "
        assertEquals("## Heading\nContent", MarkdownToHtmlConverter.sanitize(raw))
    }

    @Test
    fun `render returns empty for null input`() {
        assertEquals("", MarkdownToHtmlConverter.render(null))
    }

    @Test
    fun `render returns empty for blank input`() {
        assertEquals("", MarkdownToHtmlConverter.render(""))
        assertEquals("", MarkdownToHtmlConverter.render("   "))
    }

    @Test
    fun `render wraps output in ts-doc container`() {
        val html = MarkdownToHtmlConverter.render("plain text")
        assertTrue(html.startsWith("<div class=\"ts-doc\">"))
        assertTrue(html.endsWith("</div>"))
    }

    @Test
    fun `render H2 with known section uses section class and icon`() {
        val html = MarkdownToHtmlConverter.render("## Summary\nbody")
        assertTrue(html.contains("ts-doc-section--overview"))
        assertTrue(html.contains("🧭"))
    }

    @Test
    fun `render H2 with unknown section uses generic ts-doc-h2`() {
        val html = MarkdownToHtmlConverter.render("## MyCustomSection\nbody")
        assertTrue(html.contains("ts-doc-h2"))
        assertFalse(html.contains("ts-doc-section--"))
    }

    @Test
    fun `render H1 also uses section treatment`() {
        val html = MarkdownToHtmlConverter.render("# Risks\nbody")
        assertTrue(html.contains("ts-doc-section--risks"))
    }

    @Test
    fun `render H3 uses ts-doc-h3 class`() {
        val html = MarkdownToHtmlConverter.render("### Subsection\ntext")
        assertTrue(html.contains("ts-doc-h3"))
        assertTrue(html.contains("Subsection"))
    }

    @Test
    fun `render bullet list with dash`() {
        val html = MarkdownToHtmlConverter.render("- one\n- two\n- three")
        assertTrue(html.contains("<ul class=\"ts-doc-ul\">"))
        assertTrue(html.contains("<li>one</li>"))
        assertTrue(html.contains("<li>two</li>"))
        assertTrue(html.contains("<li>three</li>"))
        assertTrue(html.contains("</ul>"))
    }

    @Test
    fun `render bullet list with star`() {
        val html = MarkdownToHtmlConverter.render("* one\n* two")
        assertTrue(html.contains("<ul class=\"ts-doc-ul\">"))
        assertTrue(html.contains("<li>one</li>"))
    }

    @Test
    fun `render ordered list`() {
        val html = MarkdownToHtmlConverter.render("1. first\n2. second")
        assertTrue(html.contains("<ol class=\"ts-doc-ol\">"))
        assertTrue(html.contains("<li>first</li>"))
        assertTrue(html.contains("<li>second</li>"))
        assertTrue(html.contains("</ol>"))
    }

    @Test
    fun `render blockquote as callout`() {
        val html = MarkdownToHtmlConverter.render("> a quoted line")
        assertTrue(html.contains("ts-doc-bq"))
        assertTrue(html.contains("a quoted line"))
    }

    @Test
    fun `render horizontal rule`() {
        val html = MarkdownToHtmlConverter.render("---")
        assertTrue(html.contains("ts-doc-hr"))
    }

    @Test
    fun `render paragraph for plain text`() {
        val html = MarkdownToHtmlConverter.render("just a paragraph")
        assertTrue(html.contains("<p class=\"ts-doc-p\">"))
        assertTrue(html.contains("just a paragraph"))
    }

    @Test
    fun `render blank line closes open lists`() {
        val html = MarkdownToHtmlConverter.render("- one\n\n- two")
        assertTrue(html.contains("</ul>"))
    }

    @Test
    fun `render applies bold inline marker`() {
        val html = MarkdownToHtmlConverter.render("text **bold** end")
        assertTrue(html.contains("<strong>bold</strong>"))
    }

    @Test
    fun `render applies italic inline marker`() {
        val html = MarkdownToHtmlConverter.render("text _italic_ end")
        assertTrue(html.contains("<em>italic</em>"))
    }

    @Test
    fun `render applies code inline marker`() {
        val html = MarkdownToHtmlConverter.render("text `code` end")
        assertTrue(html.contains("<code class=\"ts-doc-code\">code</code>"))
    }

    @Test
    fun `render escapes html in heading text`() {
        val html = MarkdownToHtmlConverter.render("## <script>")
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun `render strips checkbox from bullet text`() {
        val html = MarkdownToHtmlConverter.render("- [x] done\n- [ ] todo")
        assertTrue(html.contains("<li>done</li>"))
        assertTrue(html.contains("<li>todo</li>"))
    }

    @Test
    fun `render closes section at end of input`() {
        val html = MarkdownToHtmlConverter.render("## Summary\nbody")
        assertTrue(html.endsWith("</div></div></div>"))
    }

    @Test
    fun `escapeHtml returns empty for null`() {
        assertEquals("", MarkdownToHtmlConverter.escapeHtml(null))
    }

    @Test
    fun `escapeHtml returns empty for empty string`() {
        assertEquals("", MarkdownToHtmlConverter.escapeHtml(""))
    }

    @Test
    fun `escapeHtml escapes all special chars`() {
        val input = "<a href=\"foo\">&'text'</a>"
        val escaped = MarkdownToHtmlConverter.escapeHtml(input)
        assertEquals("&lt;a href=&quot;foo&quot;&gt;&amp;&#39;text&#39;&lt;/a&gt;", escaped)
    }

    @Test
    fun `escapeHtml leaves plain text unchanged`() {
        assertEquals("hello world", MarkdownToHtmlConverter.escapeHtml("hello world"))
    }

    @Test
    fun `KNOWN_SECTIONS contains expected keys`() {
        assertNotNull(MarkdownToHtmlConverter.KNOWN_SECTIONS["summary"])
        assertNotNull(MarkdownToHtmlConverter.KNOWN_SECTIONS["zusammenfassung"])
        assertNotNull(MarkdownToHtmlConverter.KNOWN_SECTIONS["requirements"])
        assertNotNull(MarkdownToHtmlConverter.KNOWN_SECTIONS["akzeptanzkriterien"])
        assertNotNull(MarkdownToHtmlConverter.KNOWN_SECTIONS["risks"])
    }

    @Test
    fun `SectionStyle stores icon and cls`() {
        val style = MarkdownToHtmlConverter.SectionStyle(icon = "🚀", cls = "rocket")
        assertEquals("🚀", style.icon)
        assertEquals("rocket", style.cls)
    }
}
