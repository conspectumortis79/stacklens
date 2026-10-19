package com.stacklens.adapter.inbound.rest.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.PipelineStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class PipelineStreamRunnableCoverageTest {
    private fun run(status: PipelineStatus): AgentPipelineRun =
        AgentPipelineRun(
            id = 7L,
            rawText = "build the thing",
            repoPath = "/tmp/repo",
            featureSlug = "feature",
            createdAt = 0L,
        ).apply { this.status = status }

    @Test
    fun `an interrupt while streaming completes the emitter`() {
        val pipelineRun = run(PipelineStatus.RUNNING)
        val emitter = mock<SseEmitter>()

        Thread.currentThread().interrupt()
        PipelineStreamRunnable(pipelineRun, emitter).run()

        assertFalse(Thread.currentThread().isInterrupted)
        verify(emitter).complete()
    }

    @Test
    fun `a finished run flushes one last event and closes the emitter`() {
        val pipelineRun = run(PipelineStatus.DONE)
        val emitter = mock<SseEmitter>()
        var sent = 0
        whenever(emitter.send(any<SseEmitter.SseEventBuilder>())).thenAnswer {
            sent += 1
            null
        }

        PipelineStreamRunnable(pipelineRun, emitter).run()

        assertEquals(2, sent)
        verify(emitter).complete()
    }

    @Test
    fun `an errored run flushes one last event and closes the emitter`() {
        val pipelineRun = run(PipelineStatus.ERROR)
        val emitter = mock<SseEmitter>()
        var sent = 0
        whenever(emitter.send(any<SseEmitter.SseEventBuilder>())).thenAnswer {
            sent += 1
            null
        }

        PipelineStreamRunnable(pipelineRun, emitter).run()

        assertEquals(2, sent)
        verify(emitter).complete()
    }

    @Test
    fun `a failed send completes the emitter with the failure`() {
        val pipelineRun = run(PipelineStatus.RUNNING)
        val emitter =
            mock<SseEmitter>().also {
                whenever(it.send(any<SseEmitter.SseEventBuilder>())).thenThrow(java.io.IOException("synthetic"))
            }

        PipelineStreamRunnable(pipelineRun, emitter).run()

        verify(emitter).completeWithError(any())
    }
}
