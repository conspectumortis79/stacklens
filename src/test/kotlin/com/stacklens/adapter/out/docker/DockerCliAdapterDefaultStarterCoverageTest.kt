package com.stacklens.adapter.out.docker

import com.stacklens.testsupport.DockerPortsDouble
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class DockerCliAdapterDefaultStarterCoverageTest {
    private val dockerComposePort: DockerPortsDouble = mock()

    @Test
    fun `the default starter really launches the docker process`(
        @TempDir dir: Path,
    ) {
        val executable = File(dir.toFile(), "fake-docker")
        executable.writeText("#!/bin/sh\nexit 0\n")
        executable.setExecutable(true)
        whenever(dockerComposePort.resolveDockerExecutable()).thenReturn(executable.absolutePath)

        val result = DockerCliAdapter(dockerComposePort, {}).run(listOf("--version"), dir.toFile().absolutePath, 20, null)

        assertEquals(0, result.exitCode)
        assertEquals(false, result.timedOut)
    }
}
