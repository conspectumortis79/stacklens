package com.stacklens.application.port.inbound.dockertest

interface DockerDemoDataLocationUseCase {
    fun composeFileLocation(): String?
}
