package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerCliResult
import com.stacklens.testsupport.DockerPortsDouble
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

class DockerCliAdapterTest {
    private val dockerComposePort: DockerPortsDouble = mock()

    private val capturedBuilders = mutableListOf<ProcessBuilder>()

    private val envBuilders = mutableListOf<ProcessBuilder>()

    private fun cli(process: Process): DockerCliAdapter {
        whenever(dockerComposePort.resolveDockerExecutable()).thenReturn(DOCKER_BIN)
        return DockerCliAdapter(
            dockerComposePort,
            applyDockerEnv = { envBuilders += it },
            processStarter = { builder ->
                capturedBuilders += builder
                process
            },
        )
    }

    @Test
    fun `prepends the resolved docker executable and applies the docker env`() {
        val result = cli(FakeProcess(exitCode = 0)).run(listOf("compose", "up"), WORK_DIR, 5L, STDIN_FILE)

        assertTrue(result.successful)
        val builder = capturedBuilders.single()
        assertEquals(listOf(DOCKER_BIN, "compose", "up"), builder.command())
        assertEquals(WORK_DIR, builder.directory()?.absolutePath)
        assertEquals(STDIN_FILE, builder.redirectInput().file()?.absolutePath)
        assertEquals(listOf(builder), envBuilders)
    }

    @Test
    fun `omits working directory and stdin when the caller provides none`() {
        cli(FakeProcess(exitCode = 0)).run(listOf("version"), workingDirectory = null, timeoutSec = 5L)

        val builder = capturedBuilders.single()
        assertNull(builder.directory())
        assertEquals(ProcessBuilder.Redirect.Type.PIPE, builder.redirectInput().type())
    }

    @Test
    fun `reports a non-zero exit code as failure`() {
        val result = cli(FakeProcess(exitCode = 3)).run(listOf("ps"), WORK_DIR, 5L)

        assertEquals(3, result.exitCode)
        assertFalse(result.timedOut)
        assertFalse(result.successful)
    }

    @Test
    fun `kills a process that exceeds the timeout`() {
        val process = FakeProcess(exitCode = 0, waitReturnsFalse = true)

        val result = cli(process).run(listOf("compose", "up"), WORK_DIR, timeoutSec = 0L)

        assertEquals(DockerCliResult.FAILED_EXIT_CODE, result.exitCode)
        assertTrue(result.timedOut)
        assertTrue(process.destroyed)
    }

    private companion object {
        const val DOCKER_BIN = "/usr/bin/docker"
        const val WORK_DIR: String = "/tmp/stacklens-workdir"
        const val STDIN_FILE: String = "/tmp/stacklens-init.sql"
    }
}

private class FakeProcess(
    private val exitCode: Int,
    private val waitReturnsFalse: Boolean = false,
) : Process() {
    var destroyed: Boolean = false
        private set

    override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

    override fun getInputStream() = ByteArrayInputStream(ByteArray(0))

    override fun getErrorStream() = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = exitCode

    override fun waitFor(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = !waitReturnsFalse

    override fun exitValue(): Int = exitCode

    override fun destroy() {
        destroyed = true
    }

    override fun destroyForcibly(): Process {
        destroyed = true
        return this
    }

    override fun isAlive(): Boolean = waitReturnsFalse

    override fun pid(): Long = 0L

    override fun supportsNormalTermination(): Boolean = true

    override fun onExit(): CompletableFuture<Process> = CompletableFuture.completedFuture(this)

    override fun toHandle(): ProcessHandle = throw UnsupportedOperationException()

    override fun info(): ProcessHandle.Info = throw UnsupportedOperationException()

    override fun children(): Stream<ProcessHandle> = throw UnsupportedOperationException()
}
