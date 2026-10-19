package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.StageStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.io.File

class CopilotAgentPipelineChatTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `chat returns error message for unknown id`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        val svc = makeService()
        val result = svc.chat(99L, "hello") {}
        assertTrue(result.contains("99"))
    }

    @Test
    fun `chat returns streaming result for known run`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("response text")
        val svc = makeService()
        val run = AgentPipelineRun(id = 10L, rawText = "test", repoPath = null, featureSlug = "test", createdAt = System.currentTimeMillis())
        svc.registry.put(10L, run)
        val result = svc.chat(10L, "hello") {}
        assertEquals("response text", result)
    }

    @Test
    fun `chat includes stage outputs in context`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        var capturedPrompt = ""
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer {
            capturedPrompt = it.arguments[1] as String
            "ok"
        }
        val svc = makeService()
        val run = AgentPipelineRun(id = 11L, rawText = "feature X", repoPath = null, featureSlug = "feature-x", createdAt = System.currentTimeMillis())
        run.nora.output = "nora output here"
        run.nora.status = StageStatus.DONE
        svc.registry.put(11L, run)
        svc.chat(11L, "explain nora") {}
        assertTrue(capturedPrompt.contains("nora output here"))
    }
}
