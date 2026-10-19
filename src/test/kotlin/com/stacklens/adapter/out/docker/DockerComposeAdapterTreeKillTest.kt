package com.stacklens.adapter.out.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class DockerComposeAdapterTreeKillTest {
    private val adapter =
        DockerCommandAdapter(
            DockerProcessEnvironment(
                DockerOsPaths(),
                object : com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort {
                    override fun findAllDockerSockets(): List<String> = emptyList()
                },
            ),
        )

    private fun script(
        dir: File,
        name: String,
        body: String,
    ): File =
        File(dir, name).apply {
            writeText(body)
            setExecutable(true, false)
        }

    @Test
    fun `the tree kill runner reports the exit code and the trimmed output`(
        @TempDir dir: Path,
    ) {
        val docker = script(dir.toFile(), "docker", "#!/bin/sh\necho '   composed   '\nexit 0\n")

        val outcome = adapter.runDockerCommandWithTreeKill(listOf(docker.absolutePath), dir.toFile().absolutePath, 30L)

        assertEquals(0, outcome.exitCode)
        assertEquals("composed", outcome.output)
    }

    @Test
    fun `a process that outlives its budget is killed together with its own child`(
        @TempDir dir: Path,
    ) {
        val base = dir.toFile()
        val docker = script(base, "docker-hang", "#!/bin/sh\nsleep 60 &\necho $! > descendant.pid\nwait\n")

        val outcome = adapter.runDockerCommandWithTreeKill(listOf(docker.absolutePath), base.absolutePath, 5L)

        assertEquals(-1, outcome.exitCode)
        assertEquals("timeout after 5s", outcome.output)
        val descendant = waitUntilPidFileExists(File(base, "descendant.pid"))

        assertTrue(processGone(descendant), "descendant process $descendant survived the tree kill")
    }

    @Test
    fun `a process that cannot be launched is reported as an outcome instead of an exception`() {
        val outcome =
            object : DockerCommandAdapter(
                DockerProcessEnvironment(
                    DockerOsPaths(),
                    object : com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort {
                        override fun findAllDockerSockets(): List<String> = emptyList()
                    },
                ),
            ) {
                override fun startComposeProcess(
                    command: List<String>,
                    workDir: String?,
                ): Process = throw IllegalStateException("launch denied")
            }.runDockerCommandWithTreeKill(listOf("docker"), null, 5L)

        assertEquals(-1, outcome.exitCode)
        assertTrue(outcome.output.startsWith("IllegalStateException:"), outcome.output)
    }

    @Test
    fun `a command without a work dir runs in the inherited directory`(
        @TempDir dir: Path,
    ) {
        val docker = script(dir.toFile(), "docker-pwd", "#!/bin/sh\npwd\n")

        val outcome = adapter.runDockerCommand(listOf(docker.absolutePath), null, 30L)

        assertEquals(0, outcome.exitCode)
        assertTrue(outcome.output.isNotBlank())
    }

    @Test
    fun `a stream that only breaks while closing still hands back the exit code`() {
        val closingFails =
            object : FilterInputStream(ByteArrayInputStream("first line\n".toByteArray())) {
                override fun close(): Unit = throw IOException("close refused")
            }
        val process = mock<Process>()
        whenever(process.inputStream).thenReturn(closingFails)
        whenever(process.waitFor()).thenReturn(4)

        val exit =
            object : DockerCommandAdapter(
                DockerProcessEnvironment(
                    DockerOsPaths(),
                    object : com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort {
                        override fun findAllDockerSockets(): List<String> = emptyList()
                    },
                ),
            ) {
                override fun startComposeProcess(
                    command: List<String>,
                    workDir: String?,
                ): Process = process
            }.runComposeStreaming(listOf("docker"), null) { }

        assertEquals(4, exit)
    }

    @Test
    fun `the tree kill helper destroys the whole process tree`(
        @TempDir dir: Path,
    ) {
        val base = dir.toFile()
        val docker = script(base, "docker-tree", "#!/bin/sh\nsleep 60 &\necho $! > kid.pid\nwait\n")
        val launcher = ProcessBuilder(docker.absolutePath).directory(base).redirectErrorStream(true).start()
        try {
            val kid = waitUntilPidFileExists(File(base, "kid.pid"))

            DockerProcessEnvironment(
                DockerOsPaths(),
                object : com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort {
                    override fun findAllDockerSockets(): List<String> = emptyList()
                },
            ).killTree(launcher)

            launcher.waitFor(5, TimeUnit.SECONDS)
            assertTrue(processGone(kid), "child process $kid survived the tree kill helper")
        } finally {
            if (launcher.isAlive) launcher.destroyForcibly()
        }
    }

    @Test
    fun `a consumer that rejects a line still reports the exit code of the process`() {
        val process = mock<Process>()
        whenever(process.inputStream).thenReturn(java.io.ByteArrayInputStream("first\nsecond\n".toByteArray()))
        whenever(process.waitFor()).thenReturn(7)

        val exit =
            object : DockerCommandAdapter(
                DockerProcessEnvironment(
                    DockerOsPaths(),
                    object : com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort {
                        override fun findAllDockerSockets(): List<String> = emptyList()
                    },
                ),
            ) {
                override fun startComposeProcess(
                    command: List<String>,
                    workDir: String?,
                ): Process = process
            }.runComposeStreaming(listOf("docker"), null) { line ->
                if (line == "second") throw IllegalArgumentException("consumer refused $line")
            }

        assertEquals(7, exit)
    }

    @Test
    fun `a launch failure without a message still names the exception`() {
        val outcome =
            object : DockerCommandAdapter(
                DockerProcessEnvironment(
                    DockerOsPaths(),
                    object : com.stacklens.application.port.out.docker.DockerSocketDiscoveryPort {
                        override fun findAllDockerSockets(): List<String> = emptyList()
                    },
                ),
            ) {
                override fun startComposeProcess(
                    command: List<String>,
                    workDir: String?,
                ): Process = throw IllegalStateException()
            }.runDockerCommandWithTreeKill(listOf("docker"), null, 5L)

        assertEquals("IllegalStateException: error", outcome.output)
    }

    private fun processGone(pid: Long): Boolean {
        repeat(40) {
            val handle = ProcessHandle.of(pid)
            if (handle.map { !it.isAlive }.orElse(true)) return true
            Thread.sleep(50)
        }
        return false
    }

    private fun waitUntilPidFileExists(pidFile: File): Long {
        repeat(60) {
            if (pidFile.isFile && pidFile.readText().trim().isNotEmpty()) {
                return pidFile.readText().trim().toLong()
            }
            Thread.sleep(50)
        }
        throw IllegalStateException("the helper script never wrote $pidFile")
    }
}
