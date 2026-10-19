package com.stacklens.adapter.out.docker

import com.stacklens.application.port.out.docker.DockerCliProber
import com.stacklens.application.port.out.docker.DockerSocketProbe
import com.stacklens.domain.docker.DockerStatus
import com.stacklens.shared.DefaultSocketChannelFactory
import com.stacklens.testsupport.DockerPortsDouble
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class DockerAvailabilityAdapterTest {
    private val port: DockerPortsDouble = mock()

    private fun service(
        cliProber: DockerCliProber = DefaultDockerCliProber(port::resolveDockerExecutable, probeTimeoutSeconds = 1L),
        socketProbe: DockerSocketProbe = DefaultDockerSocketProbe(DefaultSocketChannelFactory()),
        probeCacheTtlMs: Long = DockerAvailabilityAdapter.PROBE_CACHE_TTL_MS,
        probeUnavailableCacheTtlMs: Long = DockerAvailabilityAdapter.PROBE_UNAVAILABLE_CACHE_TTL_MS,
        clock: FixedClockPort = FixedClockPort(0L),
    ): DockerAvailabilityAdapter =
        DockerAvailabilityAdapter(
            cliProber = cliProber,
            socketProbe = socketProbe,
            sockets = port,
            clock = clock,
            probeCacheTtlMs = probeCacheTtlMs,
            probeUnavailableCacheTtlMs = probeUnavailableCacheTtlMs,
        )

    @Test
    fun `checkDockerAvailability reports available through a live socket server`() {
        val sock =
            File.createTempFile("docker-test-", ".sock").apply {
                delete()
            }
        val addr = java.net.UnixDomainSocketAddress.of(sock.absolutePath)
        val serverCh =
            java.nio.channels.ServerSocketChannel
                .open(java.net.StandardProtocolFamily.UNIX)
        serverCh.bind(addr)
        try {
            val serverThread =
                Thread {
                    val client = serverCh.accept()
                    val buf = java.nio.ByteBuffer.allocate(128)
                    client.read(buf)
                    val resp = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK"
                    client.write(java.nio.ByteBuffer.wrap(resp.toByteArray()))
                    client.close()
                }
            serverThread.isDaemon = true
            serverThread.start()
            whenever(port.findAllDockerSockets()).thenReturn(listOf(sock.absolutePath))
            val status = service(cliProber = DockerCliProber { DockerStatus(false, "cli unavailable") }).checkDockerAvailability()
            assertTrue(status.available, "Expected available but was: ${status.reason}")
        } finally {
            runCatching { serverCh.close() }
            sock.delete()
        }
    }

    @Test
    fun `checkDockerAvailability reports unavailable when socket does not exist`() {
        val nonExistent = "/tmp/does-not-exist-${System.nanoTime()}.sock"
        whenever(port.findAllDockerSockets()).thenReturn(listOf(nonExistent))
        val status = service(cliProber = DockerCliProber { DockerStatus(false, "cli unavailable") }).checkDockerAvailability()
        assertFalse(status.available)
        assertTrue(status.reason.contains("cli unavailable"))
    }

    @Test
    fun `checkDockerAvailability caches a successful probe within TTL`() {
        whenever(port.findAllDockerSockets()).thenReturn(emptyList())
        whenever(port.resolveDockerExecutable()).thenReturn("/nonexistent/docker")
        val clock = FixedClockPort(1_000L)
        val svc = service(clock = clock)

        val first = svc.checkDockerAvailability()
        clock.advanceMillis(1_000L)
        val second = svc.checkDockerAvailability()

        assertFalse(first.available)
        assertEquals(first.reason, second.reason)
    }

    @Test
    fun `checkDockerAvailability re-probes once the available TTL expires`() {
        var probes = 0
        whenever(port.findAllDockerSockets()).thenReturn(emptyList())
        val clock = FixedClockPort(5_000L)
        val svc =
            service(
                cliProber = DockerCliProber { DockerStatus(true, "v1").also { probes++ } },
                probeCacheTtlMs = 8_000L,
                clock = clock,
            )
        svc.checkDockerAvailability()
        clock.advanceMillis(7_999L)
        svc.checkDockerAvailability()
        assertEquals(1, probes)
        clock.advanceMillis(2L)
        svc.checkDockerAvailability()
        assertEquals(2, probes)
    }

    @Test
    fun `checkDockerAvailability keeps unavailable result cached for the longer TTL`() {
        var probes = 0
        whenever(port.findAllDockerSockets()).thenReturn(emptyList())
        val clock = FixedClockPort(0L)
        val svc =
            service(
                cliProber = DockerCliProber { DockerStatus(false, "down").also { probes++ } },
                probeCacheTtlMs = 1_000L,
                probeUnavailableCacheTtlMs = 20_000L,
                clock = clock,
            )
        svc.checkDockerAvailability()
        clock.advanceMillis(5_000L)
        svc.checkDockerAvailability()
        assertEquals(1, probes)
    }

    @Test
    fun `checkDockerAvailability uses the CLI probe first and skips socket probing`() {
        whenever(port.findAllDockerSockets()).thenReturn(listOf("/never/pinged.sock"))
        val svc = service(cliProber = DockerCliProber { DockerStatus(true, "cli-ok") })
        val result = svc.checkDockerAvailability()
        assertTrue(result.available)
        assertEquals("cli-ok", result.reason)
    }

    @Test
    fun `checkDockerAvailability combines CLI and socket failure reasons`() {
        whenever(port.findAllDockerSockets()).thenReturn(listOf("/sock/a", "/sock/b"))
        val socketProbe = DockerSocketProbe { DockerStatus(false, "refused $it") }
        val svc = service(cliProber = DockerCliProber { DockerStatus(false, "cli down") }, socketProbe = socketProbe)
        val result = svc.checkDockerAvailability()
        assertFalse(result.available)
        assertTrue(result.reason.contains("cli down"))
        assertTrue(result.reason.contains("refused /sock/a"))
        assertTrue(result.reason.contains("refused /sock/b"))
    }

    @Test
    fun `checkDockerAvailability stops socket probing at the first reachable socket`() {
        var pings = 0
        val socketProbe =
            DockerSocketProbe { path ->
                pings++
                if (path == "/sock/b") DockerStatus(true, "b reachable") else DockerStatus(false, "nope")
            }
        whenever(port.findAllDockerSockets()).thenReturn(listOf("/sock/a", "/sock/b", "/sock/c"))
        val svc = service(cliProber = DockerCliProber { DockerStatus(false, "cli down") }, socketProbe = socketProbe)
        val result = svc.checkDockerAvailability()
        assertTrue(result.available)
        assertEquals("b reachable", result.reason)
        assertEquals(2, pings)
    }

    @Test
    fun `docker CLI probe reports unavailable when the binary does not exist`() {
        val path = "/this/path/does/not/exist/docker-${System.nanoTime()}"
        val status = DefaultDockerCliProber({ path }, probeTimeoutSeconds = 1L).probe()
        assertFalse(status.available)
        assertTrue(status.reason.contains("docker CLI probe"))
    }

    @Test
    fun `docker CLI probe times out for a hanging binary`() {
        val shim = File.createTempFile("docker-shim-", ".sh")
        shim.writeText("#!/bin/sh\nsleep 7\n")
        shim.setExecutable(true)
        try {
            val start = System.currentTimeMillis()
            val status = DefaultDockerCliProber({ shim.absolutePath }, probeTimeoutSeconds = 1L).probe()
            val elapsed = System.currentTimeMillis() - start
            assertFalse(status.available)
            assertTrue(status.reason.contains("timed out"), "reason was: ${status.reason}")
            assertTrue(elapsed < 4_000, "elapsed was ${elapsed}ms")
        } finally {
            shim.delete()
        }
    }

    @Test
    fun `docker CLI probe reports non-zero exit`() {
        val shim = File.createTempFile("docker-shim-", ".sh")
        shim.writeText("#!/bin/sh\nexit 1\n")
        shim.setExecutable(true)
        try {
            val status = DefaultDockerCliProber({ shim.absolutePath }, probeTimeoutSeconds = 3L).probe()
            assertFalse(status.available)
            assertTrue(status.reason.contains("exit=1"), "reason was: ${status.reason}")
        } finally {
            shim.delete()
        }
    }

    @Test
    fun `docker CLI probe reports blank output as unavailable`() {
        val shim = File.createTempFile("docker-shim-", ".sh")
        shim.writeText("#!/bin/sh\nexit 0\n")
        shim.setExecutable(true)
        try {
            val status = DefaultDockerCliProber({ shim.absolutePath }, probeTimeoutSeconds = 3L).probe()
            assertFalse(status.available)
            assertTrue(status.reason.contains("docker CLI"), "reason was: ${status.reason}")
        } finally {
            shim.delete()
        }
    }

    @Test
    fun `docker CLI probe reports the version output when the binary succeeds`() {
        val shim = File.createTempFile("docker-shim-", ".sh")
        shim.writeText("#!/bin/sh\necho '20.10.5'\nexit 0\n")
        shim.setExecutable(true)
        try {
            val status = DefaultDockerCliProber({ shim.absolutePath }, probeTimeoutSeconds = 3L).probe()
            assertTrue(status.available, "reason was: ${status.reason}")
            assertTrue(status.reason.contains("20.10.5"), "reason was: ${status.reason}")
        } finally {
            shim.delete()
        }
    }
}
