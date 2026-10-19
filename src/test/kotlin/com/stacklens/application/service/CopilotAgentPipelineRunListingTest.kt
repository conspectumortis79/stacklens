package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopilotAgentPipelineRunListingTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `allRuns returns empty list initially`() {
        val svc = makeService()
        assertTrue(svc.allRuns().isEmpty())
    }

    @Test
    fun `allRuns returns sorted list`() {
        val svc = makeService()
        val run1 = AgentPipelineRun(id = 1L, rawText = "a", repoPath = null, featureSlug = "a", createdAt = System.currentTimeMillis())
        val run2 = AgentPipelineRun(id = 2L, rawText = "b", repoPath = null, featureSlug = "b", createdAt = System.currentTimeMillis())
        svc.registry.put(1L, run1)
        svc.registry.put(2L, run2)
        val runs = svc.allRuns()
        assertEquals(2L, runs.first().id)
        assertEquals(1L, runs.last().id)
    }

    @Test
    fun `allRuns returns runs ordered by id descending`() {
        val svc = makeService()

        for (i in 1L..5L) {
            val run = AgentPipelineRun(id = i, rawText = "x", repoPath = null, featureSlug = "slug-$i", createdAt = System.currentTimeMillis())
            run.status = PipelineStatus.DONE
            svc.registry.put(i, run)
        }
        val sorted = svc.allRuns()

        for (i in 0 until sorted.size - 1) {
            assertTrue(sorted[i].id > sorted[i + 1].id, "Runs should be sorted by id descending")
        }
    }

    @Test
    fun `effectiveStatus keeps a recent running run running`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 1L, rawText = "x", repoPath = null, featureSlug = "a", createdAt = System.currentTimeMillis())
        run.status = PipelineStatus.RUNNING

        assertEquals("RUNNING", svc.effectiveStatus(run))
    }

    @Test
    fun `effectiveStatus turns a stale running run into an error`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 1L, rawText = "x", repoPath = null, featureSlug = "a", createdAt = 1L)
        run.status = PipelineStatus.RUNNING

        assertEquals("ERROR", svc.effectiveStatus(run))
    }

    @Test
    fun `effectiveStatus turns a stale pending run into an error`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 1L, rawText = "x", repoPath = null, featureSlug = "a", createdAt = 1L)
        run.status = PipelineStatus.PENDING

        assertEquals("ERROR", svc.effectiveStatus(run))
    }

    @Test
    fun `effectiveStatus keeps a stale finished run finished`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 1L, rawText = "x", repoPath = null, featureSlug = "a", createdAt = 1L)
        run.status = PipelineStatus.DONE

        assertEquals("DONE", svc.effectiveStatus(run))
    }
}
