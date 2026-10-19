package com.stacklens.adapter.out.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LineDiffCalculatorTest {
    @Test
    fun `lcsDiff returns empty diff for two empty lists`() {
        assertEquals(emptyList<GitDiffBuilder.DiffLine>(), LineDiffCalculator.lcsDiff(emptyList(), emptyList()))
    }

    @Test
    fun `lcsDiff marks every line as added when left is empty`() {
        val res = LineDiffCalculator.lcsDiff(emptyList(), listOf("a", "b"))
        assertEquals(2, res.size)
        assertTrue(res.all { it.sign == "+" })
    }

    @Test
    fun `lcsDiff marks every line as removed when right is empty`() {
        val res = LineDiffCalculator.lcsDiff(listOf("a", "b"), emptyList())
        assertEquals(2, res.size)
        assertTrue(res.all { it.sign == "-" })
    }

    @Test
    fun `lcsDiff returns context lines for identical input`() {
        val res = LineDiffCalculator.lcsDiff(listOf("a", "b"), listOf("a", "b"))
        assertEquals(2, res.size)
        assertTrue(res.all { it.sign == " " })
    }

    @Test
    fun `lcsDiff adds truncation marker when left exceeds MAX_LINES`() {
        val big = (1..5_010).map { "line $it" }
        val res = LineDiffCalculator.lcsDiff(big, emptyList())
        assertTrue(res.any { it.text.contains("abgeschnitten") })
    }

    @Test
    fun `collapseContext passes through short context runs without sentinel`() {
        val change = GitDiffBuilder.DiffLine("+", "change")
        val context = (1..3).map { GitDiffBuilder.DiffLine(" ", "ctx$it") }
        val lines = listOf(change) + context + listOf(change)
        val collapsed = LineDiffCalculator.collapseContext(lines)
        assertEquals(lines.size, collapsed.size)
        assertTrue(collapsed.none { it.sign == "~" })
    }

    @Test
    fun `collapseContext inserts sentinel for long context runs`() {
        val change = GitDiffBuilder.DiffLine("+", "change")
        val context = (1..10).map { GitDiffBuilder.DiffLine(" ", "ctx$it") }
        val lines = listOf(change) + context + listOf(change)
        val collapsed = LineDiffCalculator.collapseContext(lines)
        assertTrue(collapsed.any { it.sign == "~" })
    }
}
