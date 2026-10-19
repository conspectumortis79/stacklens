package com.stacklens.adapter.inbound.rest.dockertest

import com.stacklens.adapter.inbound.rest.DockerTestApiController
import com.stacklens.application.port.inbound.dockertest.DockerTestEventSink
import com.stacklens.application.port.inbound.dockertest.DockerTestInfoView
import com.stacklens.application.port.inbound.dockertest.DockerTestJobClearedView
import com.stacklens.application.port.inbound.dockertest.DockerTestRestartView
import com.stacklens.application.port.inbound.dockertest.DockerTestStateView
import com.stacklens.application.port.inbound.dockertest.DockerTestStatusView
import com.stacklens.application.port.inbound.dockertest.DockerTestStreamHandle
import com.stacklens.application.port.inbound.dockertest.DockerTestStreamRegistry
import com.stacklens.application.port.inbound.dockertest.DockerTestUseCase
import com.stacklens.testsupport.RecordingSseEmitter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.function.Consumer

internal class CountingStreamRegistry : DockerTestStreamRegistry {
    private var active = 0

    override fun open(): DockerTestStreamHandle {
        active++
        return object : DockerTestStreamHandle {
            override fun release() {
                active--
            }
        }
    }

    override fun activeStreamCount(): Int = active
}

internal class BrokenSseEmitter : RecordingSseEmitter() {
    init {
        sendFailure = IllegalStateException("client gone")
    }
}

internal class CallbackCapturingEmitter : RecordingSseEmitter() {
    private var errorCallback: Consumer<Throwable>? = null

    override fun onError(callback: Consumer<Throwable>) {
        errorCallback = callback
    }

    fun triggerError() {
        errorCallback?.accept(IllegalStateException("response closed"))
    }
}

class SseDockerTestStreamTransportTest {
    private val registry = CountingStreamRegistry()

    @Test
    fun `createStream registers the stream until the sink is finished`() {
        val transport = SseDockerTestStreamTransport(registry)

        val sink = transport.createStream()

        assertEquals(1, registry.activeStreamCount())
        assertNotNull(sink.emitter)

        sink.complete()

        assertEquals(0, registry.activeStreamCount())
    }

    @Test
    fun `a sink whose emitter breaks on send keeps recording the job for later subscribers`() {
        val transport =
            object : SseDockerTestStreamTransport(registry) {
                override fun newEmitter(timeoutMillis: Long): SseEmitter = BrokenSseEmitter()
            }

        val sink = transport.createStream()
        sink.sendLine("first")
        sink.sendLine("second")
        sink.sendEvent("ping", "keepalive")

        assertEquals(1, registry.activeStreamCount())
        sink.complete()
        assertEquals(0, registry.activeStreamCount())
    }

    @Test
    fun `the emitter error callback finishes the sink`() {
        val registryLocal = CountingStreamRegistry()
        val capturing = CallbackCapturingEmitter()
        val transport =
            object : SseDockerTestStreamTransport(registryLocal) {
                override fun newEmitter(timeoutMillis: Long): SseEmitter = capturing
            }
        val sink = transport.createStream()

        capturing.triggerError()

        assertEquals(0, registryLocal.activeStreamCount())
        sink.sendLine("after the error")
        assertTrue(capturing.dataPayloads.isEmpty())
    }

    @Test
    fun `the scheduled keepalive reaches the client while the stream is open`() {
        val emitter = RecordingSseEmitter()
        val transport =
            object : SseDockerTestStreamTransport(CountingStreamRegistry()) {
                override val keepaliveIntervalMillis: Long get() = 5L

                override fun newEmitter(timeoutMillis: Long): SseEmitter = emitter
            }

        val sink = transport.createStream()
        awaitPayload(emitter)
        sink.complete()

        assertTrue(emitter.payloads().any { it.contains("keepalive") }, "got ${emitter.payloads()}")
    }

