package com.stacklens.domain.docker

data class DockerCommandOutcome(
    val exitCode: Int,
    val output: String,
)
