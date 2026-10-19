package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.TicketRefinementTicket
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareTicketRefinementJiraAdapterFullTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    private class RecordingDelegate : TicketRefinementJiraPort {
        var postCommentResult: String = ""
        var fetchParentResult: List<TicketRefinementTicket> = emptyList()
        var renderAdfResult: String? = null

        override fun fetchParentWithLinkedTickets(
            config: com.stacklens.domain.model.JiraConfig?,
            parentKey: String,
        ): List<TicketRefinementTicket> = fetchParentResult

        override fun postComment(
            config: com.stacklens.domain.model.JiraConfig?,
            ticketKey: String,
            body: String,
            format: String,
            fallbackMarkdown: String,
            greeting: String,
            openPointsLabel: String,
            openPoints: List<String>,
        ): String = postCommentResult

        override fun renderAdfToHtml(adfJson: String?): String? = renderAdfResult
    }

    private val delegate = RecordingDelegate()

    private fun adapter() =
        McpAwareTicketRefinementJiraAdapter(
            mcpRepo = mcpRepo,
            gateway = gateway,
            objectMapper = objectMapper,
            delegate = delegate,
        )

    private fun activeMcp(): McpServerConfig =
        McpServerConfig(
            id = 1L,
            name = "test",
            baseUrl = "http://localhost:8080",
            active = true,
            routeJira = true,
            jiraBaseUrl = "https://jira.example.com",
            jiraApiToken = "tok",
        )

    @Test
    fun `fetchParentWithLinkedTickets delegates when MCP not active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        delegate.fetchParentResult = emptyList()
        val result = adapter().fetchParentWithLinkedTickets(null, "PROJ-1")
        assertEquals(0, result.size)
    }

    @Test
    fun `fetchParentWithLinkedTickets uses MCP when active and returns parent + children`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())

        val parentJson =
            """{"key":"PROJ-1","fields":{"summary":"Parent","description":"see PROJ-99","subtasks":[{"key":"PROJ-2","fields":{"summary":"Sub 2"}}],"issuelinks":[{"inwardIssue":{"key":"PROJ-3"}},{"outwardIssue":{"key":"PROJ-4"}}]}}"""
        val child2Json = """{"key":"PROJ-2","fields":{"summary":"Sub 2","subtasks":[],"issuelinks":[]}}"""
        val child3Json = """{"key":"PROJ-3","fields":{"summary":"Linked 3","subtasks":[],"issuelinks":[]}}"""
        val child4Json = """{"key":"PROJ-4","fields":{"summary":"Linked 4","subtasks":[],"issuelinks":[]}}"""
        val mentionedJson = """{"key":"PROJ-99","fields":{"summary":"Mentioned","subtasks":[],"issuelinks":[]}}"""

        var callIndex = 0
        val responses = listOf(parentJson, parentJson, child2Json, child3Json, child4Json, mentionedJson)
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            McpToolResult(text = responses[callIndex - 1], isError = false)
        }

        val result = adapter().fetchParentWithLinkedTickets(null, "PROJ-1")
        assertEquals(5, result.size)
        assertEquals("PROJ-1", result[0].key)
        assertEquals("PROJ-2", result[1].key)
        assertEquals("PROJ-3", result[2].key)
        assertEquals("PROJ-4", result[3].key)
        assertEquals("PROJ-99", result[4].key)
    }

    @Test
    fun `fetchParentWithLinkedTickets skips parent key in children`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        val parentJson =
            """{"key":"PROJ-1","fields":{"summary":"PROJ-1 mentions itself","subtasks":[],"issuelinks":[]}}"""
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            McpToolResult(text = parentJson, isError = false)
        }
        val result = adapter().fetchParentWithLinkedTickets(null, "PROJ-1")
        assertEquals(1, result.size)
        assertEquals("PROJ-1", result[0].key)
    }

    @Test
    fun `fetchParentWithLinkedTickets falls back to delegate on MCP error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "mcp failed", isError = true),
        )
        delegate.fetchParentResult = emptyList()
        val result = adapter().fetchParentWithLinkedTickets(null, "PROJ-1")
        assertEquals(0, result.size)
    }

    @Test
    fun `fetchParentWithLinkedTickets falls back to delegate on exception`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenThrow(RuntimeException("mcp error"))
        delegate.fetchParentResult = emptyList()
        val result = adapter().fetchParentWithLinkedTickets(null, "PROJ-1")
        assertEquals(0, result.size)
    }

    @Test
    fun `postComment delegates when MCP not active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        delegate.postCommentResult = "native-id"
        val id = adapter().postComment(null, "PROJ-1", "body", "wiki", "md-fallback")
        assertEquals("native-id", id)
    }

    @Test
    fun `postComment uses MCP with markdown body when format is markdown`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"id":"mcp-123"}""", isError = false),
        )
        val id = adapter().postComment(null, "PROJ-1", "**bold**", "markdown", "ignored-fallback")
        assertEquals("mcp-123", id)
    }

    @Test
    fun `postComment uses MCP with fallbackMarkdown body when format is not markdown`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"id":"mcp-456"}""", isError = false),
        )
        val id = adapter().postComment(null, "PROJ-1", "wiki-body", "wiki", "md-fallback-text")
        assertEquals("mcp-456", id)
    }

    @Test
    fun `postComment returns empty string when MCP response has no id`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"other":"value"}""", isError = false),
        )
        val id = adapter().postComment(null, "PROJ-1", "body", "markdown", "fb")
        assertEquals("", id)
    }

    @Test
    fun `postComment falls back to delegate when MCP returns error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "error", isError = true),
        )
        delegate.postCommentResult = "native-id"
        val id = adapter().postComment(null, "PROJ-1", "body", "markdown", "fb")
        assertEquals("native-id", id)
    }

    @Test
    fun `postComment falls back to delegate when MCP throws`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenThrow(RuntimeException("boom"))
        delegate.postCommentResult = "native-id"
        val id = adapter().postComment(null, "PROJ-1", "body", "markdown", "fb")
        assertEquals("native-id", id)
    }

    @Test
    fun `renderAdfToHtml delegates regardless of MCP state`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        delegate.renderAdfResult = "<p>html</p>"
        val html = adapter().renderAdfToHtml("""{"type":"doc"}""")
        assertEquals("<p>html</p>", html)
    }

    @Test
    fun `renderAdfToHtml delegates when MCP not active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        delegate.renderAdfResult = null
        val html = adapter().renderAdfToHtml(null)
        assertEquals(null, html)
    }

    @Test
    fun `fetchParentWithLinkedTickets handles missing description gracefully`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        val json = """{"key":"PROJ-1","fields":{"summary":"No desc","subtasks":[],"issuelinks":[]}}"""
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            McpToolResult(text = json, isError = false)
        }
        val result = adapter().fetchParentWithLinkedTickets(null, "PROJ-1")
        assertEquals(1, result.size)
        assertEquals("", result[0].description)
    }

    @Test
    fun `fetchParentWithLinkedTickets handles object description as ADF`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        val json =
            """{"key":"PROJ-1","fields":{"summary":"ADF","description":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"hello"}]}]},"subtasks":[],"issuelinks":[]}}"""
        var callIndex = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            callIndex++
            McpToolResult(text = json, isError = false)
        }
        val result = adapter().fetchParentWithLinkedTickets(null, "PROJ-1")
        assertEquals(1, result.size)
        assertTrue(result[0].description.contains("hello"))
    }

    @Test
    fun `fetchParentWithLinkedTickets sets URL when config has baseUrl`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        val json = """{"key":"PROJ-1","fields":{"summary":"s","subtasks":[],"issuelinks":[]}}"""
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(text = json, isError = false))
        val config =
            com.stacklens.domain.model.JiraConfig(
                id = 1L,
                name = "test",
                baseUrl = "https://jira.example.com",
                apiToken = "tok",
            )
        val result = adapter().fetchParentWithLinkedTickets(config, "PROJ-1")
        assertEquals("https://jira.example.com/browse/PROJ-1", result[0].url)
    }

    @Test
    fun `fetchParentWithLinkedTickets has empty URL when no config`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        val json = """{"key":"PROJ-1","fields":{"summary":"s","subtasks":[],"issuelinks":[]}}"""
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(text = json, isError = false))
        val result = adapter().fetchParentWithLinkedTickets(null, "PROJ-1")
        assertEquals("", result[0].url)
    }

    @Test
    fun `fetchParentWithLinkedTickets parses subtasks with key and summary`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        val json =
            """{"key":"PROJ-1","fields":{"summary":"Parent","subtasks":[{"key":"SUB-1","fields":{"summary":"First sub"}},{"key":"SUB-2","fields":{"summary":"Second sub"}},{"key":""}],"issuelinks":[]}}"""
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(text = json, isError = false))
        val result = adapter().fetchParentWithLinkedTickets(null, "PROJ-1")
        assertEquals(3, result.size)
        assertEquals("SUB-1", result[1].subtasks[0].key)
        assertEquals("SUB-2", result[1].subtasks[1].key)
    }

    @Test
    fun `fetchParentWithLinkedTickets handles invalid JSON in MCP response`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(text = "not json", isError = false))
        delegate.fetchParentResult = emptyList()
        val result = adapter().fetchParentWithLinkedTickets(null, "PROJ-1")
        assertEquals(0, result.size)
    }

    @Test
    fun `postComment forwards the upstream body to MCP for unknown format`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"id":"500"}""", isError = false),
        )
        val result = adapter().postComment(null, "PROJ-1", "raw body from caller", "unknown", "**fallback markdown**")
        assertEquals("500", result)
        val captor = org.mockito.kotlin.argumentCaptor<Map<String, Any?>>()
        org.mockito.kotlin.verify(gateway).callTool(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            captor.capture(),
        )
        assertEquals(
            "raw body from caller",
            captor.firstValue["body"],
            "caller's `body` must reach MCP, not the markdown fallback",
        )
    }

    @Test
    fun `postComment uses MCP with body when format is markdown`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcp())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"id":"501"}""", isError = false),
        )
        val result = adapter().postComment(null, "PROJ-1", "**bold**", "markdown", "**fb**")
        assertEquals("501", result)
        val captor = org.mockito.kotlin.argumentCaptor<Map<String, Any?>>()
        org.mockito.kotlin.verify(gateway).callTool(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            captor.capture(),
        )
        assertEquals("**bold**", captor.firstValue["body"])
    }
}
