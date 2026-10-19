package com.stacklens.adapter.out.review.pptx

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PptxQuestionIdsTest {
    @Test
    fun `shape question ids follow the application grammar`() {
        assertEquals("s1-shape0", PptxQuestionIds.shape(1, 0))
        assertEquals("s12-shape34", PptxQuestionIds.shape(12, 34))
    }
}
