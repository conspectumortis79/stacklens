package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ConnectionHealthSnapshotTest {
    @Test
    fun `constructor populates all fields`() {
        val now = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
        val snap =
            ConnectionHealthSnapshot(
                id = 7L,
                kind = "database",
                name = "prod-db",
                active = true,
                consecutiveFailures = 0,
                lastErrorAt = now,
                lastErrorMessage = "boom",
                healthy = true,
            )
        assertEquals(7L, snap.id)
        assertEquals("database", snap.kind)
        assertEquals("prod-db", snap.name)
        assertEquals(true, snap.active)
        assertEquals(0, snap.consecutiveFailures)
        assertEquals(now, snap.lastErrorAt)
        assertEquals("boom", snap.lastErrorMessage)
        assertEquals(true, snap.healthy)
    }

    @Test
    fun `healthy is null when not yet probed`() {
        val snap =
            ConnectionHealthSnapshot(
                id = 1L,
                kind = "k",
                name = "n",
                active = true,
                consecutiveFailures = 0,
                lastErrorAt = null,
                lastErrorMessage = null,
                healthy = null,
            )
        assertNull(snap.healthy)
    }

    @Test
    fun `equality is value-based`() {
        val a =
            ConnectionHealthSnapshot(
                id = 1L,
                kind = "k",
                name = "n",
                active = true,
                consecutiveFailures = 0,
                lastErrorAt = null,
                lastErrorMessage = null,
                healthy = true,
            )
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `copy overrides single field`() {
        val base =
            ConnectionHealthSnapshot(
                id = 1L,
                kind = "k",
                name = "n",
                active = true,
                consecutiveFailures = 0,
                lastErrorAt = null,
                lastErrorMessage = null,
                healthy = true,
            )
        val copy = base.copy(healthy = false)
        assertEquals(false, copy.healthy)
        assertEquals(true, base.healthy)
    }

    @Test
    fun `consecutiveFailures counter is exposed on the snapshot`() {
        val snap =
            ConnectionHealthSnapshot(
                id = 1L,
                kind = "k",
                name = "n",
                active = true,
                consecutiveFailures = 7,
                lastErrorAt = null,
                lastErrorMessage = null,
                healthy = false,
            )
        assertEquals(7, snap.consecutiveFailures)
    }
}
