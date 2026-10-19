package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.MarkdownConvertPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MarkdownConvertPortTest {
    private lateinit var converter: MarkdownConvertPort

    @BeforeEach
    fun setUp() {
        converter = MarkdownToHtmlConverter
    }

    @Test
    fun `sanitize returns empty string for null input`() {
        assertEquals("", converter.sanitize(null))
    }

    @Test
    fun `sanitize returns empty string for blank input`() {
        assertEquals("", converter.sanitize("   "))
        assertEquals("", converter.sanitize("\n\n"))
    }

    @Test
    fun `sanitize returns raw text when no fence is present`() {
        val input = "## Overview\n\nsome content"
        assertEquals(input.trim(), converter.sanitize(input))
    }

    @Test
    fun `sanitize extracts content from markdown fence`() {
        val input = "SUMMARY-1.md\n```markdown\n## Overview\n\nsummary content\n```"
        val expected = "## Overview\n\nsummary content"
        assertEquals(expected, converter.sanitize(input))
    }

    @Test
    fun `sanitize extracts content from md fence`() {
        val input = "```md\n## Header\n\nbody\n```"
        val expected = "## Header\n\nbody"
        assertEquals(expected, converter.sanitize(input))
    }

    @Test
    fun `sanitize extracts content from bare fence`() {
        val input = "```markdown\ncontent here\n```"
        assertEquals("content here", converter.sanitize(input))
    }

    @Test
    fun `sanitize removes stray opening fence line only`() {
        val input = "```\nparagraph one\nparagraph two"
        assertEquals("paragraph one\nparagraph two", converter.sanitize(input))
    }

    @Test
    fun `sanitize removes stray closing fence line only`() {
        val input = "paragraph one\nparagraph two\n```"
        assertEquals("paragraph one\nparagraph two", converter.sanitize(input))
    }

    @Test
    fun `sanitize removes both stray opening and closing fence lines`() {
        val input = "```\nparagraph one\nparagraph two\n```"
        assertEquals("paragraph one\nparagraph two", converter.sanitize(input))
    }

    @Test
    fun `sanitize trims leading and trailing whitespace`() {
        val input = "  \n  ## Header  \n  "
        assertEquals("## Header", converter.sanitize(input))
    }

    @Test
    fun `render returns empty string for null input`() {
        assertEquals("", converter.render(null))
    }

    @Test
    fun `render returns empty string for blank input`() {
        assertEquals("", converter.render(""))
        assertEquals("", converter.render("   "))
    }

    @Test
    fun `render wraps content in ts-doc div`() {
        val input = "plain text"
        val html = converter.render(input)
        assertTrue(html.startsWith("<div class=\"ts-doc\">"))
        assertTrue(html.endsWith("</div>"))
    }

    @Test
    fun `render h1 heading as ts-doc-h2 div`() {
        val input = "# My Heading"
        val html = converter.render(input)
        assertTrue(html.contains("<div class=\"ts-doc-h2\">My Heading</div>"))
    }

    @Test
    fun `render h2 heading with known section gets section styling`() {
        val input = "## Overview\n\nbody text"
        val html = converter.render(input)
        assertTrue(html.contains("<div class=\"ts-doc-section ts-doc-section--overview\">"))
        assertTrue(html.contains("<span class=\"ts-doc-icon\">"))
        assertTrue(html.contains("<span class=\"ts-doc-title\">Overview</span>"))
        assertTrue(html.contains("<div class=\"ts-doc-section-body\">"))
        assertTrue(html.contains("body text"))
    }

    @Test
    fun `render h2 heading with unknown section gets plain h2 styling`() {
        val input = "## Unknown Section\n\nbody"
        val html = converter.render(input)
        assertTrue(html.contains("<div class=\"ts-doc-h2\">Unknown Section</div>"))
        assertTrue(html.contains("body"))
    }

    @Test
    fun `render h3 heading as ts-doc-h3 div`() {
        val input = "### Sub Section"
        val html = converter.render(input)
        assertTrue(html.contains("<div class=\"ts-doc-h3\">Sub Section</div>"))
    }

    @Test
    fun `render bullet list with ul elements`() {
        val input = "- item one\n- item two\n- item three"
        val html = converter.render(input)
        assertTrue(html.contains("<ul class=\"ts-doc-ul\">"))
        assertTrue(html.contains("<li>item one</li>"))
        assertTrue(html.contains("<li>item two</li>"))
        assertTrue(html.contains("<li>item three</li>"))
        assertTrue(html.contains("</ul>"))
    }

    @Test
    fun `render bullet list with asterisk markers`() {
        val input = "* first\n* second"
        val html = converter.render(input)
        assertTrue(html.contains("<ul class=\"ts-doc-ul\">"))
        assertTrue(html.contains("<li>first</li>"))
        assertTrue(html.contains("<li>second</li>"))
    }

    @Test
    fun `render ordered list with ol elements`() {
        val input = "1. first\n2. second\n3. third"
        val html = converter.render(input)
        assertTrue(html.contains("<ol class=\"ts-doc-ol\">"))
        assertTrue(html.contains("<li>first</li>"))
        assertTrue(html.contains("<li>second</li>"))
        assertTrue(html.contains("<li>third</li>"))
        assertTrue(html.contains("</ol>"))
    }

    @Test
    fun `render blockquote as ts-doc-bq div`() {
        val input = "> This is a quote"
        val html = converter.render(input)
        assertTrue(html.contains("<div class=\"ts-doc-bq\">This is a quote</div>"))
    }

    @Test
    fun `render bold text with strong tags`() {
        val input = "This is **bold** text"
        val html = converter.render(input)
        assertTrue(html.contains("<strong>bold</strong>"))
    }

    @Test
    fun `render italic text with em tags`() {
        val input = "This is _italic_ text"
        val html = converter.render(input)
        assertTrue(html.contains("<em>italic</em>"))
    }

    @Test
    fun `render inline code with code tags`() {
        val input = "Use the `println` function"
        val html = converter.render(input)
        assertTrue(html.contains("<code class=\"ts-doc-code\">println</code>"))
    }

    @Test
    fun `render checkbox items strip checkbox marker`() {
        val input = "- [ ] task one\n- [x] task two"
        val html = converter.render(input)
        assertTrue(html.contains("<li>task one</li>"))
        assertTrue(html.contains("<li>task two</li>"))
        assertTrue(!html.contains("[x]"))
        assertTrue(!html.contains("[ ]"))
    }

    @Test
    fun `render horizontal rule as ts-doc-hr`() {
        val input = "before\n\n---\n\nafter"
        val html = converter.render(input)
        assertTrue(html.contains("<hr class=\"ts-doc-hr\">"))
    }

    @Test
    fun `render paragraph with ts-doc-p class`() {
        val input = "Just a paragraph"
        val html = converter.render(input)
        assertTrue(html.contains("<p class=\"ts-doc-p\">Just a paragraph</p>"))
    }

    @Test
    fun `render closes lists when leaving list context`() {
        val input = "- item\n\nplain text"
        val html = converter.render(input)
        assertTrue(html.contains("</ul>"))
        assertTrue(html.contains("<p class=\"ts-doc-p\">plain text</p>"))
    }

    @Test
    fun `render handles empty description in ticket text`() {
        val input = "## Beschreibung\n\n(leer)"
        val html = converter.render(input)
        assertTrue(html.contains("(leer)"))
    }

    @Test
    fun `render handles multiple known section headings`() {
        val input = "## Overview\n\ntext1\n\n## Requirements\n\ntext2"
        val html = converter.render(input)
        assertTrue(html.contains("ts-doc-section--overview"))
        assertTrue(html.contains("ts-doc-section--requirements"))
    }

    @Test
    fun `render handles German section heading overview`() {
        val input = "## Overview\n\ncontent"
        val html = converter.render(input)
        assertTrue(html.contains("ts-doc-section--overview"))
    }

    @Test
    fun `render checkbox without marker is kept as-is`() {
        val input = "- [N/a] option"
        val html = converter.render(input)
        assertTrue(html.contains("<li>[N/a] option</li>"))
    }

    @Test
    fun `KNOWN_SECTIONS contains all expected sections`() {
        val sections = MarkdownToHtmlConverter.KNOWN_SECTIONS
        assertTrue(sections.containsKey("uberblick"))
        assertTrue(sections.containsKey("overview"))
        assertTrue(sections.containsKey("summary"))
        assertTrue(sections.containsKey("zusammenfassung"))
        assertTrue(sections.containsKey("anforderungen"))
        assertTrue(sections.containsKey("requirements"))
        assertTrue(sections.containsKey("akzeptanzkriterien"))
        assertTrue(sections.containsKey("acceptance criteria"))
        assertTrue(sections.containsKey("ak"))
        assertTrue(sections.containsKey("diskussionsverlauf"))
        assertTrue(sections.containsKey("discussion"))
        assertTrue(sections.containsKey("offene punkte"))
        assertTrue(sections.containsKey("open points"))
        assertTrue(sections.containsKey("naechste schritte"))
        assertTrue(sections.containsKey("next steps"))
        assertTrue(sections.containsKey("risiken"))
        assertTrue(sections.containsKey("risks"))
        assertTrue(sections.containsKey("annahmen"))
        assertTrue(sections.containsKey("assumptions"))
    }

    @Test
    fun `KNOWN_SECTIONS maps each key to a SectionStyle with icon and class`() {
        val sections = MarkdownToHtmlConverter.KNOWN_SECTIONS
        sections.forEach { (key, style) ->
            assertNotNull(key)
            assertNotNull(style.icon)
            assertNotNull(style.cls)
            assertTrue(style.icon.isNotEmpty())
            assertTrue(style.cls.isNotEmpty())
        }
    }

    @Test
    fun `sanitize with mixed content and fence in middle`() {
        val input = "intro text\n```markdown\nfenced content\n```\nafter text"
        val result = converter.sanitize(input)
        assertEquals("fenced content", result)
    }

    @Test
    fun `sanitize with fence but no language specifier`() {
        val input = "```\nraw code\n```"
        val result = converter.sanitize(input)
        assertEquals("raw code", result)
    }

    @Test
    fun `render empty lines collapse to no extra content`() {
        val input = "text1\n\n\ntext2"
        val html = converter.render(input)
        assertTrue(html.contains("<p class=\"ts-doc-p\">text1</p>"))
        assertTrue(html.contains("<p class=\"ts-doc-p\">text2</p>"))
    }

    @Test
    fun `render handles section close before final div`() {
        val input = "## Overview\n\npara1\n\n## Summary\n\npara2"
        val html = converter.render(input)
        assertTrue(html.contains("ts-doc-section--overview"))
        assertTrue(html.endsWith("</div>"))
    }

    @Test
    fun `render with CRLF line endings`() {
        val input = "## Header\r\n\r\nbody text"
        val html = converter.render(input)
        assertTrue(html.contains("Header"))
        assertTrue(html.contains("body text"))
    }

    @Test
    fun `sanitize preserves content without fence chars in text`() {
        val input = "No fences here, just normal markdown text"
        assertEquals("No fences here, just normal markdown text", converter.sanitize(input))
    }
}
