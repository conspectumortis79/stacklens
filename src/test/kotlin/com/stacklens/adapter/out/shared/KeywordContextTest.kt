package com.stacklens.adapter.out.shared
import com.stacklens.domain.search.KeywordContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeywordContextTest {
    @Test
    fun `findKeywordInContent returns an empty list for a missing keyword`() {
        val matches = KeywordContext.findKeywordInContent("foo bar baz", "qux")
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `findKeywordInContent finds a single occurrence with surrounding context`() {
        val content = "line 1\nline 2 foo\nline 3\nline 4"
        val matches = KeywordContext.findKeywordInContent(content, "foo")
        assertEquals(1, matches.size)
        val (lineNo, ctx) = matches[0]
        assertEquals(2, lineNo)
        assertTrue(ctx.contains("line 1"))
        assertTrue(ctx.contains("line 2 foo"))
        assertTrue(ctx.contains("line 3"))
    }

    @Test
    fun `findKeywordInContent finds multiple occurrences`() {
        val content = "foo\nbar foo\nbaz"
        val matches = KeywordContext.findKeywordInContent(content, "foo")
        assertEquals(2, matches.size)
        assertEquals(1, matches[0].first)
        assertEquals(2, matches[1].first)
    }

    @Test
    fun `findKeywordInContent is case-insensitive`() {
        val content = "Foo\nBAR"
        val matches = KeywordContext.findKeywordInContent(content, "foo")
        assertEquals(1, matches.size)
    }

    @Test
    fun `findKeywordInContent trims context at the start`() {
        val content = "foo"
        val matches = KeywordContext.findKeywordInContent(content, "foo")
        assertEquals(1, matches.size)
        val (_, ctx) = matches[0]
        assertTrue(ctx.startsWith("foo"))
    }

    @Test
    fun `findKeywordInContent trims context at the end`() {
        val content = "foo"
        val matches = KeywordContext.findKeywordInContent(content, "foo")
        assertEquals(1, matches.size)
        val (_, ctx) = matches[0]
        assertTrue(ctx.endsWith("foo"))
    }

    @Test
    fun `findKeywordInContent handles an empty string`() {
        assertTrue(KeywordContext.findKeywordInContent("", "foo").isEmpty())
    }

    @Test
    fun `encode URL-encodes spaces and special characters`() {
        assertEquals("hello+world", KeywordContext.encode("hello world"))
        assertEquals("a%2Fb%2Bc", KeywordContext.encode("a/b+c"))
    }

    @Test
    fun `encode leaves ASCII alphanumerics unchanged`() {
        assertEquals("abcXYZ123", KeywordContext.encode("abcXYZ123"))
    }
}
