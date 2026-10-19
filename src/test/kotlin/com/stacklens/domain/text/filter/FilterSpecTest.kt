package com.stacklens.domain.text.filter

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FilterSpecTest {
    private fun minimal(
        maxLines: Int? = null,
        tailLines: Int? = null,
        truncateLinesAt: Int? = null,
        stripLinesMatching: List<Regex> = emptyList(),
        keepLinesMatching: List<Regex> = emptyList(),
    ) = FilterSpec(
        name = "x",
        description = "x",
        maxLines = maxLines,
        tailLines = tailLines,
        truncateLinesAt = truncateLinesAt,
        stripLinesMatching = stripLinesMatching,
        keepLinesMatching = keepLinesMatching,
    )

    @Test fun `isWhitelist is true when keepLinesMatching is non-empty`() {
        assertTrue(minimal(keepLinesMatching = listOf(Regex("a"))).isWhitelist)
    }

    @Test fun `isWhitelist is false when only stripLinesMatching is set`() {
        val s = minimal(stripLinesMatching = listOf(Regex("a")))
        assertFalse(s.isWhitelist)
    }

    @Test fun `negative maxLines is rejected`() {
        assertThrows<IllegalArgumentException> { minimal(maxLines = -1) }
    }

    @Test fun `zero maxLines is allowed`() {
        minimal(maxLines = 0)
    }

    @Test fun `negative tailLines is rejected`() {
        assertThrows<IllegalArgumentException> { minimal(tailLines = -5) }
    }

    @Test fun `zero tailLines is allowed`() {
        minimal(tailLines = 0)
    }

    @Test fun `zero truncateLinesAt is rejected`() {
        assertThrows<IllegalArgumentException> { minimal(truncateLinesAt = 0) }
    }

    @Test fun `negative truncateLinesAt is rejected`() {
        assertThrows<IllegalArgumentException> { minimal(truncateLinesAt = -1) }
    }

    @Test fun `setting both keep and strip is allowed (rtk-compatible)`() {
        val s =
            minimal(
                keepLinesMatching = listOf(Regex("KEEP")),
                stripLinesMatching = listOf(Regex("DROP")),
            )
        assertTrue(s.isWhitelist)
        assertTrue(s.stripLinesMatching.isNotEmpty())
    }

    @Test fun `all defaults are sensible`() {
        val s = minimal()
        assertFalse(s.isWhitelist)
        assertTrue(s.stripAnsi)
        assertFalse(s.filterStderr)
        assertTrue(s.matchOutput.isEmpty())
    }

    @Test fun `FilterResult savingsPercent handles zero original`() {
        val r = FilterResult(text = "x", originalChars = 0, filteredChars = 5, specName = "x")
        assertTrue(r.savingsPercent == 0)
    }

    @Test fun `FilterResult savingsPercent computes correctly`() {
        val r = FilterResult(text = "abc", originalChars = 12, filteredChars = 3, specName = "x")
        assertTrue(r.savingsPercent == 75)
    }
}
