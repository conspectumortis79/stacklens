package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DefaultAiJsonRepairerCoverageTest {
    private val repairer = DefaultAiJsonRepairer()

    @Test
    fun `closing brackets without an opening partner leave the stack untouched`() {
        assertNull(repairer.tryRepairTruncatedJson("}"))
        assertNull(repairer.tryRepairTruncatedJson("]"))
    }

    @Test
    fun `mismatched closing bracket does not pop a foreign opener`() {
        assertEquals("{]}", repairer.tryRepairTruncatedJson("{]"))
        assertEquals("[}]", repairer.tryRepairTruncatedJson("[}"))
    }

    @Test
    fun `matching closers pop the stack and a balanced document needs no repair`() {
        assertNull(repairer.tryRepairTruncatedJson("""{"a":[1,2]}"""))
        assertEquals("""{"a":[1,2}]}""", repairer.tryRepairTruncatedJson("""{"a":[1,2}"""))
    }

    @Test
    fun `nested openers are closed inner first`() {
        assertEquals("[[{}]]", repairer.tryRepairTruncatedJson("[[{}"))
    }

    @Test
    fun `openers inside string literals are not counted`() {
        assertNull(repairer.tryRepairTruncatedJson("""{"a":"{[}"}"""))
    }
}
