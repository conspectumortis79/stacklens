package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProcessExecutorRunCoverageTest {
    @Test
    fun `a successful run captures stdout and stderr separately`() {
        val result = ProcessExecutor.run(listOf("/bin/sh", "-c", "echo out-line; echo err-line 1>&2"), timeoutSec = 20)

        assertEquals(0, result.exitCode)
        assertTrue(result.success)
        assertTrue(result.stdout.contains("out-line"))
        assertTrue(result.stderr.contains("err-line"))
    }

    @Test
    fun `a run without path augmentation still starts the process`() {
        val result = ProcessExecutor.run(listOf("/bin/echo", "plain"), timeoutSec = 20, augmentPath = false)

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("plain"))
    }

    @Test
    fun `a run inside a working directory starts there`(
        @TempDir dir: File,
    ) {
        val result = ProcessExecutor.run(listOf("/bin/pwd"), timeoutSec = 20, workDir = dir)

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.trim().isNotEmpty())
    }

    @Test
    fun `a hanging command is killed and reported as a timeout`() {
        val started = System.currentTimeMillis()
        val result = ProcessExecutor.run(listOf("/bin/sleep", "30"), timeoutSec = 1)

        assertEquals(124, result.exitCode)
        assertTrue(result.stderr.contains("Timeout nach 1s"))
        assertTrue(System.currentTimeMillis() - started < 15_000)
    }

    @Test
    fun `a command that cannot be started is reported with the io message`() {
        val result = ProcessExecutor.run(listOf("/definitely/not/an/executable"), timeoutSec = 5)

        assertEquals(-1, result.exitCode)
        assertEquals(false, result.success)
        assertTrue(result.stderr.isNotBlank())
    }

    @Test
    fun `the path builder appends the system defaults to an existing path`() {
        val augmented = ProcessExecutor.buildPath("/custom/bin")

        assertTrue(augmented.startsWith("/custom/bin:"))
        assertTrue(augmented.endsWith("/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"))
    }

    @Test
    fun `the path builder falls back to the system defaults when no path exists`() {
        assertEquals("/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin", ProcessExecutor.buildPath(null))
    }

    @Test
    fun `draining a stream that fails keeps the collected output and never throws`() {
        val sink = StringBuilder()

        val thread = ProcessExecutor.drain(ThrowingStream(), sink, "probe-drain")
        thread.join(5_000)

        assertEquals("", sink.toString())
        assertTrue(!thread.isAlive)
    }

    private class ThrowingStream : java.io.InputStream() {
        override fun read(): Int = throw java.io.IOException("stream dead")

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = throw java.io.IOException("stream dead")
    }
}
