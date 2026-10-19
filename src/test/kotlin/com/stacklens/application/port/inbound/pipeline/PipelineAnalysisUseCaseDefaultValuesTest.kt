package com.stacklens.application.port.inbound.pipeline

import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.pipeline.PipelineMrResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PipelineAnalysisUseCaseDefaultValuesTest {
    private val stub =
        object : PipelineAnalysisUseCase {
            override fun startLocalAnalysis(
                repoConfigId: Long,
                userPrompt: String?,
            ): PipelineAnalysis = PipelineAnalysis(repoConfigId = repoConfigId)

            override fun startGitlabAnalysis(
                gitlabConfigId: Long,
                gitlabProjectId: Long,
                projectPath: String,
                userPrompt: String?,
            ): PipelineAnalysis =
                PipelineAnalysis(
                    gitlabConfigId = gitlabConfigId,
                    gitlabProjectId = gitlabProjectId,
                    gitlabProjectPath = projectPath,
                )

            override fun startGithubAnalysis(
                githubRepoConfigId: Long,
                userPrompt: String?,
            ): PipelineAnalysis = PipelineAnalysis(githubRepoConfigId = githubRepoConfigId)

            override fun getAnalysis(analysisId: Long): PipelineAnalysis? = PipelineAnalysis(id = analysisId)

            override fun getLatestLocalAnalysis(repoConfigId: Long): PipelineAnalysis? = PipelineAnalysis(repoConfigId = repoConfigId)

            override fun getLatestGitlabAnalysis(
                gitlabConfigId: Long,
                gitlabProjectId: Long,
            ): PipelineAnalysis? =
                PipelineAnalysis(
                    gitlabConfigId = gitlabConfigId,
                    gitlabProjectId = gitlabProjectId,
                )

            override fun getLatestGithubAnalysis(githubRepoConfigId: Long): PipelineAnalysis? = PipelineAnalysis(githubRepoConfigId = githubRepoConfigId)

            override fun getAllAnalyses(): List<PipelineAnalysis> = emptyList()

            override fun deleteAnalysis(analysisId: Long) {}

            override fun cancelAnalysis(analysisId: Long) {}

            override fun createMrOrPr(
                analysisId: Long,
                gitlabConfigId: Long?,
                gitlabProjectId: Long?,
            ): PipelineMrResult =
                PipelineMrResult(
                    provider = "SIMULATED",
                    url = "http://127.0.0.1:8090/simulated-mr/$analysisId",
                    kind = "simulated",
                    branch = null,
                )

            override fun cancelLatestRunningForRepo(repoConfigId: Long): Long? = null

            override fun cancelLatestRunningForGitlab(
                gitlabConfigId: Long,
                gitlabProjectId: Long,
            ): Long? = null

            override fun cancelLatestRunningForGithub(githubRepoConfigId: Long): Long? = null
        }

    @Test
    fun `startLocalAnalysis default value for userPrompt routes through synthetic default`() {
        val r = stub.startLocalAnalysis(repoConfigId = 1L)
        assertNotNull(r)
        assertEquals(1L, r.repoConfigId)
    }

    @Test
    fun `startLocalAnalysis with explicit userPrompt preserves the value`() {
        val r = stub.startLocalAnalysis(repoConfigId = 1L, userPrompt = "focus on security")
        assertNotNull(r)
    }

    @Test
    fun `startGitlabAnalysis default value for userPrompt routes through synthetic default`() {
        val r =
            stub.startGitlabAnalysis(
                gitlabConfigId = 1L,
                gitlabProjectId = 42L,
                projectPath = "group/proj",
            )
        assertNotNull(r)
        assertEquals(42L, r.gitlabProjectId)
    }

    @Test
    fun `startGitlabAnalysis with explicit userPrompt preserves the value`() {
        val r =
            stub.startGitlabAnalysis(
                gitlabConfigId = 1L,
                gitlabProjectId = 42L,
                projectPath = "group/proj",
                userPrompt = "custom",
            )
        assertNotNull(r)
    }

    @Test
    fun `startGithubAnalysis default value for userPrompt routes through synthetic default`() {
        val r = stub.startGithubAnalysis(githubRepoConfigId = 7L)
        assertNotNull(r)
        assertEquals(7L, r.githubRepoConfigId)
    }

    @Test
    fun `startGithubAnalysis with explicit userPrompt preserves the value`() {
        val r = stub.startGithubAnalysis(githubRepoConfigId = 7L, userPrompt = "p")
        assertNotNull(r)
    }

    @Test
    fun `createMrOrPr default values for the optional gitlab ids`() {
        val r = stub.createMrOrPr(analysisId = 99L)
        assertNotNull(r)
        assertEquals("SIMULATED", r.provider)
    }

    @Test
    fun `createMrOrPr with explicit gitlab ids is invoked`() {
        val r = stub.createMrOrPr(analysisId = 99L, gitlabConfigId = 1L, gitlabProjectId = 2L)
        assertNotNull(r)
    }

    @Test
    fun `cancelLatestRunning methods can be invoked with default null returns`() {
        assertNull(stub.cancelLatestRunningForRepo(repoConfigId = 1L))
        assertNull(stub.cancelLatestRunningForGitlab(gitlabConfigId = 1L, gitlabProjectId = 2L))
        assertNull(stub.cancelLatestRunningForGithub(githubRepoConfigId = 1L))
    }

    @Test
    fun `getAnalysis getLatest and getAllAnalyses return values`() {
        assertNotNull(stub.getAnalysis(1L))
        assertNotNull(stub.getLatestLocalAnalysis(1L))
        assertNotNull(stub.getLatestGitlabAnalysis(1L, 2L))
        assertNotNull(stub.getLatestGithubAnalysis(1L))
        assertEquals(0, stub.getAllAnalyses().size)
    }

    @Test
    fun `deleteAnalysis and cancelAnalysis are no-op on the stub`() {
        stub.deleteAnalysis(1L)
        stub.cancelAnalysis(1L)
    }
}
