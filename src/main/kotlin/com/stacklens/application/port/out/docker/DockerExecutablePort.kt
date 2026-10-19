package com.stacklens.application.port.out.docker

interface DockerExecutablePort {
    fun resolveDockerExecutable(): String
}
