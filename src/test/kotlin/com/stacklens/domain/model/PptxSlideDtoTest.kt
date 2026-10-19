package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PptxSlideDtoTest {
    @Test
    fun `slide dto exposes the planned question ids`() {
        val slide =
            PptxSlideDto(
                index = 3,
                title = "Question slide",
                backgroundColor = null,
                shapes = emptyList(),
                notes = null,
                rawText = "raw",
                questionIds = listOf("s3-shape1", "s3-shape2"),
            )

        assertEquals(listOf("s3-shape1", "s3-shape2"), slide.questionIds)
    }
}
