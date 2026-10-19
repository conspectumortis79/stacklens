package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReviewAnalysisTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val a = ReviewAnalysis(jiraSprintUrl = "https://jira/board/1")
        assertEquals(0L, a.id)
        assertEquals("https://jira/board/1", a.jiraSprintUrl)
        assertNull(a.jiraConfigId)
        assertEquals("", a.analysisInstructions)
        assertEquals("PENDING", a.status)
        assertNull(a.currentStep)
        assertNull(a.errorMessage)
        assertNull(a.sprintInfoJson)
        assertNull(a.sprintIssuesJson)
        assertNull(a.resultMarkdown)
        assertNull(a.processLogJson)
    }

    @Test
    fun `named constructor populates all fields`() {
        val a =
            ReviewAnalysis(
                id = 1L,
                jiraSprintUrl = "u",
                jiraConfigId = 2L,
                analysisInstructions = "ai",
                status = "RUNNING",
                currentStep = "step",
                errorMessage = "err",
                sprintInfoJson = "{}",
                sprintIssuesJson = "[]",
                resultMarkdown = "md",
                processLogJson = "[]",
            )
        assertEquals(1L, a.id)
        assertEquals(2L, a.jiraConfigId)
        assertEquals("ai", a.analysisInstructions)
        assertEquals("RUNNING", a.status)
        assertEquals("step", a.currentStep)
        assertEquals("err", a.errorMessage)
        assertEquals("{}", a.sprintInfoJson)
        assertEquals("[]", a.sprintIssuesJson)
        assertEquals("md", a.resultMarkdown)
        assertEquals("[]", a.processLogJson)
    }
}
