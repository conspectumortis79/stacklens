package com.stacklens.application.port.out.docker

import com.stacklens.domain.docker.DockerStatus

interface DockerAvailabilityPort {
    fun checkDockerAvailability(): DockerStatus

    fun isDockerAvailable(): Boolean = checkDockerAvailability().available
}
