package com.stacklens.adapter.inbound.rest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpApiDtosTest {
    @Test
    fun `McpToolCallRequest default constructor covers all defaults`() {
        val r = McpToolCallRequest()
        assertEquals("", r.tool)
        assertTrue(r.args.isEmpty())
        assertNull(r.mcpServerId)
    }

    @Test
    fun `McpToolCallRequest with values`() {
        val r = McpToolCallRequest(tool = "search", args = mapOf("q" to "x"), mcpServerId = 1L)
        assertEquals("search", r.tool)
        assertEquals("x", r.args["q"])
        assertEquals(1L, r.mcpServerId)
    }

    @Test
    fun `McpToolCallResponse holds values`() {
        val r = McpToolCallResponse(ok = true, text = "done", tool = "search")
        assertEquals(true, r.ok)
        assertEquals("done", r.text)
        assertEquals("search", r.tool)
    }

    @Test
    fun `McpServerConfigDto default constructor covers all defaults`() {
        val d = McpServerConfigDto()
        assertEquals("", d.name)
        assertEquals(false, d.active)
        assertEquals(true, d.routeJira)
        assertEquals("https://gitlab.com", d.gitlabBaseUrl)
        assertEquals("https://api.github.com", d.githubApiBase)
        assertEquals(true, d.jiraCloudMode)
        assertEquals(true, d.confluenceCloudMode)
        assertEquals(false, d.autoStartManaged)
    }
}
