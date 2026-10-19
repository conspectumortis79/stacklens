package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReviewAnalysisConfigTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val cfg = ReviewAnalysisConfig()
        assertEquals(1L, cfg.id)
        assertEquals("", cfg.jiraSprintUrl)
        assertEquals(null, cfg.jiraConfigId)
        assertEquals("", cfg.analysisInstructions)
    }

    @Test
    fun `named constructor populates all fields`() {
        val cfg =
            ReviewAnalysisConfig(
                id = 1L,
                jiraSprintUrl = "https://jira/board",
                jiraConfigId = 5L,
                analysisInstructions = "Please check X",
            )
        assertEquals(1L, cfg.id)
        assertEquals("https://jira/board", cfg.jiraSprintUrl)
        assertEquals(5L, cfg.jiraConfigId)
        assertEquals("Please check X", cfg.analysisInstructions)
    }

    @Test
    fun `updatedAt is populated to a recent timestamp by default`() {
        val cfg = ReviewAnalysisConfig()
        val now = java.time.LocalDateTime.now()
        val diff =
            java.time.Duration
                .between(cfg.updatedAt, now)
                .seconds
        assertEquals(true, kotlin.math.abs(diff) < 10)
    }
}
