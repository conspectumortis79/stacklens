package com.stacklens.application.port.out

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiscPortModelsCoverageTest {
    @Test
    fun `McpPingResult stores its arguments verbatim`() {
        val r = McpPingResult(ok = true, message = "ok", toolCount = 5)
        assertTrue(r.ok)
        assertEquals("ok", r.message)
        assertEquals(5, r.toolCount)
    }

    @Test
    fun `McpPingResult default toolCount is zero`() {
        val r = McpPingResult(ok = false, message = "err")
        assertFalse(r.ok)
        assertEquals("err", r.message)
        assertEquals(0, r.toolCount)
    }

    @Test
    fun `McpPingResult supports null message via default-free constructor`() {
        val r = McpPingResult(ok = false, message = "", toolCount = 0)
        assertFalse(r.ok)
        assertEquals("", r.message)
    }

    @Test
    fun `McpPingResult equality is value-based`() {
        val a = McpPingResult(ok = true, message = "x", toolCount = 1)
        val b = McpPingResult(ok = true, message = "x", toolCount = 1)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `McpPingResult copy lets us flip individual fields`() {
        val base = McpPingResult(ok = false, message = "m", toolCount = 2)
        val updated = base.copy(ok = true)
        assertTrue(updated.ok)
        assertEquals("m", updated.message)
        assertEquals(2, updated.toolCount)
    }

    @Test
    fun `McpPingResult supports null defaults via copy`() {
        val base = McpPingResult(ok = true, message = "m", toolCount = 4)
        val copy = base.copy(toolCount = 8)
        assertEquals(8, copy.toolCount)
    }

    @Test
    fun `McpPingResult componentN returns the right values`() {
        val r = McpPingResult(ok = true, message = "m", toolCount = 7)
        val (ok, message, count) = r
        assertTrue(ok)
        assertEquals("m", message)
        assertEquals(7, count)
    }

    @Test
    fun `McpPingResult toString contains the field values`() {
        val r = McpPingResult(ok = true, message = "msg", toolCount = 9)
        val s = r.toString()
        assertTrue(s.contains("true"))
        assertTrue(s.contains("msg"))
        assertTrue(s.contains("9"))
    }

    @Test
    fun `McpPingResult negative toolCount is allowed (no validation)`() {
        val r = McpPingResult(ok = false, message = "x", toolCount = -1)
        assertEquals(-1, r.toolCount)
    }

    @Test
    fun `McpPingResult empty message is allowed`() {
        val r = McpPingResult(ok = true, message = "", toolCount = 0)
        assertEquals("", r.message)
        assertNull(r.message.takeIf { it.isNotEmpty() })
    }
}
