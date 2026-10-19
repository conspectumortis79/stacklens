package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PipelineAnalysisTest {
    @Test
    fun `setGitlabConfigId updates the value`() {
        val analysis = PipelineAnalysis()
        analysis.gitlabConfigId = 99L
        assertEquals(99L, analysis.gitlabConfigId)
    }

    @Test
    fun `setGitlabProjectId updates the value`() {
        val analysis = PipelineAnalysis()
        analysis.gitlabProjectId = 42L
        assertEquals(42L, analysis.gitlabProjectId)
    }

    @Test
    fun `setGitlabProjectPath updates the value`() {
        val analysis = PipelineAnalysis()
        analysis.gitlabProjectPath = "group/project"
        assertEquals("group/project", analysis.gitlabProjectPath)
    }

    @Test
    fun `setGithubRepoConfigId updates the value`() {
        val analysis = PipelineAnalysis()
        analysis.githubRepoConfigId = 7L
        assertEquals(7L, analysis.githubRepoConfigId)
    }

    @Test
    fun `setModelUsed updates the value`() {
        val analysis = PipelineAnalysis()
        analysis.modelUsed = "gpt-4o"
        assertEquals("gpt-4o", analysis.modelUsed)
    }

    @Test
    fun `setUserPromptAdditions updates the value`() {
        val analysis = PipelineAnalysis()
        analysis.userPromptAdditions = "extra context"
        assertEquals("extra context", analysis.userPromptAdditions)
    }

    @Test
    fun `default status is PENDING`() {
        assertEquals("PENDING", PipelineAnalysis().status)
    }
}
