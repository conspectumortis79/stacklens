package com.stacklens.adapter.inbound.rest.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerTestStreamRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Component
open class SseDockerTestStreamTransport(
    private val registry: DockerTestStreamRegistry,
) {
    internal open val keepaliveIntervalMillis: Long = KEEPALIVE_INTERVAL_MS

    internal open fun newEmitter(timeoutMillis: Long): SseEmitter = SseEmitter(timeoutMillis)

    private val log = LoggerFactory.getLogger(SseDockerTestStreamTransport::class.java)

    fun createStream(): SseDockerTestEventSink {
        val sink =
            SseDockerTestEventSink(
                keepaliveIntervalMs = keepaliveIntervalMillis,
                emitterFactory = ::newEmitter,
                onSendFailure = { message -> log.info("SSE send failed; the docker job will continue without this client: {}", message) },
            )
        sink.attach(registry.open())
        return sink
    }

    private companion object {
        const val KEEPALIVE_INTERVAL_MS: Long = 15_000L
    }
}
