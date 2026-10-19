package com.stacklens.adapter.out.mcp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket

class DefaultMcpPortProbePortTest {
    private val probe = DefaultMcpPortProbePort()

    @Test
    fun `isInUse returns true for a port that is open`() {
        val server = ServerSocket(0)
        try {
            val port = server.localPort
            assertTrue(probe.isInUse("127.0.0.1", port, 200))
        } finally {
            server.close()
        }
    }

    @Test
    fun `isInUse returns false for a port that is closed`() {
        val server = ServerSocket(0)
        val port = server.localPort
        server.close()
        assertFalse(probe.isInUse("127.0.0.1", port, 200))
    }

    @Test
    fun `isInUse returns false for an unreachable host`() {
        assertFalse(probe.isInUse("0.0.0.0", 1, 100))
    }

    @Test
    fun `isInUse swallows IOException when the timeout is exceeded`() {
        assertFalse(probe.isInUse("192.0.2.1", 1, 50))
    }
}
