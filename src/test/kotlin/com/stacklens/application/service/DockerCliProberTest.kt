package com.stacklens.application.service
import com.stacklens.adapter.out.docker.DefaultDockerCliProber
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

class DockerCliProberTest {
    @Test
    fun `DefaultDockerCliProber returns DockerStatus(true) when process exits 0 with version output`() {
        val prober =
            DefaultDockerCliProber(
                resolveDockerExecutable = { "/usr/local/bin/docker" },
                processFactory = { _, _ -> FakeProcess(exitCode = 0, output = "24.0.5\n") },
            )
        val result = prober.probe()
        assertTrue(result.available, "expected available=true, got: ${result.reason}")
        assertTrue(result.reason.contains("24.0.5"))
    }

    @Test
    fun `DefaultDockerCliProber returns DockerStatus(false) when waitFor returns false (timeout)`() {
        val prober =
            DefaultDockerCliProber(
                resolveDockerExecutable = { "/usr/local/bin/docker" },
                processFactory = { _, _ -> FakeProcess(exitCode = 1, output = "", waitReturnsFalse = true) },
            )
        val result = prober.probe()
        assertFalse(result.available)
        assertTrue(result.reason.contains("timed out"))
    }

    @Test
    fun `DefaultDockerCliProber returns DockerStatus(false) when exitValue is non-zero`() {
        val prober =
            DefaultDockerCliProber(
                resolveDockerExecutable = { "/usr/local/bin/docker" },
                processFactory = { _, _ -> FakeProcess(exitCode = 1, output = "something") },
            )
        val result = prober.probe()
        assertFalse(result.available)
        assertTrue(result.reason.contains("exit="))
    }

    @Test
    fun `DefaultDockerCliProber returns DockerStatus(false) when stdout is blank`() {
        val prober =
            DefaultDockerCliProber(
                resolveDockerExecutable = { "/usr/local/bin/docker" },
                processFactory = { _, _ -> FakeProcess(exitCode = 0, output = "  \n") },
            )
        val result = prober.probe()
        assertFalse(result.available)
        assertTrue(result.reason.contains("exit="))
    }

    @Test
    fun `DefaultDockerCliProber catches Exception and returns DockerStatus(false)`() {
        val prober =
            DefaultDockerCliProber(
                resolveDockerExecutable = { "/usr/local/bin/docker" },
                processFactory = { _, _ -> error("spawn failed") },
            )
        val result = prober.probe()
        assertFalse(result.available)
        assertTrue(result.reason.contains("docker CLI probe failed"))
        assertTrue(result.reason.contains("spawn failed"))
    }

    @Test
    fun `DefaultDockerCliProber handles binary in root directory with null parent`() {
        val prober =
            DefaultDockerCliProber(
                resolveDockerExecutable = { "/" },
                processFactory = { _, _ -> FakeProcess(exitCode = 0, output = "ok") },
            )
        val result = prober.probe()
        assertTrue(result.available)
    }
}

private class FakeProcess(
    private val exitCode: Int,
    private val output: String,
    private val waitReturnsFalse: Boolean = false,
) : Process() {
    override fun getOutputStream(): OutputStream = java.io.ByteArrayOutputStream()

    override fun getInputStream() = ByteArrayInputStream(output.toByteArray())

    override fun getErrorStream() = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = exitCode

    override fun waitFor(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = !waitReturnsFalse

    override fun exitValue(): Int = exitCode

    override fun destroy() = Unit

    override fun destroyForcibly(): Process = this

    override fun isAlive(): Boolean = false

    override fun pid(): Long = 0L

    override fun supportsNormalTermination(): Boolean = true

    override fun onExit(): CompletableFuture<Process> = CompletableFuture.completedFuture(this)

    override fun toHandle(): ProcessHandle = throw UnsupportedOperationException()

    override fun info(): ProcessHandle.Info = throw UnsupportedOperationException()

    override fun children(): Stream<ProcessHandle> = throw UnsupportedOperationException()
}
