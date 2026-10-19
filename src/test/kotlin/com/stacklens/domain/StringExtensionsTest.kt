package com.stacklens.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringExtensionsTest {
    @Test
    fun `null returns empty list`() {
        val s: String? = null
        assertEquals(emptyList<String>(), s.splitCsvList())
    }

    @Test
    fun `empty string returns empty list`() {
        assertEquals(emptyList<String>(), "".splitCsvList())
    }

    @Test
    fun `blank string returns empty list (trimmed and filtered)`() {
        assertEquals(emptyList<String>(), "   ".splitCsvList())
    }

    @Test
    fun `single value`() {
        assertEquals(listOf("a"), "a".splitCsvList())
    }

    @Test
    fun `multiple values are trimmed`() {
        assertEquals(listOf("a", "b", "c"), "a, b, c".splitCsvList())
    }

    @Test
    fun `empty values are filtered out`() {
        assertEquals(listOf("a", "b"), "a,,b".splitCsvList())
    }

    @Test
    fun `whitespace-only values are filtered out`() {
        assertEquals(listOf("a", "b"), "a, , b".splitCsvList())
    }
}
