package com.stacklens.application.port.out.async

interface DockerTestDispatchPort {
    fun submitStream(task: () -> Unit)
}
