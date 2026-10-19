package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.PullRequestDiffPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class CopilotAgentPipelinePullRequestTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `buildPullRequestData returns null when run not found`() {
        val svc = makeService()
        assertNull(svc.buildPullRequestData(999L))
    }

    @Test
    fun `buildPullRequestData returns data with empty diffs when computeDiffs returns null`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 100L, rawText = "feature", repoPath = "/repo", featureSlug = "feat", createdAt = System.currentTimeMillis())
        run.gitBranch = "feat/my-branch"
        svc.registry.put(100L, run)
        whenever(pullRequestDiffPort.computeDiffs(any())).thenReturn(null)
        val result = svc.buildPullRequestData(100L)
        assertNotNull(result)
        assertEquals(100L, result!!.runId)
        assertEquals("feat/my-branch", result.gitBranch)
        assertEquals("", result.baseBranch)
        assertTrue(result.diffs.isEmpty())
    }

    @Test
    fun `buildPullRequestData returns data with diffs when computeDiffs returns result`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 101L, rawText = "feature", repoPath = null, featureSlug = "feat-slug", createdAt = System.currentTimeMillis())

        svc.registry.put(101L, run)
        val diffResult =
            PullRequestDiffPort.DiffResult(
                baseBranch = "main",
                diffs = emptyList(),
            )
        whenever(pullRequestDiffPort.computeDiffs(any())).thenReturn(diffResult)
        val result = svc.buildPullRequestData(101L)
        assertNotNull(result)
        assertEquals("feat-slug", result!!.gitBranch)
        assertEquals("main", result.baseBranch)
    }
}
