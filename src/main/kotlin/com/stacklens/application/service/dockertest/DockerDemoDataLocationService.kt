package com.stacklens.application.service.dockertest

import com.stacklens.application.port.inbound.dockertest.DockerDemoDataLocationUseCase
import com.stacklens.application.port.out.docker.ComposeFileLocationPort
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class DockerDemoDataLocationService(
    private val composeFiles: ComposeFileLocationPort,
) : DockerDemoDataLocationUseCase {
    override fun composeFileLocation(): String? = composeFiles.resolveComposeFile()
}
