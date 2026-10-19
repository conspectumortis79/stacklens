package com.stacklens.adapter.inbound.rest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class McpApiDtoRequestTest {
    @Test
    fun `McpApiDtoRequest default values`() {
        val req = McpApiController.McpApiDtoRequest()
        assertEquals("", req.tool)
        assertEquals(emptyMap<String, Any?>(), req.args)
        assertNull(req.mcpServerId)
    }

    @Test
    fun `McpApiDtoRequest creates with explicit values`() {
        val req = McpApiController.McpApiDtoRequest(tool = "my-tool", args = mapOf("k" to "v"), mcpServerId = 3L)
        assertEquals("my-tool", req.tool)
        assertEquals(mapOf("k" to "v"), req.args)
        assertEquals(3L, req.mcpServerId)
    }
}
