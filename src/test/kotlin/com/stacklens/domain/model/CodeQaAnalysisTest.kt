package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CodeQaAnalysisTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val a = CodeQaAnalysis(mrUrl = "https://gitlab.example.com/mr/1")
        assertEquals(0L, a.id)
        assertEquals("https://gitlab.example.com/mr/1", a.mrUrl)
        assertNull(a.jiraTicketKey)
        assertNull(a.jiraConfigId)
        assertNull(a.gitConfigId)
        assertNull(a.gitPlatform)
        assertNull(a.additionalInstructions)
        assertEquals("PENDING", a.status)
        assertEquals("", a.currentStep)
        assertEquals("", a.logOutput)
        assertNull(a.resultMarkdown)
        assertNull(a.akResultJson)
        assertNull(a.guidelinesResultJson)
        assertNull(a.errorMessage)
        assertNull(a.diffBaseSha)
        assertNull(a.diffStartSha)
        assertNull(a.diffHeadSha)
        assertNull(a.completedAt)
        assertFalse(a.integrationTestEnabled)
        assertNull(a.integrationTestStatus)
        assertNull(a.integrationTestLog)
        assertNull(a.integrationTestResult)
    }

    @Test
    fun `named constructor populates all fields`() {
        val a =
            CodeQaAnalysis(
                id = 1L,
                mrUrl = "u",
                jiraTicketKey = "PROJ-1",
                jiraConfigId = 2L,
                gitConfigId = 3L,
                gitPlatform = "gitlab",
                additionalInstructions = "ai",
                status = "COMPLETED",
                currentStep = "step",
                logOutput = "log",
                resultMarkdown = "md",
                akResultJson = "[]",
                guidelinesResultJson = "[]",
                errorMessage = null,
                diffBaseSha = "b",
                diffStartSha = "s",
                diffHeadSha = "h",
                integrationTestEnabled = true,
                integrationTestStatus = "RUNNING",
                integrationTestLog = "ilog",
                integrationTestResult = "ires",
            )
        assertEquals(1L, a.id)
        assertEquals("PROJ-1", a.jiraTicketKey)
        assertEquals(2L, a.jiraConfigId)
        assertEquals(3L, a.gitConfigId)
        assertEquals("gitlab", a.gitPlatform)
        assertEquals("ai", a.additionalInstructions)
        assertEquals("COMPLETED", a.status)
        assertEquals("step", a.currentStep)
        assertEquals("log", a.logOutput)
        assertEquals("md", a.resultMarkdown)
        assertEquals("[]", a.akResultJson)
        assertEquals("[]", a.guidelinesResultJson)
        assertEquals("b", a.diffBaseSha)
        assertEquals("s", a.diffStartSha)
        assertEquals("h", a.diffHeadSha)
        assertEquals(true, a.integrationTestEnabled)
        assertEquals("RUNNING", a.integrationTestStatus)
        assertEquals("ilog", a.integrationTestLog)
        assertEquals("ires", a.integrationTestResult)
    }
}
