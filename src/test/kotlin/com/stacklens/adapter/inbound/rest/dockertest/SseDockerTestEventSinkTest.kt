package com.stacklens.adapter.inbound.rest.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerTestStreamHandle
import com.stacklens.testsupport.RecordingSseEmitter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SseDockerTestEventSinkTest {
    private val failures = mutableListOf<String?>()

    private fun sink(
        emitter: RecordingSseEmitter = RecordingSseEmitter(),
        keepaliveIntervalMs: Long = 60_000L,
    ): Pair<SseDockerTestEventSink, RecordingSseEmitter> {
        val created =
            SseDockerTestEventSink(
                onSendFailure = { message -> failures.add(message) },
                keepaliveIntervalMs = keepaliveIntervalMs,
                emitterFactory = { emitter },
            )
        return created to emitter
    }

    private class UnrulyEmitter : RecordingSseEmitter() {
        override fun complete() {
            completeCalls++
            throw IllegalStateException("already terminated")
        }

        override fun completeWithError(e: Throwable) {
            errorSent = e
            throw IllegalStateException("already terminated")
        }
    }

    private class CountingHandle : DockerTestStreamHandle {
        val releases = AtomicInteger(0)

        override fun release() {
            releases.incrementAndGet()
        }
    }

    @Test
    fun `constructing the sink registers the lifecycle callbacks on the emitter`() {
        val (created, emitter) = sink()

        assertEquals(1, emitter.completionCallbacks)
        assertEquals(1, emitter.timeoutCallbacks)
        assertNotNull(created.emitter)
    }

    @Test
    fun `sendLine writes a plain data payload`() {
        val (created, emitter) = sink()

        created.sendLine("building image")

        assertEquals(listOf("building image"), emitter.dataPayloads)
    }

    @Test
    fun `sendEvent writes a named control payload`() {
        val (created, emitter) = sink()

        created.sendEvent("ping", "keepalive")

        assertEquals(listOf("keepalive"), emitter.dataPayloads)
        assertTrue(emitter.controlPayloads.first().startsWith("event:ping"), "got ${emitter.controlPayloads}")
    }

    @Test
    fun `the scheduled keepalive pings the client while the stream stays open`() {
        val (created, emitter) = sink(keepaliveIntervalMs = 5L)

        awaitPayloadCount(emitter, 2)

        assertTrue(emitter.payloads().contains("keepalive"), "expected a keepalive ping, got ${emitter.payloads()}")
        created.complete()
    }

    @Test
    fun `complete finishes the emitter and releases the stream handle`() {
        val handle = CountingHandle()
        val (created, emitter) = sink()
        created.attach(handle)

        created.complete()

        assertEquals(1, emitter.completeCalls)
        assertEquals(1, handle.releases.get())
    }

    @Test
    fun `completeWithError reports the error and finishes once`() {
        val handle = CountingHandle()
        val (created, emitter) = sink()
        created.attach(handle)
        val failure = IllegalStateException("socket reset")

        created.completeWithError(failure)
        created.complete()
        created.completeWithError(RuntimeException("second"))

        assertEquals(1, emitter.completeCalls)
        assertEquals("second", emitter.errorSent?.message)
        assertEquals(1, handle.releases.get())
    }

    @Test
    fun `a failing send marks the client as gone and reports the cause`() {
        val emitter = RecordingSseEmitter()
        emitter.sendFailure = RuntimeException("broken pipe")
        val (created, _) = sink(emitter)

        created.sendLine("first")
        created.sendLine("second")
        created.sendEvent("ping", "keepalive")

        assertEquals(1, failures.size)
        assertEquals("broken pipe", failures.single())
        assertTrue(emitter.dataPayloads.isEmpty())
    }

    @Test
    fun `an emitter that rejects completion is still finished locally`() {
        val handle = CountingHandle()
        val (created, recording) = sink(UnrulyEmitter())
        created.attach(handle)

        created.complete()

        assertEquals(1, recording.completeCalls)
        assertEquals(1, handle.releases.get())
    }

    @Test
    fun `an emitter that rejects completionWithError is still finished locally`() {
        val handle = CountingHandle()
        val (created, recording) = sink(UnrulyEmitter())
        created.attach(handle)

        created.completeWithError(RuntimeException("reset"))

        assertNotNull(recording.errorSent)
        assertEquals(1, handle.releases.get())
    }

    @Test
    fun `the emitter completion callback releases the handle`() {
        val handle = CountingHandle()
        val (created, emitter) = sink()
        created.attach(handle)

        emitter.triggerCompletion()

        assertEquals(1, handle.releases.get())
        created.sendLine("after completion")
        assertTrue(emitter.dataPayloads.isEmpty(), "a finished sink keeps sending nothing")
        assertEquals(1, emitter.completionCallbacks)
    }

    @Test
    fun `the emitter timeout callback releases the handle`() {
        val handle = CountingHandle()
        val (created, emitter) = sink()
        created.attach(handle)

        emitter.triggerTimeout()

        assertEquals(1, handle.releases.get())
    }

    @Test
    fun `attaching after the stream finished releases the handle immediately`() {
        val (created, _) = sink()
        created.complete()
        val handle = CountingHandle()

        created.attach(handle)

        assertEquals(1, handle.releases.get())
    }

    @Test
    fun `a send before any handle is attached cannot release anything`() {
        val (created, emitter) = sink()

        created.sendLine("without handle")

        assertEquals(listOf("without handle"), emitter.dataPayloads)
        created.complete()
    }

    private fun awaitPayloadCount(
        emitter: RecordingSseEmitter,
        atLeast: Int,
    ) {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline && emitter.payloads().size < atLeast) {
            Thread.sleep(10)
        }
    }
}
