package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CopilotAgentPipelineRunDeletionTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `delete does nothing for unknown id`() {
        val svc = makeService()

        svc.delete(99L)
    }

    @Test
    fun `delete removes done run from memory`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 5L, rawText = "test", repoPath = null, featureSlug = "test", createdAt = System.currentTimeMillis())
        run.status = PipelineStatus.DONE
        svc.registry.put(5L, run)
        svc.delete(5L)
        assertNull(svc.get(5L))
    }

    @Test
    fun `delete removes error run from memory`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 51L, rawText = "test", repoPath = null, featureSlug = "test", createdAt = System.currentTimeMillis())
        run.status = PipelineStatus.ERROR
        svc.registry.put(51L, run)
        svc.delete(51L)
        assertNull(svc.get(51L))
    }

    @Test
    fun `delete does NOT remove running run`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 6L, rawText = "test", repoPath = null, featureSlug = "test", createdAt = System.currentTimeMillis())
        run.status = PipelineStatus.RUNNING
        svc.registry.put(6L, run)
        svc.delete(6L)
        assertNotNull(svc.get(6L))
    }

    @Test
    fun `delete does NOT remove pending run`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 61L, rawText = "test", repoPath = null, featureSlug = "test", createdAt = System.currentTimeMillis())
        run.status = PipelineStatus.PENDING
        svc.registry.put(61L, run)
        svc.delete(61L)
        assertNotNull(svc.get(61L))
    }

    @Test
    fun `deleteAll removes done and error runs`() {
        val svc = makeService()
        val run1 = AgentPipelineRun(id = 1L, rawText = "a", repoPath = null, featureSlug = "a", createdAt = System.currentTimeMillis())
        run1.status = PipelineStatus.DONE
        val run2 = AgentPipelineRun(id = 2L, rawText = "b", repoPath = null, featureSlug = "b", createdAt = System.currentTimeMillis())
        run2.status = PipelineStatus.ERROR
        val run3 = AgentPipelineRun(id = 3L, rawText = "c", repoPath = null, featureSlug = "c", createdAt = System.currentTimeMillis())
        run3.status = PipelineStatus.RUNNING
        svc.registry.put(1L, run1)
        svc.registry.put(2L, run2)
        svc.registry.put(3L, run3)
        svc.deleteAll()
        assertNull(svc.get(1L))
        assertNull(svc.get(2L))
        assertNotNull(svc.get(3L))
    }

    @Test
    fun `deleteAll keeps pending runs`() {
        val svc = makeService()
        val run = AgentPipelineRun(id = 4L, rawText = "d", repoPath = null, featureSlug = "d", createdAt = System.currentTimeMillis())
        run.status = PipelineStatus.PENDING
        svc.registry.put(4L, run)
        svc.deleteAll()
        assertNotNull(svc.get(4L))
    }
}
