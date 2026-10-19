package com.stacklens.application.port.out

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpGatewayPortDataClassesTest {
    @Test
    fun `McpToolDescriptor stores name with default empty description`() {
        val d = McpToolDescriptor(name = "tool1")
        assertEquals("tool1", d.name)
        assertEquals("", d.description)
    }

    @Test
    fun `McpToolDescriptor stores name and description`() {
        val d = McpToolDescriptor(name = "tool1", description = "A tool")
        assertEquals("tool1", d.name)
        assertEquals("A tool", d.description)
    }

    @Test
    fun `McpToolResult stores text with default isError false`() {
        val r = McpToolResult(text = "ok")
        assertEquals("ok", r.text)
        assertFalse(r.isError)
    }

    @Test
    fun `McpToolResult stores text and isError true`() {
        val r = McpToolResult(text = "fail", isError = true)
        assertEquals("fail", r.text)
        assertTrue(r.isError)
    }

    @Test
    fun `McpPingResult stores all fields with default toolCount zero`() {
        val p = McpPingResult(ok = true, message = "alive")
        assertTrue(p.ok)
        assertEquals("alive", p.message)
        assertEquals(0, p.toolCount)
    }

    @Test
    fun `McpPingResult supports ok false with custom toolCount`() {
        val p = McpPingResult(ok = false, message = "down", toolCount = 42)
        assertFalse(p.ok)
        assertEquals("down", p.message)
        assertEquals(42, p.toolCount)
    }
}
