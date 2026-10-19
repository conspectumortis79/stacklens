package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FindingSeverityTest {
    @Test
    fun `fromWire resolves each declared severity`() {
        assertEquals(FindingSeverity.HIGH, FindingSeverity.fromWire("HIGH"))
        assertEquals(FindingSeverity.MEDIUM, FindingSeverity.fromWire("MEDIUM"))
        assertEquals(FindingSeverity.LOW, FindingSeverity.fromWire("LOW"))
    }

    @Test
    fun `fromWire normalises case and surrounding whitespace`() {
        assertEquals(FindingSeverity.HIGH, FindingSeverity.fromWire(" high "))
        assertEquals(FindingSeverity.MEDIUM, FindingSeverity.fromWire("Medium"))
    }

    @Test
    fun `fromWire falls back to LOW for unknown or missing values`() {
        assertEquals(FindingSeverity.LOW, FindingSeverity.fromWire("BLOCKER"))
        assertEquals(FindingSeverity.LOW, FindingSeverity.fromWire(""))
        assertEquals(FindingSeverity.LOW, FindingSeverity.fromWire(null))
    }

    @Test
    fun `badgeFor renders the report icon of a severity`() {
        assertEquals("🔴", FindingSeverity.badgeFor("HIGH"))
        assertEquals("🟡", FindingSeverity.badgeFor("medium"))
        assertEquals("🟢", FindingSeverity.badgeFor("unparseable"))
    }

    @Test
    fun `every severity exposes its wire value and badge`() =
        assertEquals(
            listOf(
                Triple("HIGH", "🔴", FindingSeverity.HIGH),
                Triple("MEDIUM", "🟡", FindingSeverity.MEDIUM),
                Triple("LOW", "🟢", FindingSeverity.LOW),
            ),
            FindingSeverity.entries.map { Triple(it.wire, it.badge, it) },
        )
}
