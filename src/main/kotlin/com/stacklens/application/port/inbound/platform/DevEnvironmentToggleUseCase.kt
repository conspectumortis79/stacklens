package com.stacklens.application.port.inbound.platform

interface DevEnvironmentToggleUseCase {
    fun reconcileDockerTestEnv(enabled: Boolean)
}
