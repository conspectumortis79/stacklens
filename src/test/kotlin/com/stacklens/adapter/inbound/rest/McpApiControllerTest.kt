package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.mcp.McpCallResult
import com.stacklens.application.port.inbound.mcp.McpManagementUseCase
import com.stacklens.application.port.inbound.mcp.McpStatusResult
import com.stacklens.application.port.inbound.mcp.McpToolCallRequest
import com.stacklens.application.port.inbound.mcp.McpToolSummary
import com.stacklens.application.port.inbound.mcp.McpToolsResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class McpApiControllerTest {
    private val mcpManagement = mock<McpManagementUseCase>()
    private val i18n = mock<com.stacklens.application.port.inbound.platform.I18nUseCase>()

    private fun newController(): McpApiController = McpApiController(mcpManagement, i18n)

    @Test
    fun `listTools returns 200 with the tool list when an active server exists`() {
        whenever(i18n.t(any<String>())).thenAnswer { "no-active-server-key" }
        val tools =
            listOf(
                McpToolSummary(name = "tool1", description = "d1"),
                McpToolSummary(name = "tool2", description = ""),
            )
        whenever(mcpManagement.listTools(null))
            .thenReturn(McpToolsResult(mcpServerId = 1L, mcpServerName = "n", tools = tools))
        val response = newController().listTools(null)
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(1L, body["mcpServerId"])
        assertEquals("n", body["mcpServerName"])
        @Suppress("UNCHECKED_CAST")
        val bodyTools = body["tools"] as List<Map<String, Any?>>
        assertEquals(2, bodyTools.size)
        assertEquals("tool1", bodyTools[0]["name"])
        assertEquals("d1", bodyTools[0]["description"])
        assertEquals("tool2", bodyTools[1]["name"])
        assertEquals("", bodyTools[1]["description"])
    }

    @Test
    fun `listTools forwards the optional mcpServerId parameter to the use case`() {
        whenever(mcpManagement.listTools(42L)).thenReturn(null)
        whenever(i18n.t(any<String>())).thenAnswer { "no-active-server-key" }
        newController().listTools(42L)
    }

    @Test
    fun `listTools returns 400 with the localized error when no active server is found`() {
        whenever(mcpManagement.listTools(null)).thenReturn(null)
        whenever(i18n.t(any<String>())).thenAnswer { "Es ist kein aktiver MCP-Server konfiguriert." }
        val response = newController().listTools(null)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Es ist kein aktiver MCP-Server konfiguriert.", response.body!!["error"])
    }

    @Test
    fun `call returns 400 with tool-required message when the request body has a blank tool`() {
        val response =
            newController().call(
                McpApiController.McpApiDtoRequest(tool = "   "),
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body!!
        assertEquals(false, body.ok)
        assertEquals("tool is required", body.text)
        assertEquals("", body.tool)
    }

    @Test
    fun `call forwards the request to the use case and returns 200 with the result`() {
        whenever(mcpManagement.callTool(any()))
            .thenReturn(McpCallResult(ok = true, text = "success", tool = "my-tool"))
        val response =
            newController().call(
                McpApiController.McpApiDtoRequest(tool = "my-tool", args = mapOf("k" to "v"), mcpServerId = 5L),
            )
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(true, body.ok)
        assertEquals("success", body.text)
        assertEquals("my-tool", body.tool)
    }

    @Test
    fun `call returns 400 when the use case reports no active server`() {
        whenever(i18n.t(any<String>())).thenAnswer { "kein Server" }
        whenever(mcpManagement.callTool(any()))
            .thenReturn(McpCallResult(ok = false, text = "kein Server", tool = "my-tool"))
        val response =
            newController().call(
                McpApiController.McpApiDtoRequest(tool = "my-tool"),
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(false, response.body!!.ok)
        assertEquals("kein Server", response.body!!.text)
    }

    @Test
    fun `call returns 200 even when the use case reports a non-active-server failure (other text)`() {
        whenever(i18n.t(any<String>())).thenAnswer { "kein Server" }
        whenever(mcpManagement.callTool(any()))
            .thenReturn(McpCallResult(ok = false, text = "internal error", tool = "my-tool"))
        val response =
            newController().call(
                McpApiController.McpApiDtoRequest(tool = "my-tool"),
            )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(false, response.body!!.ok)
        assertEquals("internal error", response.body!!.text)
    }

    @Test
    fun `status returns 200 with the status payload`() {
        val s =
            McpStatusResult(
                active = true,
                mcpServerName = "alpha",
                routes = mapOf("jira" to true, "gitlab" to false),
                processRunning = true,
            )
        whenever(mcpManagement.getStatus()).thenReturn(s)
        val response = newController().status()
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(true, body["active"])
        assertEquals("alpha", body["mcpServerName"])
        @Suppress("UNCHECKED_CAST")
        val routes = body["routes"] as Map<String, Boolean>
        assertEquals(true, routes["jira"])
        assertEquals(false, routes["gitlab"])
        assertEquals(true, body["processRunning"])
    }

    @Test
    fun `McpToolCallRequest is forwarded with the same mcpServerId, tool and args`() {
        whenever(mcpManagement.callTool(any()))
            .thenReturn(McpCallResult(ok = true, text = "ok", tool = "t"))
        newController().call(
            McpApiController.McpApiDtoRequest(tool = "t", args = mapOf("a" to 1), mcpServerId = 7L),
        )
        org.mockito.kotlin
            .verify(mcpManagement)
            .callTool(
                org.mockito.kotlin.refEq(
                    McpToolCallRequest(mcpServerId = 7L, tool = "t", args = mapOf("a" to 1)),
                ),
            )
    }
}
