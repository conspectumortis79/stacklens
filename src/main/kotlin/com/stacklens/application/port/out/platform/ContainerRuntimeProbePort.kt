package com.stacklens.application.port.out.platform

interface ContainerRuntimeProbePort {
    fun detectActiveRuntime(): ContainerRuntime
}

enum class ContainerRuntime {
    DOCKER,
    PODMAN,
    SYSBOX,
    NONE,
}
