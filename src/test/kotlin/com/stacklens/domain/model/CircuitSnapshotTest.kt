package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CircuitSnapshotTest {
    @Test
    fun `constructor populates all fields`() {
        val snap =
            CircuitSnapshot(
                mcpConfigId = 42L,
                provider = "github",
                isOpen = true,
                failures = 3,
                openedAt = 1234567L,
                probing = true,
            )
        assertEquals(42L, snap.mcpConfigId)
        assertEquals("github", snap.provider)
        assertEquals(true, snap.isOpen)
        assertEquals(3, snap.failures)
        assertEquals(1234567L, snap.openedAt)
        assertEquals(true, snap.probing)
    }

    @Test
    fun `equality is value-based`() {
        val a =
            CircuitSnapshot(
                mcpConfigId = 1L,
                provider = "x",
                isOpen = false,
                failures = 0,
                openedAt = 0L,
                probing = false,
            )
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `copy overrides one field at a time`() {
        val base = CircuitSnapshot(1L, "p", false, 0, 0L, false)
        val copy = base.copy(failures = 5)
        assertEquals(5, copy.failures)
        assertEquals(1L, copy.mcpConfigId)
    }
}
