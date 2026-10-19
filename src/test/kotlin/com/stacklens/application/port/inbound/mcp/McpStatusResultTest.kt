package com.stacklens.application.port.inbound.mcp

import com.stacklens.domain.model.CircuitSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class McpStatusResultTest {
    @Test
    fun `CircuitSnapshot getters are covered`() {
        val s =
            CircuitSnapshot(
                mcpConfigId = 1L,
                provider = "jira",
                isOpen = true,
                failures = 3,
                openedAt = 1_700_000_000L,
                probing = false,
            )
        assertEquals(1L, s.mcpConfigId)
        assertEquals("jira", s.provider)
        assertEquals(true, s.isOpen)
        assertEquals(3, s.failures)
        assertEquals(1_700_000_000L, s.openedAt)
        assertEquals(false, s.probing)
    }

    @Test
    fun `McpStatusResult getters are covered`() {
        val s =
            McpStatusResult(
                active = true,
                mcpServerName = "srv",
                routes = mapOf("github" to true, "gitlab" to false),
                processRunning = false,
            )
        assertEquals(true, s.active)
        assertEquals("srv", s.mcpServerName)
        assertEquals(mapOf("github" to true, "gitlab" to false), s.routes)
        assertEquals(false, s.processRunning)
        assertEquals(emptyMap<String, CircuitSnapshot>(), s.circuits)
    }

    @Test
    fun `McpStatusResult with circuits map is covered`() {
        val circuits = mapOf("1:jira" to CircuitSnapshot(1L, "jira", false, 0, 0L, false))
        val s =
            McpStatusResult(
                active = false,
                mcpServerName = null,
                routes = emptyMap(),
                processRunning = true,
                circuits = circuits,
            )
        assertEquals(false, s.active)
        assertEquals(null, s.mcpServerName)
        assertEquals(emptyMap<String, Boolean>(), s.routes)
        assertEquals(true, s.processRunning)
        assertEquals(circuits, s.circuits)
    }
}
