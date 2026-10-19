package com.stacklens.adapter.out.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DockerComposeAdapterProcessSeamTest {
    @Test
    fun `the test seam starts the compose process in the given directory`(
        @TempDir dir: Path,
    ) {
        val script =
            File(dir.toFile(), "docker-pwd").apply {
                writeText("#!/bin/sh\npwd\n")
                setExecutable(true)
            }

        val process =
            DockerCommandAdapter(
                DockerProcessEnvironment(
                    DockerOsPaths(),
                    object : com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort {
                        override fun findAllDockerSockets(): List<String> = emptyList()
                    },
                ),
            ).startComposeProcess(listOf(script.absolutePath), dir.toFile().absolutePath)
        val output =
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()

        process.waitFor()
        assertEquals(dir.toFile().canonicalPath, File(output).canonicalPath)
    }
}

class ComposeFileLookupFailureTest {
    @Test
    fun `a failing lookup is reported as no compose file`() {
        val adapter =
            object : ComposeFileLocationAdapter(".", DockerOsPaths()) {
                override fun resolveComposeFileInternal(): java.io.File? = throw IllegalStateException("volume unreadable")
            }

        assertEquals(null, adapter.resolveComposeFile())
    }

    @Test
    fun `a host without any compose file answers no location`() {
        val adapter =
            object : ComposeFileLocationAdapter("/definitely/not/a/stacklens/home", DockerOsPaths()) {
                override fun resolveComposeFileInternal(): java.io.File? = null
            }

        assertEquals(null, adapter.resolveComposeFile())
    }

    @Test
    fun `a located compose file is reported by its absolute path`(
        @TempDir dir: Path,
    ) {
        val compose = File(dir.toFile(), "docker-compose.yml").apply { writeText("services: {}") }
        val adapter =
            object : ComposeFileLocationAdapter(".", DockerOsPaths()) {
                override fun resolveComposeFileInternal(): File = compose
            }

        assertEquals(compose.absolutePath, adapter.resolveComposeFile())
    }
}
