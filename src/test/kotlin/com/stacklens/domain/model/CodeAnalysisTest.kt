package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CodeAnalysisTest {
    @Test
    fun `default status is PENDING`() {
        assertEquals("PENDING", CodeAnalysis().status)
    }
}
