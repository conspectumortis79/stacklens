package com.stacklens.adapter.out.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LineDiffCalculatorCollapseTest {
    private fun line(
        sign: String,
        text: String = "t",
    ) = GitDiffBuilder.DiffLine(sign, text)

    @Test
    fun `an empty diff stays empty`() {
        assertEquals(emptyList<GitDiffBuilder.DiffLine>(), LineDiffCalculator.collapseContext(emptyList()))
    }

    @Test
    fun `long runs of unchanged lines collapse into one marker`() {
        val lines =
            listOf(line("+", "neu")) + (1..8).map { line(" ", "alt$it") } + line("-", "weg")

        val collapsed = LineDiffCalculator.collapseContext(lines)

        assertEquals(
            listOf(
                line("+", "neu"),
                line(" ", "alt1"),
                line(" ", "alt2"),
                line(" ", "alt3"),
                line("~", "@@ … 2 unchanged lines … @@"),
                line(" ", "alt6"),
                line(" ", "alt7"),
                line(" ", "alt8"),
                line("-", "weg"),
            ),
            collapsed,
        )
    }

    @Test
    fun `a trailing unchanged run keeps its own context window`() {
        val lines = listOf(line("+", "neu")) + (1..6).map { line(" ", "alt$it") }

        val collapsed = LineDiffCalculator.collapseContext(lines, context = 2)

        assertEquals(
            listOf(
                line("+", "neu"),
                line(" ", "alt1"),
                line(" ", "alt2"),
                line("~", "@@ … 4 unchanged lines … @@"),
            ),
            collapsed,
        )
    }

    @Test
    fun `a short unchanged run is kept untouched`() {
        val lines = listOf(line("+", "a"), line(" ", "b"), line("-", "c"))

        assertEquals(lines, LineDiffCalculator.collapseContext(lines, context = 3))
    }
}
