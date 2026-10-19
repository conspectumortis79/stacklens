package com.stacklens.adapter.out.docker

import com.stacklens.adapter.out.docker.testdockerComposeCommand
import com.stacklens.application.port.out.docker.ComposeContainerStatePort
import com.stacklens.application.port.out.docker.DockerExecutablePort
import com.stacklens.domain.docker.ComposeContainerState
import com.stacklens.shared.utf8Reader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit

@Component
open class ComposeContainerStateAdapter(
    private val executable: DockerExecutablePort,
    private val environment: DockerProcessEnvironment,
) : ComposeContainerStatePort {
    private val log = LoggerFactory.getLogger(ComposeContainerStateAdapter::class.java)

    override fun queryComposeContainers(
        composeFile: String,
        timeoutSeconds: Long,
    ): ComposeContainerState {
        val file = File(composeFile)
        val pb =
            ProcessBuilder(composePsCommand(file.absolutePath))
                .directory(file.parentFile)
                .redirectErrorStream(true)
        environment.apply(pb)
        return readContainerState(pb, timeoutSeconds)
    }

    internal open fun composePsCommand(composeFile: String): List<String> =
        testdockerComposeCommand(
            executable.resolveDockerExecutable(),
            composeFile,
            "ps",
            "-a",
            "--format",
            CONTAINER_PS_FORMAT,
        )

    internal open fun startProcess(pb: ProcessBuilder): Process = environment.start(pb)

    internal fun readContainerState(
        pb: ProcessBuilder,
        timeoutSeconds: Long,
    ): ComposeContainerState =
        try {
            val proc = startProcess(pb)
            val output = proc.inputStream.utf8Reader().readText()
            if (!proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
            }
            parseComposePs(output)
        } catch (e: Exception) {
            log.warn("[DockerCompose] container query failed: {}", e.message)
            ComposeContainerState.EMPTY
        }

    internal fun parseComposePs(output: String): ComposeContainerState = ComposePsParser.parse(output)

    private companion object {
        const val CONTAINER_PS_FORMAT: String = "{{.Name}}\t{{.State}}\t{{.ExitCode}}"
    }
}
