package com.stacklens.adapter.out.mcp

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpCircuitStateAdapterTest {
    private val breaker = McpCircuitBreaker()

    @Test
    fun `snapshot delegates to McpCircuitBreaker and returns an empty map when no circuits are open`() {
        val adapter = McpCircuitStateAdapter(breaker)
        assertTrue(adapter.snapshot().isEmpty())
    }

    @Test
    fun `snapshot returns the current state of all open circuits`() {
        breaker.forceOpen("1:jira", openedAt = 42L)
        val snap = McpCircuitStateAdapter(breaker).snapshot()
        assertTrue(snap.containsKey("1:jira"))
        assertTrue(snap["1:jira"]?.isOpen == true)
    }
}
