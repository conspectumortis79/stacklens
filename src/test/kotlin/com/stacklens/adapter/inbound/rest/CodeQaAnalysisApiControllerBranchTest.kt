package com.stacklens.adapter.inbound.rest

import com.stacklens.application.service.CodeQaAnalysisService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.CodeQaAnalysis
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class CodeQaAnalysisApiControllerBranchTest {
    private val service = mock<CodeQaAnalysisService>()

    @BeforeEach
    fun stubChangeListener() {
        whenever(service.registerStreamingChangeListener(any(), any())).thenReturn({})
    }

    @Test
    fun `SseStreamTask handles null partial output`() {
        whenever(service.getStreamingOutput(any())).thenReturn(null)
        val task =
            SseStreamTask(
                id = 1L,
                emitter = SseEmitter(),
                analysisService = service,
                pollIntervalMs = 1L,
            )
        task.run()
    }

    @Test
    fun `SseStreamTask handles partial output growth`() {
        whenever(service.getStreamingOutput(any()))
            .thenReturn("partial output")
            .thenReturn("partial output with more text")
        val analysis =
            CodeQaAnalysis(id = 1L, mrUrl = "", status = AnalysisStatus.COMPLETED, resultMarkdown = "partial output with more text")
        whenever(service.getAnalysis(any())).thenReturn(analysis)
        val task =
            SseStreamTask(
                id = 1L,
                emitter = SseEmitter(),
                analysisService = service,
                pollIntervalMs = 1L,
            )
        task.run()
    }

    @Test
    fun `SseStreamTask handles FAILED status`() {
        whenever(service.getStreamingOutput(any())).thenReturn(null)
        val analysis =
            CodeQaAnalysis(id = 1L, mrUrl = "", status = AnalysisStatus.FAILED, errorMessage = "boom")
        whenever(service.getAnalysis(any())).thenReturn(analysis)
        val task =
            SseStreamTask(
                id = 1L,
                emitter = SseEmitter(),
                analysisService = service,
                pollIntervalMs = 1L,
            )
        task.run()
    }

    @Test
    fun `SseStreamTask handles CANCELLED status`() {
        whenever(service.getStreamingOutput(any())).thenReturn(null)
        val analysis =
            CodeQaAnalysis(id = 1L, mrUrl = "", status = AnalysisStatus.CANCELLED, errorMessage = "cancelled")
        whenever(service.getAnalysis(any())).thenReturn(analysis)
        val task =
            SseStreamTask(
                id = 1L,
                emitter = SseEmitter(),
                analysisService = service,
                pollIntervalMs = 1L,
            )
        task.run()
    }

    @Test
    fun `SseStreamTask handles null analysis`() {
        whenever(service.getStreamingOutput(any())).thenReturn(null)
        whenever(service.getAnalysis(any())).thenReturn(null)
        val task =
            SseStreamTask(
                id = 999L,
                emitter = SseEmitter(),
                analysisService = service,
                pollIntervalMs = 1L,
            )
        task.run()
    }

    @Test
    fun `SseStreamTask swallows exceptions`() {
        whenever(service.getStreamingOutput(any())).thenThrow(RuntimeException("boom"))
        val task =
            SseStreamTask(
                id = 1L,
                emitter = SseEmitter(),
                analysisService = service,
                pollIntervalMs = 1L,
            )
        task.run()
    }

    @Test
    fun `SseStreamTask registers a change listener when running`() {
        whenever(service.registerStreamingChangeListener(any(), any())).thenAnswer { invocation ->
            val id = invocation.arguments[0] as Long

            @Suppress("UNCHECKED_CAST")
            val listener = invocation.arguments[1] as () -> Unit
            val handle: () -> Unit = { service.unregisterStreamingChangeListener(id, listener) }
            handle
        }
        whenever(service.getStreamingOutput(any())).thenReturn(null)
        whenever(service.getAnalysis(any())).thenReturn(null)
        val task =
            SseStreamTask(
                id = 42L,
                emitter = SseEmitter(),
                analysisService = service,
                pollIntervalMs = 10L,
            )
        task.run()
        org.mockito.kotlin
            .verify(service)
            .registerStreamingChangeListener(
                org.mockito.kotlin.eq(42L),
                org.mockito.kotlin.any(),
            )
        org.mockito.kotlin
            .verify(service)
            .unregisterStreamingChangeListener(
                org.mockito.kotlin.eq(42L),
                org.mockito.kotlin.any(),
            )
    }
}
