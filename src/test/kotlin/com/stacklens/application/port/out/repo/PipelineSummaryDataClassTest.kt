package com.stacklens.application.port.out.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PipelineSummaryDataClassTest {
    @Test
    fun `getCharCount returns the data class field value`() {
        val summary =
            PipelineSummary(
                projectTree = "tree",
                fileContents = "content",
                fileCount = 5,
                charCount = 12_345,
            )
        assertEquals(12_345, summary.charCount)
    }

    @Test
    fun `getCharCount returns zero for an empty summary`() {
        val summary = PipelineSummary(projectTree = "", fileContents = "", fileCount = 0, charCount = 0)
        assertEquals(0, summary.charCount)
    }
}
