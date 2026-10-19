package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.tooling.ToolingExecutionUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
class ToolingExecutionApiControllerTest {
    @Mock
    private lateinit var toolingService: ToolingExecutionUseCase

    private val controller by lazy {
        ToolingExecutionApiController(
            toolingService,
            com.stacklens.application.service
                .FakeI18n(),
        )
    }

    @Test
    fun `run returns output on success`() {
        whenever(toolingService.runTool(1L)).thenReturn("Test output")
        val resp = controller.run(1L)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = (resp as org.springframework.http.ResponseEntity<Map<String, Any>>).body!!
        assertEquals("Test output", body["output"])
    }

    @Test
    fun `run returns 400 when exception thrown`() {
        whenever(toolingService.runTool(1L)).thenThrow(RuntimeException("Tool error"))
        val resp = controller.run(1L)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `run returns 400 with Unbekannter Fehler when exception has no message`() {
        whenever(toolingService.runTool(1L)).thenThrow(RuntimeException())
        val resp = controller.run(1L)
        assertEquals(400, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = (resp as org.springframework.http.ResponseEntity<Map<String, Any>>).body!!
        assertEquals("common.be_unknown_error", body["error"])
    }

    @Test
    fun `runStream returns SseEmitter and streams lines`() {
        val latch = CountDownLatch(1)
        doAnswer { inv ->
            val callback = inv.getArgument<(String) -> Unit>(1)
            callback("output line 1")
            latch.countDown()
            ""
        }.whenever(toolingService).runToolStreaming(eq(1L), any())
        val emitter = controller.runStream(1L)
        assertNotNull(emitter)
        latch.await(3, TimeUnit.SECONDS)
    }

    @Test
    fun `runStream handles exception from toolingService`() {
        val latch = CountDownLatch(1)
        doAnswer {
            latch.countDown()
            throw RuntimeException("stream error")
        }.whenever(toolingService).runToolStreaming(eq(1L), any())
        val emitter = controller.runStream(1L)
        assertNotNull(emitter)
        latch.await(3, TimeUnit.SECONDS)
    }

    @Test
    fun `runStream error event uses exception message when present`() {
        val latch = CountDownLatch(1)
        doAnswer {
            latch.countDown()
            throw RuntimeException("stream error with message")
        }.whenever(toolingService).runToolStreaming(eq(1L), any())
        val emitter = controller.runStream(1L)
        assertNotNull(emitter)
        latch.await(3, TimeUnit.SECONDS)
    }

    @Test
    fun `runStream swallows SseEmitter exception when send fails`() {
        val latch = CountDownLatch(1)
        doAnswer {
            latch.countDown()
            throw RuntimeException()
        }.whenever(toolingService).runToolStreaming(eq(1L), any())
        val emitter = controller.runStream(1L)
        assertNotNull(emitter)
        latch.await(3, TimeUnit.SECONDS)
    }

    @Test
    fun `shutdownSseExecutor rejects further stream requests`() {
        controller.shutdownSseExecutor()

        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.RejectedExecutionException::class.java) {
            controller.runStream(1L)
        }
    }
}
