package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchCancellationRegistryTest {
    @Test
    fun `isCancelled returns false for an unknown session`() {
        val r = SearchCancellationRegistry()
        assertFalse(r.isCancelled(1L))
    }

    @Test
    fun `cancel marks a session as cancelled`() {
        val r = SearchCancellationRegistry()
        r.cancel(1L)
        assertTrue(r.isCancelled(1L))
    }

    @Test
    fun `cancel is idempotent`() {
        val r = SearchCancellationRegistry()
        r.cancel(1L)
        r.cancel(1L)
        assertTrue(r.isCancelled(1L))
    }

    @Test
    fun `remove clears the cancellation flag`() {
        val r = SearchCancellationRegistry()
        r.cancel(1L)
        r.remove(1L)
        assertFalse(r.isCancelled(1L))
    }

    @Test
    fun `remove on an unknown session is a no-op`() {
        val r = SearchCancellationRegistry()
        r.remove(42L)
        assertFalse(r.isCancelled(42L))
    }

    @Test
    fun `cancel is per-session — cancelling 1 does not affect 2`() {
        val r = SearchCancellationRegistry()
        r.cancel(1L)
        assertTrue(r.isCancelled(1L))
        assertFalse(r.isCancelled(2L))
    }
}
