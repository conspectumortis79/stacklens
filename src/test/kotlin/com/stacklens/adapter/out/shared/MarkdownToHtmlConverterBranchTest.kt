package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownToHtmlConverterBranchTest {
    private val converter = MarkdownToHtmlConverter

    @Test
    fun `render returns empty for null input`() {
        val result = converter.render(null)
        assertEquals("", result)
    }

    @Test
    fun `render returns empty for blank input`() {
        val result = converter.render("   ")
        assertEquals("", result)
    }

    @Test
    fun `render converts simple markdown`() {
        val result = converter.render("# Hello")
        assertTrue(result.contains("Hello"))
    }

    @Test
    fun `render converts bold text`() {
        val result = converter.render("**bold**")
        assertTrue(result.contains("bold"))
    }

    @Test
    fun `render converts links`() {
        val result = converter.render("[link](https://example.com)")
        assertTrue(result.contains("https://example.com"))
    }

    @Test
    fun `escapeHtml escapes special characters`() {
        val result = MarkdownToHtmlConverter.escapeHtml("<script>")
        assertTrue(!result.contains("<script>"))
    }

    @Test
    fun `sanitize returns empty for null`() {
        val result = converter.sanitize(null)
        assertEquals("", result)
    }
}
