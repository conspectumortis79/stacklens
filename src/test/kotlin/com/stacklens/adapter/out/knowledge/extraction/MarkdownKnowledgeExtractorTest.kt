package com.stacklens.adapter.out.knowledge.extraction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownKnowledgeExtractorTest {
    private val extractor = MarkdownKnowledgeExtractor()

    @Test
    fun `supports recognises md files case-insensitively`() {
        assertTrue(extractor.supports("README.md", ByteArray(0)))
        assertTrue(extractor.supports("README.MD", ByteArray(0)))
        assertTrue(extractor.supports("Foo.Md", ByteArray(0)))
    }

    @Test
    fun `supports recognises markdown files case-insensitively`() {
        assertTrue(extractor.supports("README.markdown", ByteArray(0)))
        assertTrue(extractor.supports("Foo.Markdown", ByteArray(0)))
    }

    @Test
    fun `supports rejects non-markdown files`() {
        assertFalse(extractor.supports("README.txt", ByteArray(0)))
        assertFalse(extractor.supports("README", ByteArray(0)))
    }

    @Test
    fun `extract removes fenced code blocks`() {
        val md = "before\n```\nval x = 1\n```\nafter"
        val result = extractor.extract("a.md", md.toByteArray())
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `extract removes HTML comments`() {
        val md = "before <!-- hidden --> after"
        val result = extractor.extract("a.md", md.toByteArray())
        assertTrue(result.contains("before"))
        assertTrue(result.contains("after"))
    }

    @Test
    fun `extract collapses tabs and spaces`() {
        val md = "a\t\tb   c"
        val result = extractor.extract("a.md", md.toByteArray())
        assertEquals("a b c", result)
    }

    @Test
    fun `extract collapses more than two newlines`() {
        val md = "a\n\n\n\nb"
        val result = extractor.extract("a.md", md.toByteArray())
        assertEquals("a\n\nb", result)
    }
}
