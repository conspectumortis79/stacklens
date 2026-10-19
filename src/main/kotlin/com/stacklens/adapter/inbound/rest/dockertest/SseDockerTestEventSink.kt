package com.stacklens.adapter.inbound.rest.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerTestEventSink
import com.stacklens.application.port.inbound.dockertest.DockerTestStreamHandle
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SseDockerTestEventSink internal constructor(
    private val onSendFailure: (String?) -> Unit,
    private val keepaliveIntervalMs: Long,
    private val emitterFactory: (Long) -> SseEmitter,
) : DockerTestEventSink {
    val emitter: SseEmitter = emitterFactory(STREAM_TIMEOUT_MS)

    private val clientGone = AtomicBoolean(false)

    private val finished = AtomicBoolean(false)

    private val keepalive: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private var streamHandle: DockerTestStreamHandle? = null

    init {
        emitter.onCompletion { finish() }
        emitter.onTimeout { finish() }
        emitter.onError { finish() }
        keepalive.scheduleAtFixedRate({ sendEvent(EVENT_PING, DATA_KEEPALIVE) }, keepaliveIntervalMs, keepaliveIntervalMs, TimeUnit.MILLISECONDS)
    }

    fun attach(handle: DockerTestStreamHandle) {
        streamHandle = handle
        if (finished.get()) handle.release()
    }

    override fun sendLine(line: String) {
        send { emitter.send(SseEmitter.event().data(line).build()) }
    }

    override fun sendEvent(
        name: String,
        data: String,
    ) {
        send {
            emitter.send(
                SseEmitter
                    .event()
                    .name(name)
                    .data(data)
                    .build(),
            )
        }
    }

    override fun complete() {
        runCatching { emitter.complete() }
        finish()
    }

    override fun completeWithError(error: Throwable) {
        runCatching { emitter.completeWithError(error) }
        finish()
    }

    private fun send(block: () -> Unit) {
        if (clientGone.get()) return
        runCatching(block).onFailure { e ->
            clientGone.set(true)
            onSendFailure(e.message)
        }
    }

    private fun finish() {
        if (!finished.compareAndSet(false, true)) return
        clientGone.set(true)
        keepalive.shutdownNow()
        streamHandle?.release()
    }

    private companion object {
        const val STREAM_TIMEOUT_MS: Long = 15 * 60 * 1_000L
        const val EVENT_PING: String = "ping"
        const val DATA_KEEPALIVE: String = "keepalive"
    }
}
