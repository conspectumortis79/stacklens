package com.stacklens.application.port.out.docker

data class DockerTestComposePlan(
    val build: List<String>,
    val up: List<String>,
    val down: List<String>,
    val prune: List<String>,
    val workDir: String?,
)

interface DockerTestEnvironmentPort {
    fun composePlan(): DockerTestComposePlan?
}
