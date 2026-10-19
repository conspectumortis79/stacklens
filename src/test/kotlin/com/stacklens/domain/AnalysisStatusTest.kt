package com.stacklens.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnalysisStatusTest {
    @Test
    fun `status constants match expected wire format`() {
        assertEquals("PENDING", AnalysisStatus.PENDING)
        assertEquals("RUNNING", AnalysisStatus.RUNNING)
        assertEquals("COMPLETED", AnalysisStatus.COMPLETED)
        assertEquals("FAILED", AnalysisStatus.FAILED)
        assertEquals("CANCELLED", AnalysisStatus.CANCELLED)
        assertEquals("ANALYZING", AnalysisStatus.ANALYZING)
        assertEquals("GENERATING", AnalysisStatus.GENERATING)
        assertEquals("BUILDING", AnalysisStatus.BUILDING)
        assertEquals("READY", AnalysisStatus.READY)
    }

    @Test
    fun `all values are non-empty strings`() {
        val values =
            listOf(
                AnalysisStatus.PENDING,
                AnalysisStatus.RUNNING,
                AnalysisStatus.COMPLETED,
                AnalysisStatus.FAILED,
                AnalysisStatus.CANCELLED,
                AnalysisStatus.ANALYZING,
                AnalysisStatus.GENERATING,
                AnalysisStatus.BUILDING,
                AnalysisStatus.READY,
            )
        values.forEach { assertTrue(it.isNotBlank()) }
    }
}
