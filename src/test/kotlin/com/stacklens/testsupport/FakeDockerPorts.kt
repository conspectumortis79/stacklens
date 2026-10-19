package com.stacklens.testsupport

import com.stacklens.application.port.out.docker.ComposeContainerStatePort
import com.stacklens.application.port.out.docker.ComposeFileLocationPort
import com.stacklens.application.port.out.docker.DockerAvailabilityPort
import com.stacklens.application.port.out.docker.DockerExecutablePort
import com.stacklens.domain.docker.ComposeContainerState
import com.stacklens.domain.docker.DockerStatus
import java.io.File

class FakeDockerPorts(
    var composeFile: String? = null,
    var dockerExecutable: String = "docker",
    var available: Boolean = true,
    var status: DockerStatus = DockerStatus(true, "ok"),
    var containerState: ComposeContainerState = ComposeContainerState.EMPTY,
    var composeFileFailure: Throwable? = null,
    var availabilityFailure: Throwable? = null,
    var statusFailure: Throwable? = null,
    var containerFailure: Throwable? = null,
) : DockerAvailabilityPort,
    ComposeFileLocationPort,
    ComposeContainerStatePort,
    DockerExecutablePort {
    val containersQueried = mutableListOf<String>()

    override fun checkDockerAvailability(): DockerStatus {
        statusFailure?.let { throw it }
        return status
    }

    override fun isDockerAvailable(): Boolean {
        availabilityFailure?.let { throw it }
        return available
    }

    override fun resolveComposeFile(): String? {
        composeFileFailure?.let { throw it }
        return composeFile
    }

    override fun containingDirectory(path: String): String? = File(path).parentFile?.absolutePath

    override fun resolveComposeSiblingFile(
        composeFile: String,
        relativePath: String,
    ): String? = null

    override fun findComposeContainerNames(composeFile: String): List<String> = emptyList()

    override fun queryComposeContainers(
        composeFile: String,
        timeoutSeconds: Long,
    ): ComposeContainerState {
        containersQueried += composeFile
        containerFailure?.let { throw it }
        return containerState
    }

    override fun resolveDockerExecutable(): String = dockerExecutable
}
