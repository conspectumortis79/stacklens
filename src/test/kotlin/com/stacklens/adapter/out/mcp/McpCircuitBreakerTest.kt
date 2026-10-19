package com.stacklens.adapter.out.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class McpCircuitBreakerTest {
    private val breaker = McpCircuitBreaker()

    @Test
    fun `providerOf returns the segment before the first underscore`() {
        assertEquals("jira", breaker.providerOf("jira_search_issues"))
        assertEquals("github", breaker.providerOf("github_create_pr"))
        assertEquals("gitlab", breaker.providerOf("gitlab_list_mrs"))
    }

    @Test
    fun `providerOf returns the whole string when there is no underscore`() {
        assertEquals("foo", breaker.providerOf("foo"))
    }

    @Test
    fun `providerOf returns empty string when the tool name is empty`() {
        assertEquals("", breaker.providerOf(""))
    }

    @Test
    fun `keyFor combines configId and provider prefix`() {
        assertEquals("1:jira", breaker.keyFor(1L, "jira_search"))
        assertEquals("42:github", breaker.keyFor(42L, "github_pr"))
    }

    @Test
    fun `isAllowed returns true when no state is registered`() {
        assertTrue(breaker.isAllowed("missing:key"))
    }

    @Test
    fun `isAllowed returns true when the state is closed`() {
        val key = "k"
        breaker.recordFailure(key)
        breaker.recordSuccess(key)
        assertTrue(breaker.isAllowed(key))
    }

    @Test
    fun `isAllowed returns false when the circuit is open`() {
        val key = "k"
        breaker.forceOpen(key)
        assertFalse(breaker.isAllowed(key))
    }

    @Test
    fun `isAllowed transitions to half-open after the cooldown`() {
        val key = "k"
        breaker.forceOpen(key, openedAt = System.currentTimeMillis() - 70_000)
        assertTrue(breaker.isAllowed(key))
    }

    @Test
    fun `isAllowed stays false while a half-open probe is in-flight`() {
        val key = "k"
        breaker.forceOpen(key, openedAt = System.currentTimeMillis() - 70_000)
        assertTrue(breaker.isAllowed(key))
        assertFalse(breaker.isAllowed(key))
    }

    @Test
    fun `recordSuccess removes the state entry and closes the circuit`() {
        val key = "k"
        breaker.forceOpen(key)
        breaker.recordSuccess(key)
        assertTrue(breaker.isAllowed(key))
    }

    @Test
    fun `recordSuccess on a missing key is a no-op`() {
        breaker.recordSuccess("missing")
    }

    @Test
    fun `recordFailure below the threshold keeps the circuit closed`() {
        val key = "k"
        breaker.recordFailure(key)
        breaker.recordFailure(key)
        assertTrue(breaker.isAllowed(key))
    }

    @Test
    fun `recordFailure at the threshold opens the circuit`() {
        val key = "k"
        repeat(3) { breaker.recordFailure(key) }
        assertFalse(breaker.isAllowed(key))
    }

    @Test
    fun `recordFailure after a half-open probe failure refreshes the cooldown`() {
        val key = "k"
        breaker.forceOpen(key, openedAt = System.currentTimeMillis() - 70_000)
        assertTrue(breaker.isAllowed(key))
        breaker.recordFailure(key)
        assertFalse(breaker.isAllowed(key))
    }

    @Test
    fun `snapshot returns an empty map when no circuits are registered`() {
        assertTrue(breaker.snapshot().isEmpty())
    }

    @Test
    fun `snapshot returns a snapshot for every registered circuit`() {
        breaker.forceOpen("1:jira", openedAt = 1234L)
        breaker.forceOpen("2:github", openedAt = 5678L)
        val snap = breaker.snapshot()
        assertEquals(2, snap.size)
        assertEquals(1234L, snap["1:jira"]?.openedAt)
        assertEquals(5678L, snap["2:github"]?.openedAt)
        assertEquals("jira", snap["1:jira"]?.provider)
        assertEquals("github", snap["2:github"]?.provider)
        assertEquals(1L, snap["1:jira"]?.mcpConfigId)
        assertEquals(2L, snap["2:github"]?.mcpConfigId)
        assertTrue(snap["1:jira"]?.isOpen == true)
    }

    @Test
    fun `snapshot falls back to mcpConfigId=0 and empty provider when the key has no colon`() {
        breaker.forceOpen("jira", openedAt = 0L)
        val snap = breaker.snapshot()
        assertEquals(0L, snap["jira"]?.mcpConfigId)
        assertEquals("", snap["jira"]?.provider)
    }

    @Test
    fun `resetAll removes every circuit`() {
        breaker.forceOpen("1:jira")
        breaker.forceOpen("2:github")
        breaker.resetAll()
        assertTrue(breaker.snapshot().isEmpty())
    }

    @Test
    fun `only one concurrent caller may probe a half-open circuit`() {
        val key = "1:jira"
        breaker.forceOpen(key, openedAt = 0L)
        val callers = 8
        val start = CountDownLatch(1)
        val finished = CountDownLatch(callers)
        val granted = AtomicInteger(0)
        repeat(callers) {
            Thread {
                try {
                    start.await()
                    if (breaker.isAllowed(key)) granted.incrementAndGet()
                } finally {
                    finished.countDown()
                }
            }.start()
        }
        start.countDown()

        assertTrue(finished.await(10, TimeUnit.SECONDS))
        assertEquals(1, granted.get())
    }
}
