package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.tooling.ToolingExecutionUseCase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class ToolingExecutionApiControllerStreamCallbackTest {
    private val toolingService: ToolingExecutionUseCase = mock()
    private val controller = ToolingExecutionApiController(toolingService, mock<I18nUseCase>())

    private fun callback(
        emitter: SseEmitter,
        field: String,
    ): Any? {
        val declared =
            generateSequence<Class<*>>(emitter.javaClass) { it.superclass }
                .mapNotNull { runCatching { it.getDeclaredField(field) }.getOrNull() }
                .first()
        declared.isAccessible = true
        return declared.get(emitter)
    }

    private fun fireEmitterCallbacks(emitter: SseEmitter) {
        (callback(emitter, "completionCallback") as? Runnable)?.run()
        (callback(emitter, "timeoutCallback") as? Runnable)?.run()
        @Suppress("UNCHECKED_CAST")
        val errorHandler = callback(emitter, "errorCallback") as? Consumer<Throwable>
        errorHandler?.accept(IllegalStateException("client gone"))
    }

    @Test
    fun `the stream endpoint forwards every tool line and then completes`() {
        val finished = CountDownLatch(1)
        whenever(toolingService.runToolStreaming(any(), any())).thenAnswer { invocation ->
            invocation.getArgument<(String) -> Unit>(1).invoke("first line")
            finished.countDown()
            "output"
        }

        val emitter = controller.runStream(5L)

        assertTrue(finished.await(5, TimeUnit.SECONDS))
        fireEmitterCallbacks(emitter)
    }

    @Test
    fun `a failing tool reports the error on the stream`() {
        val finished = CountDownLatch(1)
        whenever(toolingService.runToolStreaming(any(), any())).thenAnswer {
            finished.countDown()
            throw IllegalStateException()
        }

        val emitter = controller.runStream(6L)

        assertTrue(finished.await(5, TimeUnit.SECONDS))
        fireEmitterCallbacks(emitter)
    }

    @Test
    fun `the plain run endpoint answers with the tool output or the error`() {
        whenever(toolingService.runTool(7L)).thenReturn("ok")
        whenever(toolingService.runTool(8L)).thenThrow(RuntimeException("broken"))

        assertTrue(
            controller
                .run(7L)
                .body
                .toString()
                .contains("ok"),
        )
        assertTrue((controller.run(8L).body as Map<*, *>).containsKey("error"))
    }
}
