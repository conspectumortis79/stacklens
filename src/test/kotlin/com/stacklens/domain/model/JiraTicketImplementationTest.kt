package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraTicketImplementationTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val t = JiraTicketImplementation(ticketKey = "PROJ-1")
        assertEquals(0L, t.id)
        assertEquals("PROJ-1", t.ticketKey)
        assertNull(t.jiraConfigId)
        assertNull(t.gitlabConfigIds)
        assertNull(t.gitlabProjectIds)
        assertNull(t.localRepoConfigIds)
        assertNull(t.githubRepoConfigIds)
        assertEquals("PENDING", t.status)
        assertNull(t.currentStep)
        assertNull(t.errorMessage)
        assertNull(t.ticketJson)
        assertNull(t.problemAnalysisJson)
        assertNull(t.similarTicketsJson)
        assertNull(t.relatedMrsJson)
        assertNull(t.finalAnalysis)
        assertNull(t.processLogJson)
        assertNull(t.codeImplementationJson)
        assertNull(t.codeLanguage)
        assertNull(t.monorepoSubPath)
        assertNull(t.monorepoAnalysisJson)
        assertNull(t.implementationPlanJson)
        assertNull(t.codeVerificationJson)
        assertNull(t.mrUrl)
        assertFalse(t.runDockerAfterImplementation)
        assertFalse(t.generateSystemTests)
        assertNull(t.dockerTestOutputJson)
        assertNull(t.liveDockerOutput)
        assertNull(t.completedAt)
        assertNull(t.rawInputText)
    }

    @Test
    fun `named constructor populates all fields`() {
        val t =
            JiraTicketImplementation(
                id = 1L,
                ticketKey = "PROJ-1",
                jiraConfigId = 2L,
                gitlabConfigIds = "1,2",
                gitlabProjectIds = "{}",
                localRepoConfigIds = "3",
                githubRepoConfigIds = "4",
                status = "COMPLETED",
                currentStep = "step",
                errorMessage = "err",
                ticketJson = "{}",
                problemAnalysisJson = "{}",
                similarTicketsJson = "[]",
                relatedMrsJson = "[]",
                finalAnalysis = "md",
                processLogJson = "[]",
                codeImplementationJson = "{}",
                codeLanguage = "kotlin",
                monorepoSubPath = "sub",
                monorepoAnalysisJson = "{}",
                implementationPlanJson = "{}",
                codeVerificationJson = "{}",
                mrUrl = "https://gitlab.example.com/mr/1",
                runDockerAfterImplementation = true,
                generateSystemTests = true,
                dockerTestOutputJson = "{}",
                liveDockerOutput = "live",
                rawInputText = "raw",
            )
        assertEquals(1L, t.id)
        assertEquals(2L, t.jiraConfigId)
        assertEquals("1,2", t.gitlabConfigIds)
        assertEquals("{}", t.gitlabProjectIds)
        assertEquals("3", t.localRepoConfigIds)
        assertEquals("4", t.githubRepoConfigIds)
        assertEquals("COMPLETED", t.status)
        assertEquals("step", t.currentStep)
        assertEquals("err", t.errorMessage)
        assertEquals("{}", t.ticketJson)
        assertEquals("{}", t.problemAnalysisJson)
        assertEquals("[]", t.similarTicketsJson)
        assertEquals("[]", t.relatedMrsJson)
        assertEquals("md", t.finalAnalysis)
        assertEquals("[]", t.processLogJson)
        assertEquals("{}", t.codeImplementationJson)
        assertEquals("kotlin", t.codeLanguage)
        assertEquals("sub", t.monorepoSubPath)
        assertEquals("{}", t.monorepoAnalysisJson)
        assertEquals("{}", t.implementationPlanJson)
        assertEquals("{}", t.codeVerificationJson)
        assertEquals("https://gitlab.example.com/mr/1", t.mrUrl)
        assertTrue(t.runDockerAfterImplementation)
        assertTrue(t.generateSystemTests)
        assertEquals("{}", t.dockerTestOutputJson)
        assertEquals("live", t.liveDockerOutput)
        assertEquals("raw", t.rawInputText)
    }
}
