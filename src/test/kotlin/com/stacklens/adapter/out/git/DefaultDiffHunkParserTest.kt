package com.stacklens.adapter.out.git

import com.stacklens.domain.util.DiffHunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultDiffHunkParserTest {
    private val parser = DefaultDiffHunkParser()

    private val sampleDiff =
        """
        --- a/src/Foo.kt
        +++ b/src/Foo.kt
        @@ -10,3 +10,5 @@ fun foo() {
         context-line-1
        -old-line
        +new-line-1
        +new-line-2
         context-line-2
        @@ -50,2 +52,2 @@ fun bar() {
        -old-x
        +new-x
         trailing
        """.trimIndent()

    @Test
    fun `hunks parses all hunk headers`() {
        val hunks = parser.hunks(sampleDiff)
        assertEquals(2, hunks.size)
        assertEquals(10, hunks[0].oldStart)
        assertEquals(3, hunks[0].oldCount)
        assertEquals(10, hunks[0].newStart)
        assertEquals(5, hunks[0].newCount)
        assertEquals(50, hunks[1].oldStart)
        assertEquals(52, hunks[1].newStart)
    }

    @Test
    fun `hunks defaults count to 1 when omitted`() {
        val diff = "@@ -10 +12 @@\n x\n"
        val hunks = parser.hunks(diff)
        assertEquals(1, hunks.size)
        assertEquals(1, hunks[0].oldCount)
        assertEquals(1, hunks[0].newCount)
    }

    @Test
    fun `hunks returns empty list for empty input`() {
        assertEquals(emptyList<DiffHunk>(), parser.hunks(""))
    }

    @Test
    fun `hunks returns empty list when no hunk headers present`() {
        val diff = "some plain text\nno headers here\n"
        assertEquals(emptyList<DiffHunk>(), parser.hunks(diff))
    }

    @Test
    fun `lastNewLine returns the highest newStart plus count minus 1 across hunks`() {
        val hunks = parser.hunks(sampleDiff)
        assertEquals(53, parser.lastNewLine(hunks))
    }

    @Test
    fun `hunkContaining returns the hunk that covers the new line`() {
        val hunks = parser.hunks(sampleDiff)
        val hit = parser.hunkContaining(hunks, 11)
        assertNotNull(hit)
        assertEquals(10, hit!!.newStart)
        val miss = parser.hunkContaining(hunks, 999)
        assertNull(miss)
    }

    @Test
    fun `isPlausibleLine checks if line is inside any hunk`() {
        val hunks = parser.hunks(sampleDiff)
        assertTrue(parser.isPlausibleLine(hunks, 10))
        assertTrue(parser.isPlausibleLine(hunks, 14))
        assertTrue(parser.isPlausibleLine(hunks, 52))
        assertFalse(parser.isPlausibleLine(hunks, 999))
        assertFalse(parser.isPlausibleLine(hunks, 0))
    }

    @Test
    fun `summarise keeps hunk headers and removes minus lines`() {
        val summary = parser.summarise(sampleDiff)
        assertTrue(summary.contains("@@ -10,3 +10,5 @@"))
        assertTrue(summary.contains("@@ -50,2 +52,2 @@"))
        assertFalse(summary.contains("-old-line"))
        assertFalse(summary.contains("-old-x"))
        assertTrue(summary.contains("+new-line-1"))
        assertTrue(summary.contains("+new-x"))
    }

    @Test
    fun `summarise keeps context lines only within per-file context limit`() {
        val summary = parser.summarise(sampleDiff, perFileContextLines = 1)
        assertTrue(summary.contains(" context-line-1"))
        assertFalse(summary.contains(" context-line-2"))
    }

    @Test
    fun `summarise limits body lines per hunk`() {
        val diff =
            """
            @@ -1,0 +1,5 @@
            +a
            +b
            +c
            +d
            +e
            +f
            +g
            """.trimIndent()
        val summary = parser.summarise(diff, perFileContextLines = 0, maxBodyLines = 3)
        assertTrue(summary.contains("+a"))
        assertTrue(summary.contains("+b"))
        assertTrue(summary.contains("+c"))
        assertFalse(summary.contains("+d"))
        assertFalse(summary.contains("+e"))
    }

    @Test
    fun `summarise returns original when no hunks present`() {
        val plain = "just text\nno hunks\n"
        assertEquals(plain, parser.summarise(plain))
    }

    @Test
    fun `summarise strips GitLab-style diff prefixes --- a_ and +++ b_`() {
        val diff = "@@ -1 +1 @@\n-old\n+new\n"
        val summary = parser.summarise(diff)
        assertFalse(summary.contains("-old"))
        assertTrue(summary.contains("+new"))
    }
}
