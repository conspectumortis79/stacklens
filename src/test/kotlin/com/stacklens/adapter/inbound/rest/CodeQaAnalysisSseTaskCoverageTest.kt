package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.code.CodeQaAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.CodeQaAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

class CodeQaAnalysisSseTaskCoverageTest {
    private val service = mock<CodeQaAnalysisUseCase>()
    private val emitter = mock<SseEmitter>()
    private val id = 5L
    private var listener: (() -> Unit)? = null
    private var released = false
    private val handle: () -> Unit = { released = true }

    private val controller = CodeQaAnalysisApiController(service, mock<I18nUseCase>())

    init {
        whenever(service.registerStreamingChangeListener(any(), any())).thenAnswer { invocation ->
            listener = invocation.getArgument(1)
            handle
        }
    }

    private fun analysis(
        status: String,
        resultMarkdown: String? = null,
        errorMessage: String? = null,
        integrationTestStatus: String? = null,
    ) = CodeQaAnalysis(
        id = id,
        mrUrl = "https://gitlab.example/g/r/-/merge_requests/1",
        status = status,
        resultMarkdown = resultMarkdown,
        errorMessage = errorMessage,
        integrationTestStatus = integrationTestStatus,
    )

    @Test
    fun `a completed analysis streams the tail of the markdown and closes`() {
        whenever(service.getStreamingOutput(id)).thenReturn("partial")
        whenever(service.getAnalysis(id)).thenReturn(analysis(AnalysisStatus.COMPLETED, resultMarkdown = "partial tail"))

        SseStreamTask(id, emitter, service, pollIntervalMs = 1L).run()

        verify(emitter, times(3)).send(any<SseEmitter.SseEventBuilder>())
        verify(emitter).complete()
        assertTrue(released)
    }

    @Test
    fun `a completed analysis without markdown only reports done`() {
        whenever(service.getStreamingOutput(id)).thenReturn(null)
        whenever(service.getAnalysis(id)).thenReturn(analysis(AnalysisStatus.COMPLETED))

        SseStreamTask(id, emitter, service, pollIntervalMs = 1L).run()

        verify(emitter).complete()
        assertTrue(released)
    }

    @Test
    fun `a disconnected client completes the stream without an error`() {
        whenever(service.getStreamingOutput(id)).thenAnswer {
            throw AsyncRequestNotUsableException(
                "ServletOutputStream failed to write: java.io.IOException: Broken pipe",
                IOException("Broken pipe"),
            )
        }

        SseStreamTask(id, emitter, service, pollIntervalMs = 1L).run()

        verify(emitter).complete()
        verify(emitter, never()).completeWithError(any())
        assertTrue(released)
    }

    @Test
    fun `a disconnected client survives a completion that throws`() {
        whenever(service.getStreamingOutput(id)).thenAnswer {
            throw AsyncRequestNotUsableException(
                "ServletOutputStream failed to write: java.io.IOException: Broken pipe",
                IOException("Broken pipe"),
            )
        }
        doThrow(IllegalStateException("already completed")).whenever(emitter).complete()

        SseStreamTask(id, emitter, service, pollIntervalMs = 1L).run()

        verify(emitter, never()).completeWithError(any())
        assertTrue(released)
    }

    @Test
    fun `a non disconnect io failure completes the stream with an error`() {
        whenever(service.getStreamingOutput(id)).thenAnswer { throw IOException("disk full") }

        SseStreamTask(id, emitter, service, pollIntervalMs = 1L).run()

        verify(emitter).completeWithError(any())
        verify(emitter, never()).complete()
    }

    @Test
    fun `a non io failure completes the stream with an error even when its message looks like a disconnect`() {
        whenever(service.getStreamingOutput(id)).thenAnswer { throw IllegalStateException("Broken pipe") }

        SseStreamTask(id, emitter, service, pollIntervalMs = 1L).run()

        verify(emitter).completeWithError(any())
        verify(emitter, never()).complete()
    }

    @Test
    fun `the registered change listener wakes the polling loop`() {
        whenever(service.getAnalysis(id)).thenReturn(
            analysis(AnalysisStatus.RUNNING),
            analysis(AnalysisStatus.COMPLETED, resultMarkdown = "final"),
        )
        whenever(service.getStreamingOutput(id)).thenAnswer {
            listener?.invoke()
            null
        }

        SseStreamTask(id, emitter, service, pollIntervalMs = 60_000L).run()

        verify(emitter).complete()
    }

    @Test
    fun `an interrupted polling thread completes the emitter and releases the listener`() {
        whenever(service.getAnalysis(id)).thenReturn(analysis(AnalysisStatus.RUNNING))
        whenever(service.getStreamingOutput(id)).thenReturn(null)

        try {
            Thread.currentThread().interrupt()
            SseStreamTask(id, emitter, service, pollIntervalMs = 1L).run()
        } finally {
            Thread.interrupted()
        }

        verify(emitter).complete()
        assertTrue(released)
    }

    @Test
    fun `a failing integration test status closes the fast stream`() {
        whenever(service.getAnalysis(id)).thenReturn(
            analysis(AnalysisStatus.RUNNING, integrationTestStatus = AnalysisStatus.FAILED),
        )

        assertEquals(0L, controller.integrationTestStream(id).timeout)
    }

