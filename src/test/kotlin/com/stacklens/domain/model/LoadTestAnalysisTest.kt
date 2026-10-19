package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LoadTestAnalysisTest {
    @Test
    fun `default constructor populates sensible defaults`() {
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
        assertNull(a.k6Output)
        assertNull(a.k6Error)
        assertNull(a.k6ExitCode)
        assertNull(a.k6SummaryJson)
    }
}
