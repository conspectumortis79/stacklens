package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerCommandPort
import com.stacklens.domain.docker.DockerCommandOutcome
import com.stacklens.shared.utf8Reader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit

@Component
open class DockerCommandAdapter(
    private val environment: DockerProcessEnvironment,
) : DockerCommandPort {
    private val log = LoggerFactory.getLogger(DockerCommandAdapter::class.java)

    override fun runDockerCommand(
        command: List<String>,
        workDir: String?,
        timeoutSeconds: Long,
    ): DockerCommandOutcome {
        val pb =
            ProcessBuilder(command)
                .redirectErrorStream(true)
        if (workDir != null) {
            pb.directory(File(workDir))
        }
        environment.apply(pb)
        val process = startProcess(pb)
        val output = process.inputStream.utf8Reader().readText()
        val exit =
            if (process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.exitValue()
            } else {
                process.destroyForcibly()
                -1
            }
        return DockerCommandOutcome(exit, output)
    }

    override fun runComposeStreaming(
        command: List<String>,
        workDir: String?,
        onLine: (String) -> Unit,
    ): Int {
        val process = startComposeProcess(command, workDir)
        try {
            process.inputStream
                .utf8Reader()
                .use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        onLine(line)
                    }
                }
        } catch (e: Exception) {
            log.warn("Reading docker output failed: {}", e.message)
        }
        return process.waitFor()
    }

    override fun runDockerCommandWithTreeKill(
        command: List<String>,
        workDir: String?,
        timeoutSeconds: Long,
    ): DockerCommandOutcome =
        try {
            val process = startComposeProcess(command, workDir)
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                environment.killTree(process)
                return DockerCommandOutcome(-1, "timeout after ${timeoutSeconds}s")
            }
            val output =
                process.inputStream
                    .utf8Reader()
                    .readText()
                    .trim()
                    .take(COMMAND_OUTPUT_LIMIT)
            DockerCommandOutcome(process.exitValue(), output)
        } catch (e: Exception) {
            DockerCommandOutcome(-1, "${e.javaClass.simpleName}: ${e.message ?: "error"}")
        }

    internal open fun startComposeProcess(
        command: List<String>,
        workDir: String?,
    ): Process {
        val pb =
            ProcessBuilder(command)
                .redirectErrorStream(true)
        if (workDir != null) {
            pb.directory(File(workDir))
        }
        environment.apply(pb)
        return environment.start(pb)
    }

    internal open fun startProcess(pb: ProcessBuilder): Process = environment.start(pb)

    private companion object {
        const val COMMAND_OUTPUT_LIMIT: Int = 500
    }
}
