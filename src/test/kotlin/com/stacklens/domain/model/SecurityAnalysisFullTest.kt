package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SecurityAnalysisFullTest {
    @Test
    fun `SecurityAnalysis default values are correct`() {
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
        assertNotNull(a.createdAt)
        assertNull(a.completedAt)
    }

    @Test
    fun `SecurityAnalysis setter roundtrip preserves all fields`() {
        val now = java.time.LocalDateTime.now()
        val a =
            SecurityAnalysis(
                id = 11L,
                repoConfigId = 5L,
                gitlabConfigId = 7L,
                gitlabProjectId = 42L,
                gitlabProjectPath = "group/proj",
                githubRepoConfigId = 99L,
                status = "RUNNING",
                summaryText = "summary",
                findingsJson = "[{}]",
                errorMessage = "err",
                modelUsed = "gpt-4",
                currentIteration = 2,
                totalIterations = 5,
                currentStep = "scanning",
                completedAt = now,
            )
        assertEquals(11L, a.id)
        assertEquals(5L, a.repoConfigId)
        assertEquals(7L, a.gitlabConfigId)
        assertEquals(42L, a.gitlabProjectId)
        assertEquals("group/proj", a.gitlabProjectPath)
        assertEquals(99L, a.githubRepoConfigId)
        assertEquals("RUNNING", a.status)
        assertEquals("summary", a.summaryText)
        assertEquals("[{}]", a.findingsJson)
        assertEquals("err", a.errorMessage)
        assertEquals("gpt-4", a.modelUsed)
        assertEquals(2, a.currentIteration)
        assertEquals(5, a.totalIterations)
        assertEquals("scanning", a.currentStep)
        assertEquals(now, a.completedAt)
    }

    @Test
    fun `SecurityAnalysis supports mutating all var fields`() {
        val a = SecurityAnalysis()
        a.gitlabConfigId = 1L
        a.gitlabProjectId = 2L
        a.gitlabProjectPath = "x/y"
        a.githubRepoConfigId = 3L
        a.status = "COMPLETED"
        a.summaryText = "s"
        a.findingsJson = "[]"
        a.errorMessage = "e"
        a.modelUsed = "m"
        a.currentIteration = 1
        a.totalIterations = 1
        a.currentStep = "c"
        a.completedAt = java.time.LocalDateTime.now()
        assertEquals(1L, a.gitlabConfigId)
        assertEquals("COMPLETED", a.status)
        assertEquals("s", a.summaryText)
        assertEquals("[]", a.findingsJson)
        assertEquals("e", a.errorMessage)
        assertEquals("m", a.modelUsed)
        assertEquals(1, a.currentIteration)
        assertEquals(1, a.totalIterations)
        assertEquals("c", a.currentStep)
        assertNotNull(a.completedAt)
    }
}
