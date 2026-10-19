package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerCliPort
import com.stacklens.application.port.out.docker.DockerCliResult
import com.stacklens.application.port.out.docker.DockerExecutablePort
import com.stacklens.shared.utf8Reader
import java.io.File
import java.util.concurrent.TimeUnit

class DockerCliAdapter(
    private val dockerExecutable: DockerExecutablePort,
    private val applyDockerEnv: (ProcessBuilder) -> Unit,
    private val processStarter: (ProcessBuilder) -> Process = { it.start() },
) : DockerCliPort {
    override fun run(
        arguments: List<String>,
        workingDirectory: String?,
        timeoutSec: Long,
        stdinFile: String?,
    ): DockerCliResult {
        val builder =
            ProcessBuilder(listOf(dockerExecutable.resolveDockerExecutable()) + arguments)
                .redirectErrorStream(true)
        if (workingDirectory != null) builder.directory(File(workingDirectory))
        if (stdinFile != null) builder.redirectInput(File(stdinFile))
        applyDockerEnv(builder)
        val process = processStarter(builder)
        process.inputStream.utf8Reader().readText()
        return if (process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            DockerCliResult(process.exitValue(), timedOut = false)
        } else {
            process.destroyForcibly()
            DockerCliResult(DockerCliResult.FAILED_EXIT_CODE, timedOut = true)
        }
    }
}
