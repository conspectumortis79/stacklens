package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopilotAgentPipelineRunModelTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `AgentPipelineRun stages returns 11 stages for free-text run`() {
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "hello",
                repoPath = null,
                featureSlug = "hello",
                createdAt = System.currentTimeMillis(),
            )
        assertEquals(12, run.stages.size)
        assertEquals("nora", run.stages.first().name)
    }

    @Test
    fun `AgentPipelineRun stages returns 12 stages for jira-mode run`() {
        val run =
            AgentPipelineRun(
                id = 2L,
                rawText = "PROJ-1",
                repoPath = null,
                featureSlug = "PROJ-1",
                jiraTicketData = "some jira data",
                createdAt = System.currentTimeMillis(),
            )
        assertEquals(13, run.stages.size)
        assertEquals("lena", run.stages.first().name)
    }

    @Test
    fun `AgentPipelineRun last stage is rex for free-text run`() {
        val run = AgentPipelineRun(id = 1L, rawText = "x", repoPath = null, featureSlug = "x", createdAt = System.currentTimeMillis())
        assertEquals("rex", run.stages.last().name)
    }

    @Test
    fun `AgentPipelineRun createdAt is set automatically`() {
        val before = System.currentTimeMillis()
        val run = AgentPipelineRun(id = 1L, rawText = "x", repoPath = null, featureSlug = "x", createdAt = System.currentTimeMillis())
        val after = System.currentTimeMillis()
        assertTrue(run.createdAt in before..after)
    }

    @Test
    fun `AgentPipelineRun initial status is PENDING`() {
        val run = AgentPipelineRun(id = 1L, rawText = "x", repoPath = null, featureSlug = "x", createdAt = System.currentTimeMillis())
        assertEquals(PipelineStatus.PENDING, run.status)
        assertFalse(run.cancelled)
        assertNull(run.currentStage)
        assertNull(run.errorMessage)
    }
}
