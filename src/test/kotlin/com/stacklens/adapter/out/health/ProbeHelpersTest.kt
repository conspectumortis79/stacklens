package com.stacklens.adapter.out.health

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProbeHelpersTest {
    @Test
    fun `truncateMessage returns empty for empty input`() {
        assertEquals("", truncateMessage(""))
        assertEquals("", truncateMessage("   "))
    }

    @Test
    fun `truncateMessage preserves short messages verbatim`() {
        val s = "Connection refused: localhost:5432"
        assertEquals(s, truncateMessage(s))
    }

    @Test
    fun `truncateMessage replaces newlines with single spaces`() {
        val s = "line1\nline2\rline3"
        assertEquals("line1 line2 line3", truncateMessage(s))
    }

    @Test
    fun `truncateMessage trims leading and trailing whitespace`() {
        assertEquals("hello", truncateMessage("  hello\n"))
    }

    @Test
    fun `truncateMessage cuts at maxLen and appends ellipsis`() {
        val raw = "a".repeat(600)
        val out = truncateMessage(raw, maxLen = 100)
        assertEquals(100, out.length)
        assertTrue(out.endsWith("…"))
    }

    @Test
    fun `truncateMessage does not append ellipsis when exactly at limit`() {
        val raw = "a".repeat(99)
        val out = truncateMessage(raw, maxLen = 99)
        assertEquals(99, out.length)
        assertTrue(!out.endsWith("…"))
    }

    @Test
    fun `MAX_ERROR_MESSAGE_LEN has a sensible default`() {
        assertEquals(500, MAX_ERROR_MESSAGE_LEN)
    }
}
