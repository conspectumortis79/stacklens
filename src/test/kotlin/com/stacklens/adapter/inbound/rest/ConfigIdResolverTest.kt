package com.stacklens.adapter.inbound.rest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ConfigIdResolverTest {
    @Test
    fun `parseConfigId accepts numbers and trims numeric strings`() {
        assertEquals(7L, parseConfigId(7))
        assertEquals(7L, parseConfigId(7L))
        assertEquals(7L, parseConfigId(7.0))
        assertEquals(7L, parseConfigId("7"))
        assertEquals(7L, parseConfigId("  7  "))
    }

    @Test
    fun `parseConfigId rejects invalid and missing values`() {
        assertEquals(null, parseConfigId("abc"))
        assertEquals(null, parseConfigId(""))
        assertEquals(null, parseConfigId(null))
        assertEquals(null, parseConfigId(true))
    }

    @Test
    fun `resolveConfigId prefers the submitted id over the active fallback`() {
        var fallbackAsked = false

        val resolved =
            resolveConfigId("42") {
                fallbackAsked = true
                9L
            }

        assertEquals(42L, resolved)
        assertFalse(fallbackAsked)
    }

    @Test
    fun `resolveConfigId falls back to the first active config`() {
        assertEquals(9L, resolveConfigId(null) { 9L })
        assertEquals(9L, resolveConfigId("abc") { 9L })
    }

    @Test
    fun `resolveConfigId stays null when the fallback has no active config`() {
        assertEquals(null, resolveConfigId(null) { null })
    }
}
