package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownToHtmlConverterCoverageTest {
    @Test
    fun `sanitize unwraps a fenced markdown block`() {
        assertEquals("inner text", MarkdownToHtmlConverter.sanitize("```markdown\ninner text\n```"))
    }

    @Test
    fun `sanitize drops an unterminated opening fence`() {
        assertEquals("body\nmore", MarkdownToHtmlConverter.sanitize("```\nbody\nmore"))
    }

    @Test
    fun `sanitize drops an unterminated closing fence`() {
        assertEquals("body", MarkdownToHtmlConverter.sanitize("body\n```"))
    }

    @Test
    fun `sanitize keeps prose that has no fences at all`() {
        assertEquals("plain prose", MarkdownToHtmlConverter.sanitize("plain prose"))
        assertEquals("", MarkdownToHtmlConverter.sanitize(null))
    }

    @Test
    fun `an ordered list is closed as soon as a plain line follows`() {
        val html = MarkdownToHtmlConverter.render("## Steps\n1. first\n2. second\na plain line\n")

        assertTrue(html.contains("<ol "), html)
        assertTrue(html.contains("</ol>"), html)
    }

    @Test
    fun `a bullet list is closed before an ordered list starts`() {
        val html = MarkdownToHtmlConverter.render("- alpha\n1. beta\n")

        assertTrue(html.contains("</ul>"), html)
        assertTrue(html.contains("<ol "), html)
    }

    @Test
    fun `inline formatting of an empty string yields no markup`() {
        val applyInline =
            MarkdownToHtmlConverter::class.java.declaredMethods
                .first { it.name == "applyInline" }
        applyInline.isAccessible = true

        assertEquals("", applyInline.invoke(MarkdownToHtmlConverter, ""))
    }

    @Test
    fun `known sections are styled while unknown headings stay plain`() {
        val html = MarkdownToHtmlConverter.render("## Security\ncontent\n## Invented\nmore\n")

        assertTrue(html.contains("Security"), html)
        assertTrue(MarkdownToHtmlConverter.KNOWN_SECTIONS.isNotEmpty())
        assertFalse(MarkdownToHtmlConverter.KNOWN_SECTIONS.containsKey("invented"))
    }

    @Test
    fun `a lone fence marker leaves nothing behind`() {
        assertEquals("", MarkdownToHtmlConverter.sanitize("```"))
    }
}
