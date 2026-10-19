package com.stacklens.application.port.out.docker

import com.stacklens.domain.docker.DockerStatus

fun interface DockerCliProber {
    fun probe(): DockerStatus
}
