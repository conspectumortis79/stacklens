package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.JiraProblemAnalysis
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.jira.JiraTicketFetchPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareJiraTicketFetchAdapterTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<JiraTicketFetchPort>()
    private val mapper = ObjectMapper()
    private lateinit var adapter: McpAwareJiraTicketFetchAdapter

    private val cfg =
        JiraConfig(
            name = "c",
            baseUrl = "https://jira.example.com",
            apiToken = "t",
            projectKey = "PROJ",
            username = "u",
        )
    private val mcpCfg =
        McpServerConfig(
            active = true,
            routeJira = true,
            jiraBaseUrl = "https://jira.example.com",
            jiraApiToken = "t",
        )

    private val fallbackTicket =
        JiraTicketData(
            key = "P-1",
            summary = "fb",
            description = "D",
            comments = "",
            issueType = "Bug",
            status = "Open",
            priority = null,
            labels = emptyList(),
            components = emptyList(),
            url = "u",
        )

    @BeforeEach
    fun setUp() {
        adapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    @Test
    fun `fetchTicketByKey routes via MCP and parses JSON with object description`() {
        val json = """{"key":"PROJ-1","fields":{"summary":"S","description":{"version":1,"content":"x"},"issuetype":{"name":"Bug"},"status":{"name":"Open"}}}"""
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = json),
        )
        val result = adapter.fetchTicketByKey(cfg, "PROJ-1")
        assertEquals("PROJ-1", result.key)
        assert(result.description.contains("version"))
    }

    @Test
    fun `fetchTicketByKey routes via MCP and parses JSON with missing description`() {
        val json = """{"key":"PROJ-2","fields":{"summary":"S","issuetype":{"name":"Bug"},"status":{"name":"Open"}}}"""
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = json),
        )
        val result = adapter.fetchTicketByKey(cfg, "PROJ-2")
        assertEquals("PROJ-2", result.key)
        assertEquals("", result.description)
    }

    @Test
    fun `fetchTicketByKey routes via MCP and parses JSON with object comment body`() {
        val json = """{"key":"PROJ-3","fields":{
            "summary":"S","description":"D","issuetype":{"name":"Bug"},"status":{"name":"Open"},
            "comment":{"comments":[{"author":{"displayName":"Alice"},"body":{"version":1,"type":"doc"}}]}
        }}"""
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = json),
        )
        val result = adapter.fetchTicketByKey(cfg, "PROJ-3")
        assertEquals("PROJ-3", result.key)
        assert(result.comments.contains("version"))
    }

    @Test
    fun `fetchTicketByKey routes via MCP and parses JSON with missing comment body`() {
        val json = """{"key":"PROJ-4","fields":{"summary":"S","description":"D","issuetype":{"name":"Bug"},"status":{"name":"Open"},"comment":{"comments":[{"author":{"displayName":"Alice"}}]}}}"""
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = json),
        )
        val result = adapter.fetchTicketByKey(cfg, "PROJ-4")
        assertEquals("PROJ-4", result.key)
    }

    @Test
    fun `fetchTicketByKey falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.fetchTicketByKey(cfg, "P-1")).thenReturn(fallbackTicket)
        val result = adapter.fetchTicketByKey(cfg, "P-1")
        assertEquals(fallbackTicket, result)
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `fetchTicketByKey falls back when MCP base url does not match`() {
        val otherCfg =
            JiraConfig(
                name = "c",
                baseUrl = "https://other.example.com",
                apiToken = "t",
                projectKey = "PROJ",
                username = "u",
            )
        whenever(delegate.fetchTicketByKey(otherCfg, "P-1")).thenReturn(fallbackTicket)
        val result = adapter.fetchTicketByKey(otherCfg, "P-1")
        assertEquals(fallbackTicket, result)
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `fetchTicketByKey routes via MCP and parses JSON`() {
        val json = """{"key":"PROJ-1","fields":{"summary":"S","description":"D","issuetype":{"name":"Bug"},"status":{"name":"Open"}}}"""
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = json),
        )
        val result = adapter.fetchTicketByKey(cfg, "PROJ-1")
        assertEquals("PROJ-1", result.key)
    }

    @Test
    fun `fetchTicketByKey falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.fetchTicketByKey(cfg, "P-1")).thenReturn(fallbackTicket)
        val result = adapter.fetchTicketByKey(cfg, "P-1")
        assertEquals(fallbackTicket, result)
    }

    @Test
    fun `fetchTicketByKey falls back when MCP returns invalid JSON`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = "not json"),
        )
        whenever(delegate.fetchTicketByKey(cfg, "P-1")).thenReturn(fallbackTicket)
        val result = adapter.fetchTicketByKey(cfg, "P-1")
        assertEquals(fallbackTicket, result)
    }

    @Test
    fun `fetchTicketByKey falls back when MCP gateway throws`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.fetchTicketByKey(cfg, "P-1")).thenReturn(fallbackTicket)
        val result = adapter.fetchTicketByKey(cfg, "P-1")
        assertEquals(fallbackTicket, result)
    }

    @Test
    fun `searchSimilarTickets always delegates`() {
        val problem =
            JiraProblemAnalysis(
                problemDescription = "x",
                rootCause = "y",
                affectedComponents = emptyList(),
                searchKeywords = emptyList(),
                errorMessages = emptyList(),
            )
        val ticket = fallbackTicket
        val expected = emptyList<com.stacklens.application.port.out.JiraSimilarTicket>()
        whenever(delegate.searchSimilarTickets(cfg, ticket, problem)).thenReturn(expected)
        val result = adapter.searchSimilarTickets(cfg, ticket, problem)
        assertEquals(expected, result)
        verify(gateway, never()).callTool(any(), any(), any())
    }
}

private fun assertTrue(condition: Boolean) {
    org.junit.jupiter.api.Assertions
        .assertTrue(condition)
}
