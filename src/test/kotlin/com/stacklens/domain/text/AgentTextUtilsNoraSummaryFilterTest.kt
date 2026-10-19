package com.stacklens.domain.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgentTextUtilsNoraSummaryFilterTest {
    @Test
    fun `extractSlugFromNoraSummary with leading and trailing whitespace in summary`() {
        val out = "**Summary**:   hello world again   "
        val slug = AgentTextUtils.extractSlugFromNoraSummary(out)
        assertEquals("hello-world-again", slug)
    }

    @Test
    fun `extractSlugFromNoraSummary with multiple consecutive spaces in summary`() {
        val out = "**Summary**:  one    two   three  "
        val slug = AgentTextUtils.extractSlugFromNoraSummary(out)
        assertEquals("one-two-three", slug)
    }

    @Test
    fun `extractSlugFromNoraSummary with tabs and spaces in summary`() {
        val out = "**Summary**: \tone\ttwo\tthree "
        val slug = AgentTextUtils.extractSlugFromNoraSummary(out)
        assertEquals("one-two-three", slug)
    }
}
