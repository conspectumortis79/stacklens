package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.ComposeFileLocationPort
import com.stacklens.application.port.out.docker.DockerCliPort
import com.stacklens.application.port.out.docker.DockerCliResult
import com.stacklens.application.port.out.docker.DockerCommandPort
import com.stacklens.application.port.out.docker.DockerExecutablePort
import com.stacklens.application.port.out.process.SleeperPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class DockerDemoEnvironmentAdapterFailurePathTest {
    private class SelectiveThrowingDockerCli(
        private val rejectedWord: String,
    ) : DockerCliPort {
        val invocations = mutableListOf<List<String>>()

        override fun run(
            arguments: List<String>,
            workingDirectory: String?,
            timeoutSec: Long,
            stdinFile: String?,
        ): DockerCliResult {
            invocations += arguments
            if (arguments.contains(rejectedWord)) throw IllegalStateException("cli exploded")
            return DockerCliResult(exitCode = 0, timedOut = false)
        }
    }

    private lateinit var composeFiles: ComposeFileLocationPort
    private lateinit var tempDir: File

    @BeforeEach
    fun setUp(
        @TempDir tmp: Path,
    ) {
        composeFiles = mock()
        tempDir = tmp.toFile()
    }

    private fun adapterWith(dockerCli: DockerCliPort) =
        DockerDemoEnvironmentAdapter(
            composeFiles = composeFiles,
            dockerCli = dockerCli,
            dockerCommands = mock<DockerCommandPort>(),
            dockerExecutable = mock<DockerExecutablePort>(),
            sleeper = SleeperPort { true },
        )

    private fun composeFile(): File = File(tempDir, "docker-compose.yml").apply { writeText("services: {}") }

    @Test
    fun `a missing compose file aborts the tear down before any docker call`() {
        val dockerCli = SelectiveThrowingDockerCli(rejectedWord = "down")
        whenever(composeFiles.resolveComposeFile()).thenReturn(null)

        adapterWith(dockerCli).bringDown()

        assertEquals(emptyList<List<String>>(), dockerCli.invocations)
    }

    @Test
    fun `a failing down step is logged and the container name fallback still runs`() {
        val dockerCli = SelectiveThrowingDockerCli(rejectedWord = "down")
        whenever(composeFiles.resolveComposeFile()).thenReturn(composeFile().absolutePath)
        whenever(composeFiles.findComposeContainerNames(org.mockito.kotlin.any())).thenReturn(listOf("stacklens-demo-mysql"))

        adapterWith(dockerCli).bringDown()

        assertEquals(3, dockerCli.invocations.size)
        assertEquals(listOf("stop", "stacklens-demo-mysql"), dockerCli.invocations.last())
        assertTrue(dockerCli.invocations.take(2).all { it.last() == "down" })
    }

    @Test
    fun `a docker cli that cannot exec at all aborts the mysql repair immediately`() {
        val initSql = File(tempDir, "01-schema.sql").apply { writeText("SELECT 1;") }
        val dockerCli = SelectiveThrowingDockerCli(rejectedWord = "exec")
        whenever(composeFiles.resolveComposeSiblingFile(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(initSql.absolutePath)

        adapterWith(dockerCli).repairMysqlInitIfNeeded(composeFile().absolutePath, probe = { false })

        assertEquals(1, dockerCli.invocations.size)
        assertTrue(dockerCli.invocations.first().contains("exec"))
    }

    @Test
    fun `a mysql container that never settles is never fed the init SQL`() {
        val initSql = File(tempDir, "01-schema.sql").apply { writeText("SELECT 1;") }
        val dockerCli = SelectiveThrowingDockerCli(rejectedWord = "grep -qx mysqld /proc/1/comm")
        whenever(composeFiles.resolveComposeSiblingFile(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(initSql.absolutePath)

        adapterWith(dockerCli).repairMysqlInitIfNeeded(composeFile().absolutePath, probe = { false })

        assertTrue(dockerCli.invocations.all { "-i" !in it })
    }
}
