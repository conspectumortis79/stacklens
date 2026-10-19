package com.stacklens.adapter.inbound.rest

import com.stacklens.application.service.CodeQaAnalysisService
import com.stacklens.application.service.I18nService
import com.stacklens.domain.model.CodeQaAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CodeQaAnalysisApiControllerSseTest {
    private val service: CodeQaAnalysisService = mock()
    private val i18n: I18nService = mock()
    private val controller = CodeQaAnalysisApiController(service, i18n)

    @BeforeEach
    fun stubChangeListener() {
        whenever(service.registerStreamingChangeListener(any(), any())).thenReturn({})
    }

    private fun analysis(
        id: Long = 1L,
        status: String = "COMPLETED",
        resultMarkdown: String? = null,
        errorMessage: String? = null,
        integrationTestStatus: String? = null,
        integrationTestLog: String? = null,
    ) = CodeQaAnalysis(
        id = id,
        mrUrl = "https://gitlab.example.com/group/repo/-/merge_requests/1",
        status = status,
        resultMarkdown = resultMarkdown,
        errorMessage = errorMessage,
        integrationTestStatus = integrationTestStatus,
        integrationTestLog = integrationTestLog,
    )

    @Test
    fun `CodeQaSseThreadFactory creates daemon thread with name mr-sse`() {
        val factory = CodeQaSseThreadFactory()
        val thread = factory.newThread { }
        assertEquals("mr-sse", thread.name)
        assertTrue(thread.isDaemon)
    }

    @Test
    fun `FutureCanceller run cancels future`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit { Thread.sleep(10_000) }
            val canceller = FutureCanceller(future)
            canceller.run()
            assertTrue(future.isCancelled)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `FutureCanceller accept cancels future on error`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit { Thread.sleep(10_000) }
            val canceller = FutureCanceller(future)
            canceller.accept(RuntimeException("boom"))
            assertTrue(future.isCancelled)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `SseStreamTask emits done and completes on COMPLETED`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)

        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", resultMarkdown = "hello world"),
        )
        whenever(service.getStreamingOutput(1L)).thenReturn("hello world")

        val task = SseStreamTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()

        verify(service, atLeastOnce()).getAnalysis(1L)
        verify(service, atLeastOnce()).getStreamingOutput(1L)
        assertTrue(capturing.completed.get())
    }

    @Test
    fun `SseStreamTask emits error and completes on FAILED`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)

        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "FAILED", errorMessage = "compile error"),
        )
        whenever(service.getStreamingOutput(1L)).thenReturn(null)

        val task = SseStreamTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()

        assertTrue(capturing.completed.get())
    }

    @Test
    fun `SseStreamTask emits error and completes on CANCELLED`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)

        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "CANCELLED", errorMessage = null),
        )
        whenever(service.getStreamingOutput(1L)).thenReturn(null)

        val task = SseStreamTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()

        assertTrue(capturing.completed.get())
    }

    @Test
    fun `SseStreamTask emits not-found error when analysis is null`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)

        whenever(service.getAnalysis(1L)).thenReturn(null)
        whenever(service.getStreamingOutput(1L)).thenReturn(null)

        val task = SseStreamTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()

        assertTrue(capturing.completed.get())
    }

    @Test
    fun `SseStreamTask emits chunk when result markdown grows between polls`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)

        var callCount = 0
        whenever(service.getAnalysis(1L)).thenAnswer {
            callCount++
            if (callCount == 1) {
                analysis(1L, status = "COMPLETED", resultMarkdown = "hello")
            } else {
                null
            }
        }
        whenever(service.getStreamingOutput(1L)).thenReturn("hello world")

        val task = SseStreamTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()

        assertTrue(capturing.completed.get())
    }

    @Test
    fun `SseStreamTask swallows InterruptedException by completing emitter`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = ThrowingEmitter(sentChunks, IllegalStateException("send failure"))

        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "RUNNING"),
        )
        whenever(service.getStreamingOutput(1L)).thenReturn("partial")

        val task = SseStreamTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()
    }

    @Test
    fun `SseStreamTask swallows generic exception from emitter send`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = ThrowingEmitter(sentChunks, RuntimeException("transient"))

        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "RUNNING"),
        )
        whenever(service.getStreamingOutput(1L)).thenReturn("partial")

        val task = SseStreamTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()
    }

    @Test
    fun `SseStreamTask sends initial chunk when partial is non-null and completes after status change`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)

        whenever(service.getStreamingOutput(1L)).thenReturn("first")
        var callCount = 0
        whenever(service.getAnalysis(1L)).thenAnswer {
            callCount++
            if (callCount >= 2) {
                analysis(1L, status = "COMPLETED", resultMarkdown = "first")
            } else {
                analysis(1L, status = "RUNNING")
            }
        }

        val task = SseStreamTask(1L, capturing, service, pollIntervalMs = 5L)
        task.run()
        assertTrue(capturing.completed.get())
    }

    @Test
    fun `IntegrationTestSseTask emits test-done COMPLETED when integration test COMPLETED`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)

        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", integrationTestStatus = "COMPLETED", integrationTestLog = "log line"),
        )
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn("log line")

        val task = IntegrationTestSseTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()

        assertTrue(capturing.completed.get())
    }

    @Test
    fun `IntegrationTestSseTask emits test-done FAILED when integration test FAILED`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)

        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", integrationTestStatus = "FAILED"),
        )
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn(null)

        val task = IntegrationTestSseTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()

        assertTrue(capturing.completed.get())
    }

    @Test
    fun `IntegrationTestSseTask emits test-done CANCELLED when integration test CANCELLED`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)

        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", integrationTestStatus = "CANCELLED"),
        )
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn(null)

        val task = IntegrationTestSseTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()

        assertTrue(capturing.completed.get())
    }

    @Test
    fun `IntegrationTestSseTask emits test-error when integration test status is null`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)

        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "RUNNING", integrationTestStatus = null),
        )
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn(null)

        val task = IntegrationTestSseTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()

        assertTrue(capturing.completed.get())
        val payloads = eventPayloads(sentChunks).joinToString("\n")
        assertTrue(payloads.contains("event:test-error"), "expected test-error event but got $payloads")
        assertTrue(payloads.contains("not found"), "expected not-found message but got $payloads")
        assertFalse(payloads.contains("COMPLETED"), "the vanished analysis must not report COMPLETED")
    }

    @Test
    fun `IntegrationTestSseTask sends intermediate chunks while running`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)

        var callCount = 0
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn("partial")
        whenever(service.getAnalysis(1L)).thenAnswer {
            callCount++
            if (callCount >= 2) {
                analysis(1L, status = "COMPLETED", integrationTestStatus = "COMPLETED")
            } else {
                analysis(1L, status = "RUNNING", integrationTestStatus = "RUNNING")
            }
        }

        val task = IntegrationTestSseTask(1L, capturing, service, pollIntervalMs = 5L)
        task.run()
        assertTrue(capturing.completed.get())
    }

    @Test
    fun `IntegrationTestSseTask swallows InterruptedException`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = ThrowingEmitter(sentChunks, IllegalStateException("send fail"))

        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", integrationTestStatus = "RUNNING"),
        )
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn("log")

        val task = IntegrationTestSseTask(1L, capturing, service, pollIntervalMs = 5L)
        task.run()
    }

    @Test
    fun `IntegrationTestSseTask swallows generic exception from emitter`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = ThrowingEmitter(sentChunks, RuntimeException("boom"))

        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", integrationTestStatus = "RUNNING"),
        )
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn("log")

        val task = IntegrationTestSseTask(1L, capturing, service, pollIntervalMs = 5L)
        task.run()
    }

    @Test
    fun `IntegrationTestSseTask sends final partial chunk after terminal state`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)

        var outputCallCount = 0
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", integrationTestStatus = "COMPLETED"),
        )
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenAnswer {
            outputCallCount++
            if (outputCallCount >= 2) {
                "log final"
            } else {
                "log "
            }
        }

        val task = IntegrationTestSseTask(1L, capturing, service, pollIntervalMs = 5L)
        task.run()
        assertTrue(capturing.completed.get())
    }

    @Test
    fun `stream returns emitter that sends chunk and done when COMPLETED with resultMarkdown`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", resultMarkdown = "final result"),
        )
        val emitter = controller.stream(1L)
        assertNotNull(emitter)
    }

    @Test
    fun `stream returns emitter that sends chunk and done when COMPLETED with null resultMarkdown`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", resultMarkdown = null),
        )
        val emitter = controller.stream(1L)
        assertNotNull(emitter)
    }

    @Test
    fun `stream returns emitter that sends analysis-error when FAILED`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "FAILED", errorMessage = "boom"),
        )
        val emitter = controller.stream(1L)
        assertNotNull(emitter)
    }

    @Test
    fun `stream returns emitter that sends analysis-error when CANCELLED`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "CANCELLED"),
        )
        val emitter = controller.stream(1L)
        assertNotNull(emitter)
    }

    @Test
    fun `stream returns error emitter when analysis not found`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val emitter = controller.stream(99L)
        assertNotNull(emitter)
    }

    @Test
    fun `stream returns emitter that submits task when RUNNING`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "RUNNING"),
        )
        val emitter = controller.stream(1L)
        assertNotNull(emitter)
    }

    @Test
    fun `integrationTestStream returns error emitter when analysis not found`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        val emitter = controller.integrationTestStream(99L)
        assertNotNull(emitter)
    }

    @Test
    fun `integrationTestStream returns emitter with log when integration test COMPLETED`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", integrationTestStatus = "COMPLETED", integrationTestLog = "log"),
        )
        val emitter = controller.integrationTestStream(1L)
        assertNotNull(emitter)
    }

    @Test
    fun `integrationTestStream returns emitter with log when integration test FAILED`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", integrationTestStatus = "FAILED", integrationTestLog = "log"),
        )
        val emitter = controller.integrationTestStream(1L)
        assertNotNull(emitter)
    }

    @Test
    fun `integrationTestStream returns emitter when integration test CANCELLED`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", integrationTestStatus = "CANCELLED", integrationTestLog = "log"),
        )
        val emitter = controller.integrationTestStream(1L)
        assertNotNull(emitter)
    }

    @Test
    fun `integrationTestStream returns emitter with no log when integration test COMPLETED and log is null`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", integrationTestStatus = "COMPLETED", integrationTestLog = null),
        )
        val emitter = controller.integrationTestStream(1L)
        assertNotNull(emitter)
    }

    @Test
    fun `integrationTestStream returns emitter with no log when integration test COMPLETED and log is blank`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "COMPLETED", integrationTestStatus = "COMPLETED", integrationTestLog = "   "),
        )
        val emitter = controller.integrationTestStream(1L)
        assertNotNull(emitter)
    }

    @Test
    fun `integrationTestStream returns emitter that submits task when integration test RUNNING`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(1L, status = "RUNNING", integrationTestStatus = "RUNNING"),
        )
        val emitter = controller.integrationTestStream(1L)
        assertNotNull(emitter)
    }

    @Test
    fun `shutdownSseExecutor can be invoked via reflection and shuts down the executor`() {
        val method = CodeQaAnalysisApiController::class.java.getDeclaredMethod("shutdownSseExecutor")
        method.isAccessible = true
        method.invoke(controller)
    }

    @Test
    fun `IntegrationTestSseTask completes with error when polling throws`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn(null)
        whenever(service.getAnalysis(1L)).thenThrow(RuntimeException("boom"))

        val task = IntegrationTestSseTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()

        assertTrue(capturing.completed.get())
        assertTrue(sentChunks.isEmpty(), "the polling failure must not fall through to a status branch: $sentChunks")
    }

    @Test
    fun `SseStreamTask completes with error when polling throws`() {
        val sentChunks = mutableListOf<Any>()
        val capturing = CapturingEmitter(sentChunks)
        whenever(service.getAnalysis(1L)).thenThrow(RuntimeException("boom"))

        val task = SseStreamTask(1L, capturing, service, pollIntervalMs = 10L)
        task.run()

        assertTrue(capturing.completed.get())
        assertTrue(sentChunks.isEmpty(), "the polling failure must not fall through to a status branch: $sentChunks")
    }

    @Test
    fun `SseStreamTask ignores a failing completeWithError`() {
        whenever(service.getAnalysis(1L)).thenThrow(RuntimeException("boom"))

        val task = SseStreamTask(1L, FailingCompleteWithErrorEmitter(), service, pollIntervalMs = 10L)
        task.run()
    }

    @Test
    fun `IntegrationTestSseTask ignores a failing completeWithError`() {
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn(null)
        whenever(service.getAnalysis(1L)).thenThrow(RuntimeException("boom"))

        val task = IntegrationTestSseTask(1L, FailingCompleteWithErrorEmitter(), service, pollIntervalMs = 10L)
        task.run()
    }

    private fun eventPayloads(sentChunks: List<Any>): List<String> =
        sentChunks
            .filterIsInstance<SseEmitter.SseEventBuilder>()
            .flatMap { builder -> builder.build().map { it.data.toString() } }

    private class CapturingEmitter(
        val sentChunks: MutableList<Any>,
    ) : SseEmitter(0L) {
        val completed = AtomicBoolean(false)

        override fun send(data: Any) {
            sentChunks.add(data)
        }

        override fun send(builder: SseEmitter.SseEventBuilder) {
            sentChunks.add(builder)
        }

        override fun complete() {
            completed.set(true)
        }

        override fun completeWithError(ex: Throwable) {
            completed.set(true)
        }
    }

    private class ThrowingEmitter(
        val sentChunks: MutableList<Any>,
        private val error: Throwable,
    ) : SseEmitter(0L) {
        override fun send(data: Any): Unit = throw error

        override fun send(builder: SseEmitter.SseEventBuilder): Unit = throw error

        override fun complete() {
        }

        override fun completeWithError(ex: Throwable) {
        }
    }

    private class FailingCompleteWithErrorEmitter : SseEmitter(0L) {
        override fun send(data: Any) {
        }

        override fun send(builder: SseEmitter.SseEventBuilder) {
        }

        override fun complete() {
        }

        override fun completeWithError(ex: Throwable): Unit = throw IllegalStateException("emitter already completed")
    }
}
