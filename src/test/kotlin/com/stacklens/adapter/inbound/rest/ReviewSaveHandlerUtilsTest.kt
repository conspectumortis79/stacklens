package com.stacklens.adapter.inbound.rest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReviewSaveHandlerUtilsTest {
    @Test
    fun `escapeHtml escapes the five standard HTML entities`() {
        assertEquals("&amp;", escapeHtml("&"))
        assertEquals("&lt;", escapeHtml("<"))
        assertEquals("&gt;", escapeHtml(">"))
        assertEquals("&quot;", escapeHtml("\""))
    }

    @Test
    fun `escapeHtml leaves single quotes alone`() {
        assertEquals("'", escapeHtml("'"))
    }

    @Test
    fun `escapeHtml escapes multiple entities in a single string`() {
        assertEquals("&lt;b&gt;a &amp; b&lt;/b&gt;", escapeHtml("<b>a & b</b>"))
    }

    @Test
    fun `escapeForJs escapes backslashes and double quotes`() {
        assertEquals("\\\\", escapeForJs("\\"))
        assertEquals("\\\"", escapeForJs("\""))
    }

    @Test
    fun `escapeForJs converts line breaks to the literal string backslash-n`() {
        assertEquals("a\\nb", escapeForJs("a\nb"))
        assertEquals("a\\nb", escapeForJs("a\r\nb"))
        assertEquals("a\\nb", escapeForJs("a\rb"))
    }

    @Test
    fun `escapeForJs escapes tabs`() {
        assertEquals("a\\tb", escapeForJs("a\tb"))
    }

    @Test
    fun `escapeForJs encodes angle brackets as unicode escapes`() {
        assertEquals("\\u003c", escapeForJs("<"))
        assertEquals("\\u003e", escapeForJs(">"))
    }

    @Test
    fun `stripHtmlBlock removes a fenced html block`() {
        val md =
            """
            Some prose.
            ```html
            <div>HTML</div>
            ```
            More prose.
            """.trimIndent()
        val stripped = stripHtmlBlock(md)
        assertTrue(!stripped.contains("<div>"))
        assertTrue(stripped.contains("Some prose"))
        assertTrue(stripped.contains("More prose"))
    }

    @Test
    fun `stripHtmlBlock returns the input unchanged when there is no fenced html block`() {
        val md = "Just some prose."
        assertEquals(md, stripHtmlBlock(md))
    }

    @Test
    fun `extractHtmlBlock returns the content of a fenced html block`() {
        val md =
            """
            preamble
            ```html
            <p>Hello</p>
            ```
            """.trimIndent()
        assertEquals("<p>Hello</p>\n", extractHtmlBlock(md))
    }

    @Test
    fun `extractHtmlBlock returns null when no html block is present`() {
        assertNull(extractHtmlBlock("no html here"))
    }

    @Test
    fun `extractHtmlBlock returns the input as-is when it starts with DOCTYPE html`() {
        val html = "<!DOCTYPE html><html><body>hi</body></html>"
        assertEquals(html, extractHtmlBlock(html))
    }

    @Test
    fun `extractHtmlBlock returns the input as-is when it starts with html`() {
        val html = "<html><body>hi</body></html>"
        assertEquals(html, extractHtmlBlock(html))
    }

    @Test
    fun `buildHtmlPage produces a self-contained HTML document`() {
        val html = buildHtmlPage("My Title", "# Hello")
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("My Title"))
        assertTrue(html.contains("marked"))
    }

    @Test
    fun `buildHtmlPage escapes the title for HTML safety`() {
        val html = buildHtmlPage("<script>alert(1)</script>", "x")
        assertTrue(!html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `buildHtmlPage embeds the markdown as a JS string literal`() {
        val html = buildHtmlPage("T", "var x = \"\\\"\";")
        assertTrue(html.contains("var x = \\\"\\\\\\\"\\\";"))
    }

    @Test
    fun `buildHtmlPage always has a body container div and a script tag`() {
        val html = buildHtmlPage("T", "x")
        assertTrue(html.contains("<body>"))
        assertTrue(html.contains("id=\"content\""))
        assertTrue(html.contains("<script"))
    }
}
