package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SecurityAnalysisTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val a = SecurityAnalysis()
        assertEquals(0L, a.id)
        assertNull(a.repoConfigId)
        assertNull(a.gitlabConfigId)
        assertNull(a.gitlabProjectId)
        assertNull(a.gitlabProjectPath)
        assertNull(a.githubRepoConfigId)
        assertEquals("PENDING", a.status)
        assertNull(a.summaryText)
        assertNull(a.findingsJson)
        assertNull(a.errorMessage)
        assertNull(a.modelUsed)
        assertEquals(0, a.currentIteration)
        assertEquals(1, a.totalIterations)
        assertNull(a.currentStep)
    }

    @Test
    fun `named constructor populates all fields`() {
        val now = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
        val a =
            SecurityAnalysis(
                id = 1L,
                repoConfigId = 10L,
                gitlabConfigId = 11L,
                gitlabProjectId = 12L,
                gitlabProjectPath = "g/p",
                githubRepoConfigId = 13L,
                status = "COMPLETED",
                summaryText = "s",
                findingsJson = "[]",
                errorMessage = null,
                modelUsed = "gpt-4",
                currentIteration = 3,
                totalIterations = 3,
                currentStep = "scanning",
                createdAt = now,
                completedAt = now,
            )
        assertEquals(1L, a.id)
        assertEquals(10L, a.repoConfigId)
        assertEquals(11L, a.gitlabConfigId)
        assertEquals(12L, a.gitlabProjectId)
        assertEquals("g/p", a.gitlabProjectPath)
        assertEquals(13L, a.githubRepoConfigId)
        assertEquals("COMPLETED", a.status)
        assertEquals("s", a.summaryText)
        assertEquals("[]", a.findingsJson)
        assertEquals("gpt-4", a.modelUsed)
        assertEquals(3, a.currentIteration)
        assertEquals(3, a.totalIterations)
        assertEquals("scanning", a.currentStep)
    }
}
