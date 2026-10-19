package com.stacklens.application.port.inbound.dockertest

interface DockerTestEventSink {
    fun sendLine(line: String)

    fun sendEvent(
        name: String,
        data: String,
    )

    fun complete()

    fun completeWithError(error: Throwable)
}
