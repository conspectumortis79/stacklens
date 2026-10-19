package com.stacklens.application.service
import com.stacklens.adapter.out.docker.DefaultDockerSocketProbe
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.ByteBuffer

class DefaultDockerSocketProbeBranchesTest {
    @Test
    fun `ping returns available when daemon responds with HTTP 200 OK`() {
        val sock = newSocketFile()
        val serverCh =
            java.nio.channels.ServerSocketChannel
                .open(java.net.StandardProtocolFamily.UNIX)
        serverCh.bind(java.net.UnixDomainSocketAddress.of(sock.absolutePath))
        try {
            val serverThread =
                Thread {
                    val client = serverCh.accept()
                    try {
                        val buf = ByteBuffer.allocate(128)
                        client.read(buf)
                        val resp = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK"
                        client.write(ByteBuffer.wrap(resp.toByteArray()))
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
                com.stacklens.shared.DefaultSocketChannelFactory(),
            )
        val status = probe.ping("/tmp/m-${System.nanoTime()}.sock")
        assertFalse(status.available)
        assertNotNull(status.reason)
    }

    @Test
    fun `ping returns unavailable with unexpected response when server returns non-200`() {
        val sock = newSocketFile()
        val serverCh =
            java.nio.channels.ServerSocketChannel
                .open(java.net.StandardProtocolFamily.UNIX)
        serverCh.bind(java.net.UnixDomainSocketAddress.of(sock.absolutePath))
        try {
            val serverThread =
                Thread {
                    val client = serverCh.accept()
                    try {
                        val buf = ByteBuffer.allocate(128)
                        client.read(buf)
                        val resp = "HTTP/1.1 500 Internal Server Error\r\n\r\n"
                        client.write(ByteBuffer.wrap(resp.toByteArray()))
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
            assertTrue(status.reason.contains("unexpected response") || status.reason.contains("500"))
        } finally {
            runCatching { serverCh.close() }
            sock.delete()
        }
    }

    @Test
    fun `ping returns unavailable when connection times out`() {
        val probe =
            DefaultDockerSocketProbe(
                com.stacklens.shared.DefaultSocketChannelFactory(),
            )
        val status = probe.ping("/no/such/path/${System.nanoTime()}.sock")
        assertFalse(status.available)
        assertNotNull(status.reason)
    }

    private fun newSocketFile(): File {
        val sock = File("/tmp/dp-${System.nanoTime()}.sock")
        if (sock.exists()) sock.delete()
        return sock
    }
}
