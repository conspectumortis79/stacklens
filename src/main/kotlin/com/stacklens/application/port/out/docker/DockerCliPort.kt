package com.stacklens.application.port.out.docker

data class DockerCliResult(
    val exitCode: Int,
    val timedOut: Boolean,
) {
    val successful: Boolean get() = exitCode == SUCCESSFUL_EXIT_CODE

    companion object {
        const val SUCCESSFUL_EXIT_CODE = 0
        const val FAILED_EXIT_CODE = -1
    }
}

interface DockerCliPort {
    fun run(
        arguments: List<String>,
        workingDirectory: String?,
        timeoutSec: Long,
        stdinFile: String? = null,
    ): DockerCliResult
}
