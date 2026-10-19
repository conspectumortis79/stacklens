package com.stacklens.testsupport

import com.stacklens.application.port.inbound.dockertest.DockerDemoDataLocationUseCase

class FakeDockerDemoDataLocation(
    private val location: String? = null,
    private val failure: Throwable? = null,
) : DockerDemoDataLocationUseCase {
    override fun composeFileLocation(): String? {
        failure?.let { throw it }
        return location
    }
}
