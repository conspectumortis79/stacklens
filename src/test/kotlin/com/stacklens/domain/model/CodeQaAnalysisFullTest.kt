package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CodeQaAnalysisFullTest {
    @Test
    fun `CodeQaAnalysis default values are correct`() {
        val a = CodeQaAnalysis(mrUrl = "https://example.com/mr/1")
        assertEquals(0L, a.id)
        assertEquals("https://example.com/mr/1", a.mrUrl)
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
        assertNotNull(a.createdAt)
        assertNull(a.completedAt)
        assertFalse(a.integrationTestEnabled)
        assertNull(a.integrationTestStatus)
        assertNull(a.integrationTestLog)
        assertNull(a.integrationTestResult)
    }

    @Test
    fun `CodeQaAnalysis supports mutating all var fields`() {
        val a = CodeQaAnalysis(mrUrl = "https://example.com/mr/1")
        a.mrUrl = "https://example.com/mr/2"
        assertEquals("https://example.com/mr/2", a.mrUrl)
        a.jiraTicketKey = "PROJ-1"
        a.jiraConfigId = 2L
        a.gitConfigId = 3L
        a.gitPlatform = "gitlab"
        a.additionalInstructions = "extra"
        a.status = "RUNNING"
        a.currentStep = "step"
        a.logOutput = "log"
        a.resultMarkdown = "md"
        a.akResultJson = "[{}]"
        a.guidelinesResultJson = "[{}]"
        a.errorMessage = "err"
        a.diffBaseSha = "base"
        a.diffStartSha = "start"
        a.diffHeadSha = "head"
        a.completedAt = java.time.LocalDateTime.now()
        a.integrationTestEnabled = true
        a.integrationTestStatus = "RUNNING"
        a.integrationTestLog = "running"
        a.integrationTestResult = "ok"
        assertEquals("PROJ-1", a.jiraTicketKey)
        assertEquals(2L, a.jiraConfigId)
        assertEquals(3L, a.gitConfigId)
        assertEquals("gitlab", a.gitPlatform)
        assertEquals("extra", a.additionalInstructions)
        assertEquals("RUNNING", a.status)
        assertEquals("step", a.currentStep)
        assertEquals("log", a.logOutput)
        assertEquals("md", a.resultMarkdown)
        assertEquals("[{}]", a.akResultJson)
        assertEquals("[{}]", a.guidelinesResultJson)
        assertEquals("err", a.errorMessage)
        assertEquals("base", a.diffBaseSha)
        assertEquals("start", a.diffStartSha)
        assertEquals("head", a.diffHeadSha)
        assertNotNull(a.completedAt)
        assertEquals(true, a.integrationTestEnabled)
        assertEquals("RUNNING", a.integrationTestStatus)
        assertEquals("running", a.integrationTestLog)
        assertEquals("ok", a.integrationTestResult)
    }

    @Test
    fun `CodeQaAnalysis full construction with all fields`() {
        val now = java.time.LocalDateTime.now()
        val a =
            CodeQaAnalysis(
                id = 9L,
                mrUrl = "https://example.com/mr/2",
                jiraTicketKey = "PROJ-2",
                jiraConfigId = 4L,
                gitConfigId = 5L,
                gitPlatform = "github",
                additionalInstructions = "extra",
                status = "COMPLETED",
                currentStep = "done",
                logOutput = "ok",
                resultMarkdown = "md",
                akResultJson = "[]",
                guidelinesResultJson = "[]",
                errorMessage = null,
                diffBaseSha = "b",
                diffStartSha = "s",
                diffHeadSha = "h",
                completedAt = now,
                integrationTestEnabled = true,
                integrationTestStatus = "COMPLETED",
                integrationTestLog = "passed",
                integrationTestResult = "ok",
            )
        assertEquals(9L, a.id)
        assertEquals("https://example.com/mr/2", a.mrUrl)
        assertEquals("PROJ-2", a.jiraTicketKey)
        assertEquals(4L, a.jiraConfigId)
        assertEquals(5L, a.gitConfigId)
        assertEquals("github", a.gitPlatform)
        assertEquals("COMPLETED", a.status)
        assertEquals("done", a.currentStep)
        assertEquals(now, a.completedAt)
        assertEquals(true, a.integrationTestEnabled)
        assertEquals("COMPLETED", a.integrationTestStatus)
    }
}
