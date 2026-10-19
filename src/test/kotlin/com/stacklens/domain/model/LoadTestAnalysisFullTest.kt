package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LoadTestAnalysisFullTest {
    @Test
    fun `LoadTestAnalysis default values are correct`() {
        val a = LoadTestAnalysis()
        assertEquals(0L, a.id)
        assertNull(a.gitlabConfigId)
        assertNull(a.gitlabProjectId)
        assertNull(a.gitlabProjectPath)
        assertNull(a.repoConfigId)
        assertNull(a.githubRepoConfigId)
        assertEquals("PENDING", a.status)
        assertNull(a.summaryText)
        assertNull(a.findingsJson)
        assertNull(a.k6Script)
        assertNull(a.environmentsJson)
        assertNull(a.errorMessage)
        assertNull(a.modelUsed)
        assertNotNull(a.createdAt)
        assertNull(a.completedAt)
        assertNull(a.k6Output)
        assertNull(a.k6Error)
        assertNull(a.k6ExitCode)
        assertNull(a.k6SummaryJson)
    }

    @Test
    fun `LoadTestAnalysis supports mutating all var fields`() {
        val a = LoadTestAnalysis()
        a.gitlabConfigId = 1L
        a.gitlabProjectId = 2L
        a.gitlabProjectPath = "g/p"
        a.repoConfigId = 3L
        a.githubRepoConfigId = 4L
        a.status = "RUNNING"
        a.summaryText = "summary"
        a.findingsJson = "[]"
        a.k6Script = "script"
        a.environmentsJson = "[]"
        a.errorMessage = "err"
        a.modelUsed = "m"
        a.completedAt = java.time.LocalDateTime.now()
        a.k6Output = "out"
        a.k6Error = "err"
        a.k6ExitCode = 0
        a.k6SummaryJson = "{}"
        assertEquals(1L, a.gitlabConfigId)
        assertEquals(2L, a.gitlabProjectId)
        assertEquals("g/p", a.gitlabProjectPath)
        assertEquals(3L, a.repoConfigId)
        assertEquals(4L, a.githubRepoConfigId)
        assertEquals("RUNNING", a.status)
        assertEquals("summary", a.summaryText)
        assertEquals("[]", a.findingsJson)
        assertEquals("script", a.k6Script)
        assertEquals("[]", a.environmentsJson)
        assertEquals("err", a.errorMessage)
        assertEquals("m", a.modelUsed)
        assertNotNull(a.completedAt)
        assertEquals("out", a.k6Output)
        assertEquals("err", a.k6Error)
        assertEquals(0, a.k6ExitCode)
        assertEquals("{}", a.k6SummaryJson)
    }
}
