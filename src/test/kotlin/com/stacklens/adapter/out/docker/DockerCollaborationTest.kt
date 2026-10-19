package com.stacklens.adapter.out.docker

import com.stacklens.domain.docker.ComposeContainerEntry
import com.stacklens.domain.docker.ComposeContainerState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DockerCollaborationTest {
    private fun environment(osName: String): DockerProcessEnvironment =
        DockerProcessEnvironment(
            DockerOsPaths({ osName }),
            object : com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort {
                override fun findAllDockerSockets(): List<String> = emptyList()
            },
        )

    @Test
    fun `the container query state exposes the queried entries`() {
        val state = ComposeContainerState(listOf(ComposeContainerEntry("stacklens-web-1", "running", 0)))

        assertEquals(1, state.total)
        assertEquals(1, state.running)
    }

    @Test
    fun `the docker lookup paths follow the host operating system`() {
        val macBuilder = ProcessBuilder("true")
        environment("Mac OS X").apply(macBuilder)

        val linuxBuilder = ProcessBuilder("true")
        environment("Linux").apply(linuxBuilder)

        val macPath = macBuilder.environment()["PATH"]!!
        val linuxPath = linuxBuilder.environment()["PATH"]!!

        assertTrue(macPath.startsWith("/usr/local/bin:/opt/homebrew/bin:"), macPath)
        assertTrue(macPath.contains("/Applications/Docker.app/Contents/Resources/bin"), macPath)
        assertTrue(linuxPath.startsWith("/usr/bin:/usr/local/bin:/snap/bin:"), linuxPath)
        assertTrue(!linuxPath.contains("/Applications/Docker.app"), linuxPath)
    }

    @Test
    fun `the rex stage collects its feedback in the pipeline order`() {
        assertEquals(listOf("finn", "tess", "syd", "quinn", "sage", "vera"), com.stacklens.application.service.pipeline.AgentStageFlow.rexFeedbackStages)
        assertEquals(
            listOf("faye", "tess", "syd", "quinn", "sage"),
            listOf(
                com.stacklens.application.service.pipeline.loopback
                    .FayeFinnLoopback(),
                com.stacklens.application.service.pipeline.loopback
                    .TessFinnLoopback(),
                com.stacklens.application.service.pipeline.loopback
                    .SydFinnLoopback(),
                com.stacklens.application.service.pipeline.loopback
                    .QuinnLoopback(),
                com.stacklens.application.service.pipeline.loopback
                    .SageTessLoopback(),
            ).map { it.afterStage },
        )
    }
}
