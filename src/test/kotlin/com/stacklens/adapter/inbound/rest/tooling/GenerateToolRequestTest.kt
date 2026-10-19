package com.stacklens.adapter.inbound.rest.tooling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GenerateToolRequestTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val r = GenerateToolRequest()
        assertEquals("", r.name)
        assertNull(r.scriptContent)
    }

    @Test
    fun `named constructor populates all fields`() {
        val r = GenerateToolRequest(name = "n", scriptContent = "code")
        assertEquals("n", r.name)
        assertEquals("code", r.scriptContent)
    }

    @Test
    fun `equality is value-based`() {
        val a = GenerateToolRequest("n", "code")
        val b = GenerateToolRequest("n", "code")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `inequality when name differs`() {
        val a = GenerateToolRequest("n", "code")
        val b = GenerateToolRequest("m", "code")
        assertNotEquals(a, b)
    }

    @Test
    fun `inequality when scriptContent differs`() {
        val a = GenerateToolRequest("n", "a")
        val b = GenerateToolRequest("n", "b")
        assertNotEquals(a, b)
    }

    @Test
    fun `copy preserves and overrides fields`() {
        val base = GenerateToolRequest("n", "code")
        val copy = base.copy(name = "x")
        assertEquals("x", copy.name)
        assertEquals("code", copy.scriptContent)
        assertEquals("n", base.name)
    }
}
