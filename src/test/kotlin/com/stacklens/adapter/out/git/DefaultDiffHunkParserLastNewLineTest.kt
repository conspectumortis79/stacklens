package com.stacklens.adapter.out.git

import com.stacklens.domain.util.DiffHunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultDiffHunkParserLastNewLineTest {
    private val parser = DefaultDiffHunkParser()

    @Test
    fun `an empty hunk list has no last new line`() {
        assertEquals(0, parser.lastNewLine(emptyList()))
    }

    @Test
    fun `a hunk without added lines reports its own start`() {
        val hunk = DiffHunk(oldStart = 4, oldCount = 1, newStart = 10, newCount = 0, header = "@@ -4,1 +10,0 @@")

        assertEquals(10, parser.lastNewLine(listOf(hunk)))
    }

    @Test
    fun `the highest covered new line wins`() {
        val low = DiffHunk(oldStart = 1, oldCount = 2, newStart = 3, newCount = 4, header = "@@ -1,2 +3,4 @@")
        val high = DiffHunk(oldStart = 20, oldCount = 2, newStart = 30, newCount = 3, header = "@@ -20,2 +30,3 @@")

        assertEquals(32, parser.lastNewLine(listOf(low, high)))
    }

    @Test
    fun `a hunk that ends earlier than the current maximum does not move it`() {
        val high = DiffHunk(oldStart = 20, oldCount = 2, newStart = 30, newCount = 3, header = "@@ -20,2 +30,3 @@")
        val low = DiffHunk(oldStart = 1, oldCount = 2, newStart = 3, newCount = 4, header = "@@ -1,2 +3,4 @@")

        assertEquals(32, parser.lastNewLine(listOf(high, low)))
    }
}
