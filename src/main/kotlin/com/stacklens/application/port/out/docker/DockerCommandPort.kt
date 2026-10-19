package com.stacklens.application.port.out.docker

import com.stacklens.domain.docker.DockerCommandOutcome

interface DockerCommandPort {
    fun runDockerCommand(
        command: List<String>,
        workDir: String?,
        timeoutSeconds: Long,
    ): DockerCommandOutcome

    fun runDockerCommandWithTreeKill(
        command: List<String>,
        workDir: String?,
        timeoutSeconds: Long,
    ): DockerCommandOutcome

    fun runComposeStreaming(
        command: List<String>,
        workDir: String?,
        onLine: (String) -> Unit,
    ): Int
}
