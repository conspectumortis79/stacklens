package com.stacklens.adapter.out.docker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class DefaultDockerCliProberCoverageTest {
    private fun process(
        output: String,
        exitCode: Int,
        finished: Boolean = true,
    ): Process {
        val proc = mock<Process>()
        whenever(proc.waitFor(any(), any())).thenReturn(finished)
        whenever(proc.inputStream).thenReturn(ByteArrayInputStream(output.toByteArray()))
        whenever(proc.exitValue()).thenReturn(exitCode)
        return proc
    }

    private fun prober(proc: Process): DefaultDockerCliProber = DefaultDockerCliProber({ "/usr/bin/docker" }, { _, _ -> proc })

    @Test
    fun `a successful docker version call reports the server version`() {
        val proc = process("27.1.1\n", 0)

        val status = prober(proc).probe()

        assertEquals(true, status.available)
        assertTrue(status.reason.contains("27.1.1"), status.reason)
        verify(proc).waitFor(6L, TimeUnit.SECONDS)
    }

    @Test
    fun `a failing docker version call reports the exit code`() {
        val status = prober(process("", 1)).probe()

        assertFalse(status.available)
        assertTrue(status.reason.contains("exit=1"), status.reason)
    }

    @Test
    fun `a silent docker version call is treated as unavailable`() {
        val status = prober(process("   ", 0)).probe()

        assertFalse(status.available)
    }

    @Test
    fun `a hanging probe is killed and reported as a timeout`() {
        val proc = process("", 0, finished = false)

        val status = prober(proc).probe()

        assertFalse(status.available)
        assertTrue(status.reason.contains("timed out"), status.reason)
        verify(proc).destroyForcibly()
    }

    @Test
    fun `an unreadable process stream is reported as a probe failure`() {
        val proc = mock<Process>()
        whenever(proc.waitFor(any(), any())).thenReturn(true)
        whenever(proc.inputStream).thenThrow(IllegalStateException("pipe closed"))

        val status = prober(proc).probe()

        assertFalse(status.available)
        assertTrue(status.reason.contains("pipe closed"), status.reason)
    }

    @Test
    fun `a missing docker executable is reported as a probe failure`() {
        val failing =
            DefaultDockerCliProber(
                { throw IOException("No such file or directory") },
                { _, _ -> process("x", 0) },
            )

        val status = failing.probe()

        assertFalse(status.available)
        assertTrue(status.reason.contains("No such file or directory"), status.reason)
    }

    @Test
    fun `a custom probe timeout is honoured`() {
        val proc = mock<Process>()
        whenever(proc.waitFor(any(), any())).thenReturn(true)
        whenever(proc.inputStream).thenReturn(ByteArrayInputStream("20.0".toByteArray()))
        val prober = DefaultDockerCliProber({ "docker" }, { _, _ -> proc }, probeTimeoutSeconds = 2L)

        assertEquals(true, prober.probe().available)
        verify(proc).waitFor(2L, TimeUnit.SECONDS)
        verify(proc, never()).destroyForcibly()
    }

    @Test
    fun `the probe passes the server version command and no working directory`() {
        var seen: List<String>? = null
        var workDir: File? = null
        val prober =
            DefaultDockerCliProber(
                { "docker" },
                { command, directory ->
                    seen = command
                    workDir = directory
                    process("1.0", 0)
                },
            )

        prober.probe()

        assertEquals(listOf("docker", "version", "--format", "{{.Server.Version}}"), seen)
        assertEquals(null, workDir)
    }
}
