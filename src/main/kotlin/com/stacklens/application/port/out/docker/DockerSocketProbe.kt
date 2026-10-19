package com.stacklens.application.port.out.docker

import com.stacklens.domain.docker.DockerStatus

fun interface DockerSocketProbe {
    fun ping(socketPath: String): DockerStatus
}
