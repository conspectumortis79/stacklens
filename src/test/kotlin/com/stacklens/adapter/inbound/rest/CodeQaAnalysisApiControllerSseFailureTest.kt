package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.code.CodeQaAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.CodeQaAnalysis
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class CodeQaAnalysisApiControllerSseFailureTest {
    private val service: CodeQaAnalysisUseCase = mock()
    private val i18n: I18nUseCase = mock()
    private val controller = CodeQaAnalysisApiController(service, i18n)

    private fun analysis(
        status: String,
        resultMarkdown: String? = "markdown",
        errorMessage: String? = null,
        integrationTestStatus: String? = null,
        integrationTestLog: String? = null,
    ) = CodeQaAnalysis(
        id = 1L,
        mrUrl = "https://gitlab.example.com/group/repo/-/merge_requests/1",
        status = status,
        resultMarkdown = resultMarkdown,
        errorMessage = errorMessage,
        integrationTestStatus = integrationTestStatus,
        integrationTestLog = integrationTestLog,
    )

    private fun failingEmitterConstruction() =
        Mockito.mockConstruction(SseEmitter::class.java) { mock, _ ->
            doThrow(IOException("client gone")).`when`(mock).send(any<SseEmitter.SseEventBuilder>())
            doThrow(IllegalStateException("already complete")).`when`(mock).complete()
        }

    @Test
    fun `stream of a completed analysis survives a failing emitter`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(status = "COMPLETED"))

        failingEmitterConstruction().use {
            assertNotNull(controller.stream(1L))
        }
    }

    @Test
    fun `stream of a completed analysis without markdown survives a failing emitter`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(status = "COMPLETED", resultMarkdown = null))

        failingEmitterConstruction().use {
            assertNotNull(controller.stream(1L))
        }
    }

    @Test
    fun `stream of a failed analysis survives a failing emitter`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(status = "FAILED", errorMessage = "boom"))

        failingEmitterConstruction().use {
            assertNotNull(controller.stream(1L))
        }
    }

    @Test
    fun `stream of a failed analysis without message survives a failing emitter`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(status = "FAILED", errorMessage = null))

        failingEmitterConstruction().use {
            assertNotNull(controller.stream(1L))
        }
    }

    @Test
    fun `stream of a cancelled analysis survives a failing emitter`() {
        whenever(service.getAnalysis(1L)).thenReturn(analysis(status = "CANCELLED"))

        failingEmitterConstruction().use {
            assertNotNull(controller.stream(1L))
        }
    }

    @Test
    fun `integration test stream of a completed run survives a failing emitter`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(status = "COMPLETED", integrationTestStatus = "COMPLETED", integrationTestLog = "docker compose up"),
        )

        failingEmitterConstruction().use {
            assertNotNull(controller.integrationTestStream(1L))
        }
    }

    @Test
    fun `integration test stream of a failed run without log survives a failing emitter`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(status = "COMPLETED", integrationTestStatus = "FAILED", integrationTestLog = null),
        )

        failingEmitterConstruction().use {
            assertNotNull(controller.integrationTestStream(1L))
        }
    }

    @Test
    fun `integration test stream of a cancelled run with blank log survives a failing emitter`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(status = "COMPLETED", integrationTestStatus = "CANCELLED", integrationTestLog = "   "),
        )

        failingEmitterConstruction().use {
            assertNotNull(controller.integrationTestStream(1L))
        }
    }

    @Test
    fun `integration test stream of a running analysis schedules the polling task`() {
        val running = analysis(status = "COMPLETED", integrationTestStatus = "RUNNING")
        whenever(service.getAnalysis(1L)).thenReturn(running, analysis(status = "COMPLETED", integrationTestStatus = "COMPLETED"))
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn(null)

        failingEmitterConstruction().use {
            assertNotNull(controller.integrationTestStream(1L))
        }
    }

    @Test
    fun `integration test stream of an analysis without a test status schedules the polling task`() {
        whenever(service.getAnalysis(1L))
            .thenReturn(
                analysis(status = "COMPLETED", integrationTestStatus = null),
                analysis(status = "COMPLETED", integrationTestStatus = "COMPLETED"),
            )
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn(null)

        failingEmitterConstruction().use {
            assertNotNull(controller.integrationTestStream(1L))
        }
    }

    @Test
    fun `stream task completes with a failing emitter after an interrupt`() {
        whenever(service.registerStreamingChangeListener(any(), any())).thenReturn({})
        doAnswer { throw InterruptedException("interrupted") }
            .whenever(service)
            .getStreamingOutput(1L)

        val task = SseStreamTask(1L, CompleteThrowingEmitter(), service, pollIntervalMs = 1L)
        task.run()
    }

    @Test
    fun `stream task completes with a failing emitter after a failure`() {
        whenever(service.registerStreamingChangeListener(any(), any())).thenReturn({})
        doThrow(RuntimeException("db down")).whenever(service).getStreamingOutput(1L)

        val task = SseStreamTask(1L, CompleteThrowingEmitter(), service, pollIntervalMs = 1L)
        task.run()
    }

    @Test
    fun `integration test task keeps the final chunk failure from breaking the run`() {
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn("TAIL", "TAIL-MORE")
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(status = "COMPLETED", integrationTestStatus = "COMPLETED"),
        )

        val task =
            IntegrationTestSseTask(
                1L,
                SelectiveThrowingEmitter(failOnCall = 2),
                service,
                pollIntervalMs = 1L,
            )
        task.run()
    }

    @Test
    fun `integration test task completes when the sleeper reports an interrupt`() {
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn("")
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(status = "RUNNING", integrationTestStatus = "RUNNING"),
        )

        val task =
            IntegrationTestSseTask(
                1L,
                CompleteThrowingEmitter(),
                service,
                pollIntervalMs = 1L,
                waiter = { false },
            )
        task.run()
    }

    @Test
    fun `integration test task swallows an interrupt from the repository`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(status = "RUNNING", integrationTestStatus = "RUNNING"),
        )
        doAnswer { throw InterruptedException("interrupted") }
            .whenever(service)
            .getIntegrationTestStreamingOutput(1L)

        val task =
            IntegrationTestSseTask(
                1L,
                CompleteThrowingEmitter(),
                service,
                pollIntervalMs = 1L,
            )
        task.run()
    }

    @Test
    fun `integration test task swallows a failure from the repository`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            analysis(status = "RUNNING", integrationTestStatus = "RUNNING"),
        )
        doThrow(RuntimeException("db down")).whenever(service).getIntegrationTestStreamingOutput(1L)

        val task =
            IntegrationTestSseTask(
                1L,
                CompleteThrowingEmitter(),
                service,
                pollIntervalMs = 1L,
            )
        task.run()
    }

    @Test
    fun `integration test task reports done for a cancelled and failed run`() {
        for (status in listOf("FAILED", "CANCELLED")) {
            whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn(null)
            whenever(service.getAnalysis(1L)).thenReturn(
                analysis(status = "COMPLETED", integrationTestStatus = status),
            )

            val task =
                IntegrationTestSseTask(
                    1L,
                    CompleteThrowingEmitter(throwOnSend = false),
                    service,
                    pollIntervalMs = 1L,
                )
            task.run()
        }
    }

    @Test
    fun `integration test task tolerates a vanished analysis`() {
        whenever(service.getIntegrationTestStreamingOutput(1L)).thenReturn(null)
        whenever(service.getAnalysis(1L)).thenReturn(null)

        val task =
            IntegrationTestSseTask(
                1L,
                CompleteThrowingEmitter(throwOnSend = false),
                service,
                pollIntervalMs = 1L,
            )
        task.run()
    }

    private class CompleteThrowingEmitter(
        private val throwOnSend: Boolean = true,
    ) : SseEmitter(0L) {
        override fun send(data: Any) {
            if (throwOnSend) throw IOException("client gone")
        }

        override fun send(builder: SseEmitter.SseEventBuilder) {
            if (throwOnSend) throw IOException("client gone")
        }

        override fun complete(): Unit = throw IllegalStateException("already complete")
    }

    private class SelectiveThrowingEmitter(
        private val failOnCall: Int,
    ) : SseEmitter(0L) {
        private val calls = AtomicInteger()

        override fun send(builder: SseEmitter.SseEventBuilder) {
            if (calls.incrementAndGet() == failOnCall) throw IOException("client gone")
        }

        override fun send(data: Any) {
            if (calls.incrementAndGet() == failOnCall) throw IOException("client gone")
        }
    }
}
