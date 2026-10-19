package com.stacklens.adapter.out.jira

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraMarkdownConverterBranchTest {
    private val converter = JiraMarkdownConverter()

    @Test
    fun `looksLikeMarkdown returns false for empty string`() {
        assertFalse(converter.looksLikeMarkdown(""))
    }

    @Test
    fun `looksLikeMarkdown returns false for whitespace-only string`() {
        assertFalse(converter.looksLikeMarkdown("   \n\t  "))
    }

    @Test
    fun `looksLikeMarkdown returns true for ATX heading`() {
        assertTrue(converter.looksLikeMarkdown("# Hello"))
        assertTrue(converter.looksLikeMarkdown("## Heading 2"))
        assertTrue(converter.looksLikeMarkdown("### Heading 3"))
    }

    @Test
    fun `looksLikeMarkdown returns true for fenced code block`() {
        assertTrue(converter.looksLikeMarkdown("```kotlin\nval x = 1\n```"))
    }

    @Test
    fun `looksLikeMarkdown returns true for bullet list`() {
        assertTrue(converter.looksLikeMarkdown("- item one\n- item two"))
    }

    @Test
    fun `looksLikeMarkdown returns true for bold text`() {
        assertTrue(converter.looksLikeMarkdown("This is **bold** text"))
    }

    @Test
    fun `looksLikeMarkdown returns true for markdown link`() {
        assertTrue(converter.looksLikeMarkdown("See [docs](https://example.com)"))
    }

    @Test
    fun `looksLikeMarkdown returns false for plain wiki text`() {
        assertFalse(converter.looksLikeMarkdown("Just plain text without any markdown markers"))
    }
}
