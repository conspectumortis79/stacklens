package com.stacklens.adapter.out.git

import com.stacklens.domain.util.DiffHunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DefaultDiffHunkParserCoverageTest {
    private val parser = DefaultDiffHunkParser()

    private val hunk = DiffHunk(oldStart = 1, oldCount = 2, newStart = 5, newCount = 3, header = "@@ -1,2 +5,3 @@")

    @Test
    fun `lastNewLine is zero for an empty hunk list`() {
        assertEquals(0, parser.lastNewLine(emptyList()))
    }

    @Test
    fun `lastNewLine ignores a zero length span`() {
        val empty = DiffHunk(oldStart = 1, oldCount = 1, newStart = 5, newCount = 0, header = "@@ -1 +5 @@")

        assertEquals(5, parser.lastNewLine(listOf(empty)))
    }

    @Test
    fun `lastNewLine reports the final line of the largest hunk`() {
        val first = DiffHunk(oldStart = 1, oldCount = 2, newStart = 3, newCount = 4, header = "@@ -1,2 +3,4 @@")
        val second = DiffHunk(oldStart = 10, oldCount = 1, newStart = 12, newCount = 3, header = "@@ -10 +12,3 @@")

        assertEquals(14, parser.lastNewLine(listOf(first, second)))
    }

    @Test
    fun `hunkContaining returns the hunk that spans the line`() {
        assertSame(hunk, parser.hunkContaining(listOf(hunk), 5))
        assertSame(hunk, parser.hunkContaining(listOf(hunk), 7))
    }

    @Test
    fun `hunkContaining returns null outside the span`() {
        assertNull(parser.hunkContaining(listOf(hunk), 8))
        assertNull(parser.hunkContaining(emptyList(), 1))
    }

    @Test
    fun `summarise keeps added lines and ignores pseudo headers inside the hunk body`() {
        val diff =
            """
            --- a/f.txt
            +++ b/f.txt
            @@ -1,2 +1,3 @@
             context
            +added
            +++extra
            ---stripped
            -old
            """.trimIndent()

        assertEquals(
            "@@ -1,2 +1,3 @@\n context\n+added\n",
            parser.summarise(diff, perFileContextLines = 3, maxBodyLines = 5),
        )
    }

    @Test
    fun `summarise emits only the hunk header when every body line is a deletion`() {
        val diff =
            """
            @@ -1,1 +1,1 @@
            -old
            """.trimIndent()

        assertEquals("@@ -1,1 +1,1 @@\n", parser.summarise(diff, perFileContextLines = 1, maxBodyLines = 3))
    }

    @Test
    fun `summarise returns the raw diff when it holds no hunks`() {
        assertEquals("plain text", parser.summarise("plain text", perFileContextLines = 2, maxBodyLines = 2))
    }

    @Test
    fun `summarise stops after the configured body lines per hunk`() {
        val diff =
            """
            @@ -1,1 +1,4 @@
            +one
            +two
            +three
            """.trimIndent()

        assertEquals("@@ -1,1 +1,4 @@\n+one\n", parser.summarise(diff, perFileContextLines = 2, maxBodyLines = 1))
    }

    @Test
    fun `summarise drops context lines beyond the configured window`() {
        val diff =
            """
            @@ -1,1 +1,6 @@
             one
             two
             three
            """.trimIndent()

        assertEquals("@@ -1,1 +1,6 @@\n one\n", parser.summarise(diff, perFileContextLines = 1, maxBodyLines = 5))
    }
}
