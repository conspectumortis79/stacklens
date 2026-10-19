package com.stacklens.application.service.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.testsupport.TestExecutors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CopilotPipelineRegistryTest {
    @Test
    fun `nextRunId increments counter`() {
        val registry = CopilotPipelineRegistry(TestExecutors.daemon())
        val first = registry.nextRunId()
        val second = registry.nextRunId()
        assertEquals(first + 1, second)
    }

    @Test
    fun `seed with higher persistedMaxId bumps counter`() {
        val registry = CopilotPipelineRegistry(TestExecutors.daemon())
        val initial = registry.nextRunId()
        registry.seed(persistedMaxId = 100L)
        val after = registry.nextRunId()
        assertEquals(101L, after)
    }

    @Test
    fun `seed with lower persistedMaxId does not regress counter`() {
        val registry = CopilotPipelineRegistry(TestExecutors.daemon())
        registry.nextRunId()
        registry.nextRunId()
        registry.nextRunId()
        registry.seed(persistedMaxId = 0L)
        val after = registry.nextRunId()
        assertEquals(4L, after)
    }

    @Test
    fun `get returns null for unknown id`() {
        val registry = CopilotPipelineRegistry(TestExecutors.daemon())
        assertEquals(null, registry.get(999L))
    }

    @Test
    fun `put then get returns the run`() {
        val registry = CopilotPipelineRegistry(TestExecutors.daemon())
        val run =
            AgentPipelineRun(
                id = 1L,
                rawText = "x",
                repoPath = null,
                featureSlug = "slug",
                createdAt = System.currentTimeMillis(),
            )
        registry.put(1L, run)
        assertEquals(run, registry.get(1L))
    }
}
