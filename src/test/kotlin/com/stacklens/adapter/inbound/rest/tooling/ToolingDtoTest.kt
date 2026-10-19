package com.stacklens.adapter.inbound.rest.tooling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ToolingDtoTest {
    @Test
    fun `GenerateToolRequest no-arg constructor uses defaults`() {
        val req = GenerateToolRequest()
        assertEquals("", req.name)
        assertNull(req.scriptContent)
    }
}
