package com.stacklens.adapter.out.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpCircuitBreakerStateTest {
    private val breaker = McpCircuitBreaker()

    @Test
    fun `a fresh state is closed and counts no failures`() {
        val state = McpCircuitBreaker.State()

        assertEquals(0, state.failures)
        assertEquals(0L, state.openedAt)
        assertFalse(state.isOpen)
        assertFalse(state.probing)
    }

    @Test
    fun `an opened state stays open for the cooldown and closes on success`() {
        val key = "breaker-open-${System.nanoTime()}"

        repeat(3) { breaker.recordFailure(key) }

        assertTrue(McpCircuitBreaker.State(failures = 3, isOpen = true).isOpen)
        assertFalse(breaker.isAllowed(key))
        breaker.recordSuccess(key)
        assertTrue(breaker.isAllowed(key))
    }

    @Test
    fun `a single copied flag keeps the remaining values`() {
        val state = McpCircuitBreaker.State(failures = 2).copy(probing = true)

        assertEquals(2, state.failures)
        assertTrue(state.probing)
        assertFalse(state.isOpen)
    }
}
