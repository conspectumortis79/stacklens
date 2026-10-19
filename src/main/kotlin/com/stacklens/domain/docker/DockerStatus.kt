package com.stacklens.domain.docker

data class DockerStatus(
    val available: Boolean,
    val reason: String,
)
