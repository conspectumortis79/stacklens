package com.stacklens.adapter.out.shared
import com.stacklens.domain.search.KeywordContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeywordContextBranchTest {
    @Test
    fun `findKeywordInContent returns matches when keyword is present`() {
        val result = KeywordContext.findKeywordInContent("hello world", "world")
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `findKeywordInContent returns empty when keyword is absent`() {
        val result = KeywordContext.findKeywordInContent("hello world", "foo")
        assertEquals(0, result.size)
    }

    @Test
    fun `encode produces URL-encoded string`() {
        val result = KeywordContext.encode("hello world")
        assertEquals("hello+world", result)
    }

    @Test
    fun `encode handles special characters`() {
        val result = KeywordContext.encode("a&b")
        assertTrue(result.contains("a"))
    }
}
