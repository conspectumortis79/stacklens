package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MissingDomainModelsTest {
    @Test
    fun `ConfluenceTool default status is PENDING`() {
        val tool = ConfluenceTool(name = "tool", confluencePageUrl = "https://confluence/page")
        assertEquals("PENDING", tool.status)
    }

    @Test
    fun `ConfluenceTool default hiddenFromHistory is false`() {
        val tool = ConfluenceTool(name = "t", confluencePageUrl = "u")
        assertFalse(tool.hiddenFromHistory)
    }

    @Test
    fun `ConfluenceTool default id is zero`() {
        val tool = ConfluenceTool(name = "t", confluencePageUrl = "u")
        assertEquals(0L, tool.id)
    }

    @Test
    fun `ConfluenceTool createdAt defaults to current time`() {
        val before = LocalDateTime.now().minusSeconds(1)
        val tool = ConfluenceTool(name = "t", confluencePageUrl = "u")
        val after = LocalDateTime.now().plusSeconds(1)
        assertNotNull(tool.createdAt)
        assertTrue(tool.createdAt.isAfter(before) && tool.createdAt.isBefore(after))
    }

    @Test
    fun `ConfluenceTool optional fields default to null`() {
        val tool = ConfluenceTool(name = "t", confluencePageUrl = "u")
        assertNull(tool.description)
        assertNull(tool.confluenceConfigId)
        assertNull(tool.currentStep)
        assertNull(tool.pageContentText)
        assertNull(tool.generationPrompt)
        assertNull(tool.generatedCode)
        assertNull(tool.jarPath)
        assertNull(tool.lastRunOutput)
        assertNull(tool.lastRunAt)
        assertNull(tool.errorMessage)
        assertNull(tool.completedAt)
        assertNull(tool.icon)
    }

    @Test
    fun `ConfluenceTool can store all fields`() {
        val now = LocalDateTime.now()
        val tool =
            ConfluenceTool(
                id = 5L,
                name = "My Tool",
                description = "A great tool",
                confluencePageUrl = "https://x",
                confluenceConfigId = 2L,
                status = "READY",
                currentStep = "compiled",
                pageContentText = "page text",
                generationPrompt = "prompt",
                generatedCode = "fun main() {}",
                jarPath = "/tmp/tool.jar",
                lastRunOutput = "hello",
                lastRunAt = now,
                errorMessage = null,
                createdAt = now,
                completedAt = now,
                hiddenFromHistory = true,
                icon = "bi-database",
            )
        assertEquals(5L, tool.id)
        assertEquals("My Tool", tool.name)
        assertEquals("A great tool", tool.description)
        assertEquals("https://x", tool.confluencePageUrl)
        assertEquals(2L, tool.confluenceConfigId)
        assertEquals("READY", tool.status)
        assertEquals("compiled", tool.currentStep)
        assertEquals("page text", tool.pageContentText)
        assertEquals("prompt", tool.generationPrompt)
        assertEquals("fun main() {}", tool.generatedCode)
        assertEquals("/tmp/tool.jar", tool.jarPath)
        assertEquals("hello", tool.lastRunOutput)
        assertEquals(now, tool.lastRunAt)
        assertEquals(now, tool.createdAt)
        assertEquals(now, tool.completedAt)
        assertTrue(tool.hiddenFromHistory)
        assertEquals("bi-database", tool.icon)
    }

    @Test
    fun `JiraTicketImplementation default id is zero`() {
        val record = JiraTicketImplementation(ticketKey = "PROJ-1")
        assertEquals(0L, record.id)
    }

    @Test
    fun `JiraTicketImplementation stores required ticketKey`() {
        val record = JiraTicketImplementation(ticketKey = "PROJ-123")
        assertEquals("PROJ-123", record.ticketKey)
    }

    @Test
    fun `JiraTicketImplementation default status is PENDING`() {
        val record = JiraTicketImplementation(ticketKey = "PROJ-1")
        assertEquals("PENDING", record.status)
    }

    @Test
    fun `JiraTicketImplementation default boolean flags are false`() {
        val record = JiraTicketImplementation(ticketKey = "PROJ-1")
        assertFalse(record.runDockerAfterImplementation)
        assertFalse(record.generateSystemTests)
    }

    @Test
    fun `JiraTicketImplementation optional fields default to null`() {
        val record = JiraTicketImplementation(ticketKey = "PROJ-1")
        assertNull(record.jiraConfigId)
        assertNull(record.gitlabConfigIds)
        assertNull(record.gitlabProjectIds)
        assertNull(record.localRepoConfigIds)
        assertNull(record.githubRepoConfigIds)
        assertNull(record.currentStep)
        assertNull(record.errorMessage)
        assertNull(record.ticketJson)
        assertNull(record.problemAnalysisJson)
        assertNull(record.similarTicketsJson)
        assertNull(record.relatedMrsJson)
        assertNull(record.finalAnalysis)
        assertNull(record.processLogJson)
        assertNull(record.codeImplementationJson)
        assertNull(record.codeLanguage)
        assertNull(record.monorepoSubPath)
        assertNull(record.monorepoAnalysisJson)
        assertNull(record.implementationPlanJson)
        assertNull(record.codeVerificationJson)
        assertNull(record.mrUrl)
        assertNull(record.dockerTestOutputJson)
        assertNull(record.liveDockerOutput)
        assertNull(record.completedAt)
        assertNull(record.rawInputText)
    }

    @Test
    fun `McpServerConfig default id is zero`() {
        val config = McpServerConfig(name = "mcp", baseUrl = "http://mcp")
        assertEquals(0L, config.id)
    }

    @Test
    fun `McpServerConfig default active is false`() {
        val config = McpServerConfig(name = "mcp", baseUrl = "http://mcp")
        assertFalse(config.active)
    }

    @Test
    fun `McpServerConfig default routing flags are true`() {
        val config = McpServerConfig(name = "mcp", baseUrl = "http://mcp")
        assertTrue(config.routeJira)
        assertTrue(config.routeConfluence)
        assertTrue(config.routeGitlab)
        assertTrue(config.routeGithub)
        assertTrue(config.routeGrafana)
        assertTrue(config.routeDatabase)
    }

    @Test
    fun `McpServerConfig default jiraCloudMode and confluenceCloudMode are true`() {
        val config = McpServerConfig(name = "mcp", baseUrl = "http://mcp")
        assertTrue(config.jiraCloudMode)
        assertTrue(config.confluenceCloudMode)
    }

    @Test
    fun `McpServerConfig stores required name and baseUrl`() {
        val config = McpServerConfig(name = "mcp", baseUrl = "http://mcp")
        assertEquals("mcp", config.name)
        assertEquals("http://mcp", config.baseUrl)
    }

    @Test
    fun `McpServerConfig can store authToken`() {
        val config = McpServerConfig(name = "mcp", baseUrl = "http://mcp", authToken = "tok")
        assertEquals("tok", config.authToken)
    }

    @Test
    fun `CodeQaAnalysis default id is zero`() {
        val a = CodeQaAnalysis(mrUrl = "https://gitlab/mr/1")
        assertEquals(0L, a.id)
    }

    @Test
    fun `CodeQaAnalysis stores required mrUrl`() {
        val a = CodeQaAnalysis(mrUrl = "https://gitlab/group/proj/-/merge_requests/1")
        assertEquals("https://gitlab/group/proj/-/merge_requests/1", a.mrUrl)
    }

    @Test
    fun `CodeQaAnalysis default status is PENDING`() {
        val a = CodeQaAnalysis(mrUrl = "https://x")
        assertEquals("PENDING", a.status)
    }

    @Test
    fun `CodeQaAnalysis default currentStep and logOutput are empty strings`() {
        val a = CodeQaAnalysis(mrUrl = "https://x")
        assertEquals("", a.currentStep)
        assertEquals("", a.logOutput)
    }

    @Test
    fun `CodeQaAnalysis default integrationTestEnabled is false`() {
        val a = CodeQaAnalysis(mrUrl = "https://x")
        assertFalse(a.integrationTestEnabled)
    }

    @Test
    fun `CodeQaAnalysis optional fields default to null`() {
        val a = CodeQaAnalysis(mrUrl = "https://x")
        assertNull(a.jiraTicketKey)
        assertNull(a.jiraConfigId)
        assertNull(a.gitConfigId)
        assertNull(a.gitPlatform)
        assertNull(a.additionalInstructions)
        assertNull(a.resultMarkdown)
        assertNull(a.akResultJson)
        assertNull(a.guidelinesResultJson)
        assertNull(a.errorMessage)
        assertNull(a.diffBaseSha)
        assertNull(a.diffStartSha)
        assertNull(a.diffHeadSha)
        assertNull(a.completedAt)
        assertNull(a.integrationTestStatus)
        assertNull(a.integrationTestLog)
        assertNull(a.integrationTestResult)
    }

    @Test
    fun `TicketRefinement default id is zero`() {
        val r = TicketRefinement(parentTicketKey = "PROJ-1")
        assertEquals(0L, r.id)
    }

    @Test
    fun `TicketRefinement stores required parentTicketKey`() {
        val r = TicketRefinement(parentTicketKey = "PROJ-42")
        assertEquals("PROJ-42", r.parentTicketKey)
    }

    @Test
    fun `TicketRefinement default status is PENDING`() {
        val r = TicketRefinement(parentTicketKey = "PROJ-1")
        assertEquals("PENDING", r.status)
    }

    @Test
    fun `TicketRefinement default singleTicketOnly is true`() {
        val r = TicketRefinement(parentTicketKey = "PROJ-1")
        assertTrue(r.singleTicketOnly)
    }

    @Test
    fun `TicketRefinement optional fields default to null`() {
        val r = TicketRefinement(parentTicketKey = "PROJ-1")
        assertNull(r.jiraConfigId)
        assertNull(r.templateOverride)
        assertNull(r.currentStep)
        assertNull(r.errorMessage)
        assertNull(r.parentTicketJson)
        assertNull(r.reviewsJson)
        assertNull(r.repoSelectionsJson)
        assertNull(r.databaseConfigId)
        assertNull(r.databaseConfigIds)
        assertNull(r.completedAt)
    }

    @Test
    fun `TicketCreationRecord default id is zero`() {
        val r = TicketCreationRecord()
        assertEquals(0L, r.id)
    }

    @Test
    fun `TicketCreationRecord default values`() {
        val r = TicketCreationRecord()
        assertEquals("", r.inputText)
        assertNull(r.summary)
        assertNull(r.issueType)
        assertNull(r.labels)
        assertNull(r.resultJson)
    }

    @Test
    fun `TicketCreationRecord can store all fields`() {
        val r =
            TicketCreationRecord(
                id = 1L,
                inputText = "my input",
                summary = "Summary",
                issueType = "Story",
                labels = "label1,label2",
                resultJson = "{}",
                status = "COMPLETED",
                errorMessage = null,
                jiraConfigId = 1L,
                jiraProjectKey = "PROJ",
                jiraKey = "PROJ-1",
                jiraUrl = "https://jira/PROJ-1",
            )
        assertEquals(1L, r.id)
        assertEquals("my input", r.inputText)
        assertEquals("Summary", r.summary)
        assertEquals("Story", r.issueType)
        assertEquals("label1,label2", r.labels)
        assertEquals("COMPLETED", r.status)
        assertEquals(1L, r.jiraConfigId)
        assertEquals("PROJ", r.jiraProjectKey)
        assertEquals("PROJ-1", r.jiraKey)
        assertEquals("https://jira/PROJ-1", r.jiraUrl)
    }
}
