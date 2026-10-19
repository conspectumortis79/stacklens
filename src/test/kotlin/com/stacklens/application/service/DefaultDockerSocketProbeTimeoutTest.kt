package com.stacklens.application.service
import com.stacklens.adapter.out.docker.DefaultDockerSocketProbe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DefaultDockerSocketProbeTimeoutTest {
    private fun steppingClock(stepMillis: Long): () -> Long {
        var now = 0L
        return {
            val current = now
            now += stepMillis
            current
        }
    }

    @Test
    fun `ping returns unavailable with timeout reason when server never responds`() {
        val sock = File("/tmp/dp-timeout-${System.nanoTime()}.sock").apply { delete() }
        val serverCh =
            java.nio.channels.ServerSocketChannel
                .open(java.net.StandardProtocolFamily.UNIX)
        serverCh.bind(java.net.UnixDomainSocketAddress.of(sock.absolutePath))
        try {
            val serverThread =
                Thread {
                    val client = serverCh.accept()

                    try {
                        client.read(java.nio.ByteBuffer.allocate(256))
                        Thread.sleep(5000)
                    } catch (_: InterruptedException) {
                    } finally {
                        runCatching { client.close() }
                    }
                }
            serverThread.isDaemon = true
            serverThread.start()
            Thread.sleep(100)

            val probe =
                DefaultDockerSocketProbe(
                    com.stacklens.shared.DefaultSocketChannelFactory(),
                    clock = steppingClock(900L),
                )
            val status = probe.ping(sock.absolutePath)
            assertFalse(status.available)
            assertEquals(
                "socket read timed out: $sock",
                status.reason,
            )
        } finally {
            runCatching { serverCh.close() }
            sock.delete()
        }
    }

    @Test
    fun `ping returns unavailable when socket path is in invalid location`() {
        val probe =
            DefaultDockerSocketProbe(
                com.stacklens.shared.DefaultSocketChannelFactory(),
            )
        val status = probe.ping("/proc/this-cannot-exist-${System.nanoTime()}.sock")
        assertFalse(status.available)
        assertNotNull(status.reason)
    }

    @Test
    fun `ping returns unavailable when server returns non-200 status`() {
        val sock = File("/tmp/dp-500-${System.nanoTime()}.sock").apply { delete() }
        val serverCh =
            java.nio.channels.ServerSocketChannel
                .open(java.net.StandardProtocolFamily.UNIX)
        serverCh.bind(java.net.UnixDomainSocketAddress.of(sock.absolutePath))
        try {
            val serverThread =
                Thread {
                    val client = serverCh.accept()
                    try {
                        val buf = java.nio.ByteBuffer.allocate(256)
                        client.read(buf)
                        val resp = "HTTP/1.1 503 Service Unavailable\r\n\r\n"
                        client.write(java.nio.ByteBuffer.wrap(resp.toByteArray()))
                    } finally {
                        runCatching { client.close() }
                    }
                }
            serverThread.isDaemon = true
            serverThread.start()
            Thread.sleep(100)

            val probe =
                DefaultDockerSocketProbe(
                    com.stacklens.shared.DefaultSocketChannelFactory(),
                )
            val status = probe.ping(sock.absolutePath)
            assertFalse(status.available)

            assertTrue(
                status.reason.contains("503") || status.reason.contains("unexpected"),
                "reason was: ${status.reason}",
            )
        } finally {
            runCatching { serverCh.close() }
            sock.delete()
        }
    }
}
