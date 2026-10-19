package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JiraTicketImplementationFullTest {
    @Test
    fun `JiraTicketImplementation default values are correct`() {
        val a = JiraTicketImplementation(ticketKey = "PROJ-1")
        assertEquals(0L, a.id)
        assertEquals("PROJ-1", a.ticketKey)
        assertNull(a.jiraConfigId)
        assertNull(a.gitlabConfigIds)
        assertNull(a.gitlabProjectIds)
        assertNull(a.localRepoConfigIds)
        assertNull(a.githubRepoConfigIds)
        assertEquals("PENDING", a.status)
        assertNull(a.currentStep)
        assertNull(a.errorMessage)
        assertNull(a.ticketJson)
        assertNull(a.problemAnalysisJson)
        assertNull(a.similarTicketsJson)
        assertNull(a.relatedMrsJson)
        assertNull(a.finalAnalysis)
        assertNull(a.processLogJson)
        assertNull(a.codeImplementationJson)
        assertNull(a.codeLanguage)
        assertNull(a.monorepoSubPath)
        assertNull(a.monorepoAnalysisJson)
        assertNull(a.implementationPlanJson)
        assertNull(a.codeVerificationJson)
        assertNull(a.mrUrl)
        assertFalse(a.runDockerAfterImplementation)
        assertFalse(a.generateSystemTests)
        assertNull(a.dockerTestOutputJson)
        assertNull(a.liveDockerOutput)
        assertNotNull(a.createdAt)
        assertNull(a.completedAt)
        assertNull(a.rawInputText)
    }

    @Test
    fun `JiraTicketImplementation supports mutating all var fields`() {
        val a = JiraTicketImplementation(ticketKey = "PROJ-1")
        a.ticketKey = "PROJ-2"
        assertEquals("PROJ-2", a.ticketKey)
        a.jiraConfigId = 1L
        a.gitlabConfigIds = "1,2"
        a.gitlabProjectIds = "{}"
        a.localRepoConfigIds = "5"
        a.githubRepoConfigIds = "9"
        a.status = "RUNNING"
        a.currentStep = "step"
        a.errorMessage = "err"
        a.ticketJson = "{}"
        a.problemAnalysisJson = "{}"
        a.similarTicketsJson = "[]"
        a.relatedMrsJson = "[]"
        a.finalAnalysis = "md"
        a.processLogJson = "[]"
        a.codeImplementationJson = "{}"
        a.codeLanguage = "kotlin"
        a.monorepoSubPath = "OrderReferenceBook"
        a.monorepoAnalysisJson = "{}"
        a.implementationPlanJson = "{}"
        a.codeVerificationJson = "{}"
        a.mrUrl = "https://gl/mr/1"
        a.runDockerAfterImplementation = true
        a.generateSystemTests = true
        a.dockerTestOutputJson = "{}"
        a.liveDockerOutput = "live"
        a.completedAt = java.time.LocalDateTime.now()
        a.rawInputText = "raw text"
        assertEquals(1L, a.jiraConfigId)
        assertEquals("1,2", a.gitlabConfigIds)
        assertEquals("{}", a.gitlabProjectIds)
        assertEquals("5", a.localRepoConfigIds)
        assertEquals("9", a.githubRepoConfigIds)
        assertEquals("RUNNING", a.status)
        assertEquals("step", a.currentStep)
        assertEquals("err", a.errorMessage)
        assertEquals("{}", a.ticketJson)
        assertEquals("{}", a.problemAnalysisJson)
        assertEquals("[]", a.similarTicketsJson)
        assertEquals("[]", a.relatedMrsJson)
        assertEquals("md", a.finalAnalysis)
        assertEquals("[]", a.processLogJson)
        assertEquals("{}", a.codeImplementationJson)
        assertEquals("kotlin", a.codeLanguage)
        assertEquals("OrderReferenceBook", a.monorepoSubPath)
        assertEquals("{}", a.monorepoAnalysisJson)
        assertEquals("{}", a.implementationPlanJson)
        assertEquals("{}", a.codeVerificationJson)
        assertEquals("https://gl/mr/1", a.mrUrl)
        assertEquals(true, a.runDockerAfterImplementation)
        assertEquals(true, a.generateSystemTests)
        assertEquals("{}", a.dockerTestOutputJson)
        assertEquals("live", a.liveDockerOutput)
        assertNotNull(a.completedAt)
        assertEquals("raw text", a.rawInputText)
    }

    @Test
    fun `JiraTicketImplementation full construction with all fields`() {
        val now = java.time.LocalDateTime.now()
        val a =
            JiraTicketImplementation(
                id = 11L,
                ticketKey = "PROJ-2",
                jiraConfigId = 5L,
                gitlabConfigIds = "1",
                status = "COMPLETED",
                mrUrl = "https://gl/mr/2",
                codeLanguage = "java",
                monorepoSubPath = "app",
                runDockerAfterImplementation = true,
                generateSystemTests = true,
                rawInputText = "raw",
                completedAt = now,
            )
        assertEquals(11L, a.id)
        assertEquals("PROJ-2", a.ticketKey)
        assertEquals(5L, a.jiraConfigId)
        assertEquals("1", a.gitlabConfigIds)
        assertEquals("COMPLETED", a.status)
        assertEquals("https://gl/mr/2", a.mrUrl)
        assertEquals("java", a.codeLanguage)
        assertEquals("app", a.monorepoSubPath)
        assertEquals(true, a.runDockerAfterImplementation)
        assertEquals(true, a.generateSystemTests)
        assertEquals("raw", a.rawInputText)
        assertEquals(now, a.completedAt)
    }
}
