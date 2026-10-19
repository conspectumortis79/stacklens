package com.stacklens.adapter.out.docker
import com.stacklens.application.port.out.docker.DockerSocketProbe
import com.stacklens.domain.docker.DockerStatus
import com.stacklens.testsupport.DockerPortsDouble
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.ByteBuffer

class DockerAvailabilityAdapterProbeTest {
    private val port: DockerPortsDouble = mock()
    private val probe: DockerSocketProbe = mock()

    private fun service(): DockerAvailabilityAdapter =
        DockerAvailabilityAdapter(
            cliProber = DefaultDockerCliProber(port::resolveDockerExecutable, probeTimeoutSeconds = 3L),
            socketProbe = probe,
            sockets = port,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    @Test
    fun `checkDockerAvailability returns cached available result within TTL`() {
        val svc = service()

        seedCache(svc, DockerStatus(true, "cached ok"))

        val result = svc.checkDockerAvailability()
        assertTrue(result.available)
        assertEquals("cached ok", result.reason)
    }

    @Test
    fun `checkDockerAvailability returns cached unavailable result within TTL`() {
        val svc = service()
        seedCache(svc, DockerStatus(false, "cached unavailable"))
        val result = svc.checkDockerAvailability()
        assertFalse(result.available)
        assertEquals("cached unavailable", result.reason)
    }

    @Test
    fun `checkDockerAvailability returns CLI probe result on success and logs`() {
        val svc = service()

        val shim = File.createTempFile("docker-", ".sh")
        shim.writeText("#!/bin/sh\necho '20.10.7'\nexit 0\n")
        shim.setExecutable(true)
        try {
            whenever(port.resolveDockerExecutable()).thenReturn(shim.absolutePath)
            whenever(port.findAllDockerSockets()).thenReturn(emptyList())
            val first = svc.checkDockerAvailability()
            assertTrue(first.available, "reason was: ${first.reason}")
            assertTrue(first.reason.contains("20.10.7"))

            val second = svc.checkDockerAvailability()
            assertEquals(first.reason, second.reason)
        } finally {
            shim.delete()
        }
    }

    @Test
    fun `checkDockerAvailability logs when transitioning from unavailable to available`() {
        val svc = service()

        val shim = File.createTempFile("docker-", ".sh")
        shim.writeText("#!/bin/sh\necho '24.0.0'\nexit 0\n")
        shim.setExecutable(true)
        try {
            setField(svc, "lastAvailable", false)
            whenever(port.resolveDockerExecutable()).thenReturn(shim.absolutePath)
            whenever(port.findAllDockerSockets()).thenReturn(emptyList())
            val result = svc.checkDockerAvailability()
            assertTrue(result.available)
        } finally {
            shim.delete()
        }
    }

    @Test
    fun `checkDockerAvailability falls back to socket ping and succeeds`() {
        val svc = service()
        val shim = File.createTempFile("docker-", ".sh")
        shim.writeText("#!/bin/sh\nexit 1\n")
        shim.setExecutable(true)
        try {
            whenever(port.resolveDockerExecutable()).thenReturn(shim.absolutePath)
            whenever(port.findAllDockerSockets()).thenReturn(listOf("/tmp/sock1", "/tmp/sock2"))
            whenever(probe.ping("/tmp/sock1")).thenReturn(DockerStatus(false, "sock1 fail"))
            whenever(probe.ping("/tmp/sock2")).thenReturn(DockerStatus(true, "sock2 ok"))
            val result = svc.checkDockerAvailability()
            assertTrue(result.available, "reason was: ${result.reason}")
            assertEquals("sock2 ok", result.reason)
        } finally {
            shim.delete()
        }
    }

    @Test
    fun `checkDockerAvailability logs when transitioning from unavailable to socket success`() {
        val svc = service()
        val shim = File.createTempFile("docker-", ".sh")
        shim.writeText("#!/bin/sh\nexit 1\n")
        shim.setExecutable(true)
        try {
            setField(svc, "lastAvailable", false)
            whenever(port.resolveDockerExecutable()).thenReturn(shim.absolutePath)
            whenever(port.findAllDockerSockets()).thenReturn(listOf("/tmp/sock"))
            whenever(probe.ping("/tmp/sock")).thenReturn(DockerStatus(true, "sock ok"))
            val result = svc.checkDockerAvailability()
            assertTrue(result.available)
        } finally {
            shim.delete()
        }
    }

    @Test
    fun `checkDockerAvailability combines all failure reasons and returns unavailable`() {
        val svc = service()
        val shim = File.createTempFile("docker-", ".sh")
        shim.writeText("#!/bin/sh\nexit 1\n")
        shim.setExecutable(true)
        try {
            whenever(port.resolveDockerExecutable()).thenReturn(shim.absolutePath)
            whenever(port.findAllDockerSockets()).thenReturn(listOf("/tmp/s1", "/tmp/s2"))
            whenever(probe.ping("/tmp/s1")).thenReturn(DockerStatus(false, "s1 reason"))
            whenever(probe.ping("/tmp/s2")).thenReturn(DockerStatus(false, "s2 reason"))
            val result = svc.checkDockerAvailability()
            assertFalse(result.available)
            assertTrue(result.reason.contains("s1 reason"))
            assertTrue(result.reason.contains("s2 reason"))
        } finally {
            shim.delete()
        }
    }

    @Test
    fun `checkDockerAvailability warns when transitioning from available to unavailable`() {
        val svc = service()
        val shim = File.createTempFile("docker-", ".sh")
        shim.writeText("#!/bin/sh\nexit 1\n")
        shim.setExecutable(true)
        try {
            setField(svc, "lastAvailable", true)
            whenever(port.resolveDockerExecutable()).thenReturn(shim.absolutePath)
            whenever(port.findAllDockerSockets()).thenReturn(emptyList())
            val result = svc.checkDockerAvailability()
            assertFalse(result.available)
        } finally {
            shim.delete()
        }
    }

    @Test
    fun `checkDockerAvailability does not warn when remaining unavailable`() {
        val svc = service()
        val shim = File.createTempFile("docker-", ".sh")
        shim.writeText("#!/bin/sh\nexit 1\n")
        shim.setExecutable(true)
        try {
            setField(svc, "lastAvailable", false)
            whenever(port.resolveDockerExecutable()).thenReturn(shim.absolutePath)
            whenever(port.findAllDockerSockets()).thenReturn(emptyList())
            val result = svc.checkDockerAvailability()
            assertFalse(result.available)
        } finally {
            shim.delete()
        }
    }

    @Test
    fun `checkDockerAvailability expires the unavailable cache after TTL`() {
        val svc = service()

        seedCache(svc, DockerStatus(false, "old"), System.currentTimeMillis() - 30_000)
        val shim = File.createTempFile("docker-", ".sh")
        shim.writeText("#!/bin/sh\nexit 1\n")
        shim.setExecutable(true)
        try {
            whenever(port.resolveDockerExecutable()).thenReturn(shim.absolutePath)
            whenever(port.findAllDockerSockets()).thenReturn(emptyList())
            val result = svc.checkDockerAvailability()
            assertFalse(result.available)
        } finally {
            shim.delete()
        }
    }

    @Test
    fun `checkDockerAvailability logs availability on first ever probe`() {
        val svc = service()

        val shim = File.createTempFile("docker-", ".sh")
        shim.writeText("#!/bin/sh\necho '25.0.0'\nexit 0\n")
        shim.setExecutable(true)
        try {
            whenever(port.resolveDockerExecutable()).thenReturn(shim.absolutePath)
            whenever(port.findAllDockerSockets()).thenReturn(emptyList())
            val result = svc.checkDockerAvailability()
            assertTrue(result.available)
        } finally {
            shim.delete()
        }
    }

    private fun seedCache(
        svc: DockerAvailabilityAdapter,
        status: DockerStatus,
        at: Long = System.currentTimeMillis(),
    ) {
        val cacheField = DockerAvailabilityAdapter::class.java.getDeclaredField("cachedStatus")
        cacheField.isAccessible = true
        cacheField.set(svc, status)
        val cacheAtField = DockerAvailabilityAdapter::class.java.getDeclaredField("cachedAt")
        cacheAtField.isAccessible = true
        cacheAtField.setLong(svc, at)
    }

    private fun setField(
        svc: DockerAvailabilityAdapter,
        name: String,
        value: Any?,
    ) {
        val field = DockerAvailabilityAdapter::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(svc, value)
    }
}

class DefaultDockerSocketProbeTest {
    @Test
    fun `ping returns available when server replies with HTTP 200`() {
        val sock = File.createTempFile("docker-probe-", ".sock").apply { delete() }
        val addr = java.net.UnixDomainSocketAddress.of(sock.absolutePath)
        val serverCh =
            java.nio.channels.ServerSocketChannel
                .open(java.net.StandardProtocolFamily.UNIX)
        serverCh.bind(addr)
        try {
            val serverThread =
                Thread {
                    val client = serverCh.accept()
                    val buf = ByteBuffer.allocate(256)
                    while (client.read(buf) > 0) {
                        buf.flip()

                        client.write(java.nio.ByteBuffer.wrap("HTTP/1.1 200 OK\r\n\r\n".toByteArray()))
                        client.close()
                        break
                    }
                }
            serverThread.isDaemon = true
            serverThread.start()

            val probe =
                DefaultDockerSocketProbe(
                    com.stacklens.shared
                        .DefaultSocketChannelFactory(),
                )
            val status = probe.ping(sock.absolutePath)
            assertTrue(status.available, "Expected available but was: ${status.reason}")
            assertNotNull(status.reason)
        } finally {
            runCatching { serverCh.close() }
            sock.delete()
        }
    }

    @Test
    fun `ping returns unavailable when socket does not exist`() {
        val probe =
            DefaultDockerSocketProbe(
                com.stacklens.shared
                    .DefaultSocketChannelFactory(),
            )
        val status = probe.ping("/tmp/definitely-missing-${System.nanoTime()}.sock")
        assertFalse(status.available)
        assertNotNull(status.reason)
    }
}
