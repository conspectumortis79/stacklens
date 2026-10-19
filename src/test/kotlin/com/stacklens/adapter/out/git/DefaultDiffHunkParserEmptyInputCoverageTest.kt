package com.stacklens.adapter.out.git

import com.stacklens.domain.util.DiffHunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultDiffHunkParserEmptyInputCoverageTest {
    private val parser = DefaultDiffHunkParser()

    @Test
    fun `an empty hunk list has no last new line`() {
        assertEquals(0, parser.lastNewLine(emptyList()))
    }

    @Test
    fun `a hunk that adds no line ends on its own start`() {
        assertEquals(10, parser.lastNewLine(listOf(DiffHunk(1, 1, 10, 0, "@@ -1,1 +10 @@"))))
    }

    @Test
    fun `a hunk whose end lies before the requested line is not returned`() {
        assertEquals(null, parser.hunkContaining(listOf(DiffHunk(1, 1, 10, 2, "@@ -1,1 +10,2 @@")), 1))
    }
}
