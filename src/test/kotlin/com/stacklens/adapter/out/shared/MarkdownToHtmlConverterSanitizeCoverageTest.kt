package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownToHtmlConverterSanitizeCoverageTest {
    @Test
    fun `plain text without any fence is kept line by line`() {
        assertEquals("first line", MarkdownToHtmlConverter.sanitize("first line"))
    }

    @Test
    fun `a fenced block is unwrapped to its content`() {
        assertEquals("content", MarkdownToHtmlConverter.sanitize("```\ncontent\n```"))
    }

    @Test
    fun `a lone fence opener without a body is stripped entirely`() {
        assertEquals("", MarkdownToHtmlConverter.sanitize("```kotlin"))
    }
}
