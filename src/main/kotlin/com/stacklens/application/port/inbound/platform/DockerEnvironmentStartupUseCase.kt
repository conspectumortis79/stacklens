package com.stacklens.application.port.inbound.platform

interface DockerEnvironmentStartupUseCase {
    fun startDemoEnvironmentIfEnabledInBackground()
}
