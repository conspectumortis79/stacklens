package com.stacklens.application.port.out.docker

interface DockerSocketDiscoveryPort {
    fun findAllDockerSockets(): List<String>
}