    @Test
    fun `a cancelled integration test status closes the fast stream`() {
        whenever(service.getAnalysis(id)).thenReturn(
            analysis(AnalysisStatus.RUNNING, integrationTestStatus = AnalysisStatus.CANCELLED),
        )

        assertEquals(0L, controller.integrationTestStream(id).timeout)
    }

    @Test
    fun `a running integration test keeps the long lived stream open`() {
        whenever(service.getAnalysis(id)).thenReturn(
            analysis(AnalysisStatus.RUNNING, integrationTestStatus = AnalysisStatus.RUNNING),
            analysis(AnalysisStatus.RUNNING, integrationTestStatus = AnalysisStatus.COMPLETED),
        )

        assertEquals(30 * 60 * 1000L, controller.integrationTestStream(id).timeout)
    }

    @Test
    fun `an integration test without a status keeps the long lived stream open`() {
        whenever(service.getAnalysis(id)).thenReturn(
            analysis(AnalysisStatus.RUNNING),
            analysis(AnalysisStatus.RUNNING, integrationTestStatus = AnalysisStatus.COMPLETED),
        )

        assertEquals(30 * 60 * 1000L, controller.integrationTestStream(id).timeout)
    }
}

class CodeQaAnalysisIntegrationTestSseTaskCoverageTest {
    private val service = mock<CodeQaAnalysisUseCase>()
    private val emitter = mock<SseEmitter>()
    private val id = 9L

    private fun analysis(status: String?) =
        CodeQaAnalysis(
            id = id,
            mrUrl = "https://gitlab.example/g/r/-/merge_requests/1",
            status = AnalysisStatus.COMPLETED,
            integrationTestStatus = status,
        )

    @Test
    fun `a disconnected client completes the integration test stream without an error`() {
        whenever(service.getIntegrationTestStreamingOutput(id)).thenAnswer {
            throw AsyncRequestNotUsableException(
                "ServletOutputStream failed to write: java.io.IOException: Broken pipe",
                IOException("Broken pipe"),
            )
        }

        IntegrationTestSseTask(id, emitter, service, pollIntervalMs = 1L, waiter = { true }).run()

        verify(emitter).complete()
        verify(emitter, never()).completeWithError(any())
    }

    @Test
    fun `a disconnected integration test client survives a completion that throws`() {
        whenever(service.getIntegrationTestStreamingOutput(id)).thenAnswer {
            throw AsyncRequestNotUsableException(
                "ServletOutputStream failed to write: java.io.IOException: Broken pipe",
                IOException("Broken pipe"),
            )
        }
        doThrow(IllegalStateException("already completed")).whenever(emitter).complete()

        IntegrationTestSseTask(id, emitter, service, pollIntervalMs = 1L, waiter = { true }).run()

        verify(emitter, never()).completeWithError(any())
    }

    @Test
    fun `a finished integration test streams the final log chunk and stops`() {
        whenever(service.getIntegrationTestStreamingOutput(id)).thenReturn("step 1", "step 1 and step 2")
        whenever(service.getAnalysis(id)).thenReturn(analysis(AnalysisStatus.COMPLETED))

        IntegrationTestSseTask(id, emitter, service, pollIntervalMs = 1L, waiter = { true }).run()

        verify(emitter, atLeastOnce()).send(any<SseEmitter.SseEventBuilder>())
        verify(emitter).complete()
    }

    @Test
    fun `a cancelled integration test reports the test done event`() {
        whenever(service.getIntegrationTestStreamingOutput(id)).thenReturn(null)
        whenever(service.getAnalysis(id)).thenReturn(analysis(AnalysisStatus.CANCELLED))

        IntegrationTestSseTask(id, emitter, service, pollIntervalMs = 1L, waiter = { true }).run()

        verify(emitter).complete()
    }

    @Test
    fun `a sleeping poller aborts the stream when the wait is interrupted`() {
        whenever(service.getIntegrationTestStreamingOutput(id)).thenReturn(null)
        whenever(service.getAnalysis(id)).thenReturn(analysis(AnalysisStatus.RUNNING))
        IntegrationTestSseTask(id, emitter, service, pollIntervalMs = 1L, waiter = { false }).run()

        verify(emitter).complete()
    }

    @Test
    fun `an interrupted sleeper completes the emitter`() {
        whenever(service.getIntegrationTestStreamingOutput(id)).thenReturn(null)
        whenever(service.getAnalysis(id)).thenReturn(analysis(AnalysisStatus.RUNNING))
        IntegrationTestSseTask(id, emitter, service, pollIntervalMs = 1L, waiter = { throw InterruptedException("cancelled") }).run()

        verify(emitter).complete()
    }

    @Test
    fun `a failed integration test reports the test done event`() {
        whenever(service.getIntegrationTestStreamingOutput(id)).thenReturn(null)
        whenever(service.getAnalysis(id)).thenReturn(analysis(AnalysisStatus.FAILED))

        IntegrationTestSseTask(id, emitter, service, pollIntervalMs = 1L, waiter = { true }).run()

        verify(emitter).complete()
    }
}