    private fun awaitPayload(emitter: RecordingSseEmitter) {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline && emitter.payloads().isEmpty()) {
            Thread.sleep(10)
        }
    }

    @Test
    fun `the keepalive budget is read from the overridable interval`() {
        val transport =
            object : SseDockerTestStreamTransport(registry) {
                override val keepaliveIntervalMillis: Long get() = 5L
            }

        val sink = transport.createStream()

        assertEquals(1, registry.activeStreamCount())
        sink.complete()
        assertEquals(0, registry.activeStreamCount())
    }
}

class DockerTestStreamEndpointControllerTest {
    private val useCase: DockerTestUseCase = mock()

    private val registry = CountingStreamRegistry()

    private val transport = SseDockerTestStreamTransport(registry)

    private val controller = DockerTestApiController(useCase, transport)

    @Test
    fun `info renders the view as string pairs`() {
        whenever(useCase.info()).thenReturn(DockerTestInfoView("/tmp/docker-compose.yml", found = true, dockerAvailable = true, dockerDiagnostic = ""))

        val body = requireNotNull(controller.info().body)

        assertEquals("true", body["found"])
        assertEquals("/tmp/docker-compose.yml", body["composeFile"])
        assertEquals("true", body["dockerAvailable"])
        assertEquals("", body["dockerDiagnostic"])
    }

    @Test
    fun `status renders numbers and booleans as strings`() {
        whenever(useCase.status()).thenReturn(DockerTestStatusView(phase = "running", jobActive = true, available = false, containers = 3, running = 2))

        val body = requireNotNull(controller.status().body)

        assertEquals("running", body["phase"])
        assertEquals("true", body["jobActive"])
        assertEquals("false", body["available"])
        assertEquals("3", body["containers"])
        assertEquals("2", body["running"])
    }

    @Test
    fun `clear reports ok with the use case message`() {
        whenever(useCase.clearCurrentJob()).thenReturn(DockerTestJobClearedView("current job cleared"))

        val body = requireNotNull(controller.clearCurrentJob().body)

        assertEquals("ok", body["status"])
        assertEquals("current job cleared", body["message"])
    }

    @Test
    fun `restart renders the view without changing the wire shape`() {
        whenever(useCase.restartDocker()).thenReturn(DockerTestRestartView(ok = true, dockerAvailable = false, dockerDiagnostic = "diag", message = "msg", details = "os=linux"))

        val body = requireNotNull(controller.restartDocker().body)

        assertEquals("true", body["ok"])
        assertEquals("false", body["dockerAvailable"])
        assertEquals("diag", body["dockerDiagnostic"])
        assertEquals("msg", body["message"])
        assertEquals("os=linux", body["details"])
    }

    @Test
    fun `state renders a running job snapshot`() {
        whenever(useCase.state()).thenReturn(DockerTestStateView(jobId = "j1", status = "running", output = listOf("a"), lineCount = 1))

        val body = requireNotNull(controller.state().body)

        assertEquals("j1", body["jobId"])
        assertEquals(listOf("a"), body["output"])
        assertEquals(1, body["lineCount"])
    }

    @Test
    fun `state renders an idle snapshot with a null job id`() {
        whenever(useCase.state()).thenReturn(DockerTestStateView(jobId = null, status = "idle", output = emptyList(), lineCount = 0))

        val body = requireNotNull(controller.state().body)

        assertEquals(null, body["jobId"])
        assertEquals(emptyList<String>(), body["output"])
    }

    @Test
    fun `start streams through a registered sink and answers with its emitter`() {
        whenever(useCase.startCompose(any())).thenAnswer { invocation ->
            invocation.getArgument<DockerTestEventSink>(0).sendLine("built")
        }

        val emitter = controller.startDockerCompose()

        assertNotNull(emitter)
        assertEquals(1, registry.activeStreamCount())
        verify(useCase).startCompose(any())
    }

    @Test
    fun `attach forwards the since offset to the use case`() {
        controller.attach(since = 7)

        verify(useCase).attachOutput(eq(7), any())
    }

    @Test
    fun `reset delegates to the use case`() {
        controller.resetDocker()

        verify(useCase).resetDocker(any())
    }
}
