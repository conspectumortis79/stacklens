package com.stacklens.adapter.out.health

import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpPingResult
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultMcpConnectionProbeTest {
    private val gateway = mock<McpGatewayPort>()
    private val probe = DefaultMcpConnectionProbe(gateway)
    private val cfg = McpServerConfig(id = 1L, name = "m", baseUrl = "http://localhost:9999")

    @Test
    fun `probe returns Ok when gateway ping is healthy`() {
        whenever(gateway.ping(cfg)).thenReturn(McpPingResult(ok = true, message = "OK", toolCount = 12))
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Ok)
        r as ConnectionProbeResult.Ok
        assertEquals(12, r.toolCount)
    }

    @Test
    fun `probe returns Failure with gateway message when ping is unhealthy`() {
        whenever(gateway.ping(cfg)).thenReturn(McpPingResult(ok = false, message = "connection refused"))
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Failure)
        r as ConnectionProbeResult.Failure
        assertEquals("connection refused", r.errorMessage)
    }

    @Test
    fun `probe truncates long error messages to keep the persisted column tidy`() {
        val longMsg = "x".repeat(5000)
        whenever(gateway.ping(cfg)).thenReturn(McpPingResult(ok = false, message = longMsg))
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Failure)
        r as ConnectionProbeResult.Failure
        assertTrue(r.errorMessage.length <= 500, "message was ${r.errorMessage.length} chars")
        assertTrue(r.errorMessage.endsWith("…"))
    }

    @Test
    fun `probe never throws - always returns Ok or Failure`() {
        whenever(gateway.ping(cfg)).thenReturn(McpPingResult(ok = false, message = ""))
        val r = probe.probe(cfg)
        assertTrue(r is ConnectionProbeResult.Failure)
    }
}
