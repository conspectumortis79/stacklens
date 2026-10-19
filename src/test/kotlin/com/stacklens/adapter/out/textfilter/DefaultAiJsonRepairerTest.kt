package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DefaultAiJsonRepairerTest {
    private val repairer = DefaultAiJsonRepairer()

    @Test
    fun `sanitizeAiJson returns empty string for empty input`() {
        assertEquals("", repairer.sanitizeAiJson(""))
    }

    @Test
    fun `sanitizeAiJson replaces fullwidth comma outside string`() {
        assertEquals(",", repairer.sanitizeAiJson("\uFF0C"))
    }

    @Test
    fun `sanitizeAiJson replaces smart quotes outside string`() {
        assertEquals("\"\"", repairer.sanitizeAiJson("\u201C\u201D"))
    }

    @Test
    fun `sanitizeAiJson preserves stray chars inside string literal`() {
        val input = """{"a": "\u201Chello\u201D"}"""
        val out = repairer.sanitizeAiJson(input)
        assertEquals("""{"a": "\u201Chello\u201D"}""", out)
    }

    @Test
    fun `sanitizeAiJson replaces ideographic space`() {
        assertEquals(" ", repairer.sanitizeAiJson("\u3000"))
    }

    @Test
    fun `repairUnbalancedQuotes escapes stray quotes inside content`() {
        val input = """{"a": "say "hi" please"}"""
        val out = repairer.repairUnbalancedQuotes(input)
        assertEquals("""{"a": "say \"hi\" please"}""", out)
    }

    @Test
    fun `repairUnbalancedQuotes keeps terminator quotes`() {
        val input = """{"a": "value"}"""
        val out = repairer.repairUnbalancedQuotes(input)
        assertEquals(input, out)
    }

    @Test
    fun `tryRepairTruncatedJson returns null when balanced`() {
        assertNull(repairer.tryRepairTruncatedJson("""{"a": 1}"""))
    }

    @Test
    fun `tryRepairTruncatedJson closes unclosed object`() {
        val out = repairer.tryRepairTruncatedJson("""{"a": 1""")
        assertEquals("""{"a": 1}""", out)
    }

    @Test
    fun `tryRepairTruncatedJson closes open array`() {
        val out = repairer.tryRepairTruncatedJson("""[1, 2""")
        assertEquals("""[1, 2]""", out)
    }

    @Test
    fun `tryRepairTruncatedJson closes open string literal`() {
        val out = repairer.tryRepairTruncatedJson("""{"a": "hello""")
        assertEquals("""{"a": "hello"}""", out)
    }
}
