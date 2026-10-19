package com.stacklens.application.port.out.dockertest

interface DockerOsOperationsPort {
    fun waitUntilDockerAvailable(
        timeoutSec: Long,
        pollMs: Long,
    ): Boolean

    fun restartDockerOnMac(details: MutableList<String>): Boolean

    fun restartDockerOnLinux(details: MutableList<String>): Boolean
}
