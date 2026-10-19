package com.stacklens.application.port.inbound.dockertest

interface DockerTestStreamHandle {
    fun release()
}

interface DockerTestStreamRegistry {
    fun open(): DockerTestStreamHandle

    fun activeStreamCount(): Int
}
