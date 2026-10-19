package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.jira.JiraTicketFetchPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareAnalysisAdaptersTest {
    private val objectMapper = ObjectMapper().apply { findAndRegisterModules() }

    private fun activeMcp(jiraBaseUrl: String = "http://localhost:8081") =
        McpServerConfig(
            id = 1L,
            active = true,
            routeJira = true,
            jiraBaseUrl = jiraBaseUrl,
            jiraApiToken = "token",
        )

    private fun jiraConfig(baseUrl: String = "http://localhost:8081") = JiraConfig(id = 1L, name = "Demo", baseUrl = baseUrl, apiToken = "tok")

    private fun stubTicket(key: String = "PROJ-1") =
        JiraTicketData(
            key = key,
            summary = "stub",
            description = "",
            comments = "",
            issueType = "Bug",
            status = "Open",
            priority = null,
            labels = emptyList(),
            components = emptyList(),
            url = "http://jira/browse/$key",
        )

    @Test
    fun `delegates to native adapter when MCP is not active`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraTicketFetchPort = mock()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        val expected = stubTicket()
        whenever(delegate.fetchTicketByKey(any(), any())).thenReturn(expected)

        val adapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, objectMapper, delegate)
        adapter.fetchTicketByKey(jiraConfig(), "PROJ-1")

        verify(delegate).fetchTicketByKey(any(), eq("PROJ-1"))
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `delegates to native adapter when config URL does not match MCP jiraBaseUrl`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraTicketFetchPort = mock()

        val mcp = activeMcp(jiraBaseUrl = "http://localhost:8081")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        val expected = stubTicket("ACME-99999")
        whenever(delegate.fetchTicketByKey(any(), any())).thenReturn(expected)

        val adapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, objectMapper, delegate)
        val config = jiraConfig(baseUrl = "https://acme.atlassian.net")
        val result = adapter.fetchTicketByKey(config, "ACME-99999")

        verify(delegate).fetchTicketByKey(eq(config), eq("ACME-99999"))
        verify(gateway, never()).callTool(any(), any(), any())
        assert(result.key == "ACME-99999")
    }

    @Test
    fun `routes through MCP when config URL matches MCP jiraBaseUrl`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraTicketFetchPort = mock()

        val mcp = activeMcp(jiraBaseUrl = "http://localhost:8081")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        val issueJson =
            """
            {
              "key": "DEMO-1",
              "fields": {
                "summary": "Demo ticket",
                "description": "desc",
                "issuetype": {"name": "Bug"},
                "status": {"name": "Open"},
                "priority": {"name": "High"},
                "labels": [],
                "components": [],
                "comment": {"comments": []}
              }
            }
            """.trimIndent()
        whenever(gateway.callTool(eq(mcp), eq("jira_get_issue"), any()))
            .thenReturn(McpToolResult(text = issueJson, isError = false))

        val adapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, objectMapper, delegate)
        val config = jiraConfig(baseUrl = "http://localhost:8081")
        val result = adapter.fetchTicketByKey(config, "DEMO-1")

        verify(gateway).callTool(eq(mcp), eq("jira_get_issue"), eq(mapOf("key" to "DEMO-1")))
        verify(delegate, never()).fetchTicketByKey(any(), any())
        assert(result.key == "DEMO-1")
        assert(result.summary == "Demo ticket")
    }

    @Test
    fun `routes acceptance criteria extraction from the MCP description through the shared extractor`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraTicketFetchPort = mock()

        val mcp = activeMcp(jiraBaseUrl = "http://localhost:8081")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        val issueJson =
            """
            {
              "key": "DEMO-2",
              "fields": {
                "summary": "Demo ticket",
                "description": "Context line\nAcceptance Criteria:\n- first criterion\n- second criterion",
                "issuetype": {"name": "Bug"},
                "status": {"name": "Open"},
                "labels": [],
                "components": [],
                "comment": {"comments": []}
              }
            }
            """.trimIndent()
        whenever(gateway.callTool(eq(mcp), eq("jira_get_issue"), any()))
            .thenReturn(McpToolResult(text = issueJson, isError = false))

        val adapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, objectMapper, delegate)
        val result = adapter.fetchTicketByKey(jiraConfig(baseUrl = "http://localhost:8081"), "DEMO-2")

        assertEquals(listOf("first criterion", "second criterion"), result.acceptanceCriteria)
    }

    @Test
    fun `URL matching is case-insensitive and strips trailing slash`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraTicketFetchPort = mock()

        val mcp = activeMcp(jiraBaseUrl = "http://localhost:8081/")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        val issueJson =
            "{\"key\":\"X-1\",\"fields\":{\"summary\":\"s\",\"issuetype\":{\"name\":\"\"}," +
                "\"status\":{\"name\":\"\"},\"labels\":[],\"components\":[],\"comment\":{\"comments\":[]}}}"
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(text = issueJson, isError = false))

        val adapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, objectMapper, delegate)
        adapter.fetchTicketByKey(jiraConfig(baseUrl = "HTTP://LOCALHOST:8081"), "X-1")

        verify(gateway).callTool(any(), eq("jira_get_issue"), any())
        verify(delegate, never()).fetchTicketByKey(any(), any())
    }

    @Test
    fun `falls back to native adapter when MCP gateway returns error`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraTicketFetchPort = mock()

        val mcp = activeMcp()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(text = "HTTP 404 ...", isError = true))
        whenever(delegate.fetchTicketByKey(any(), any())).thenReturn(stubTicket())

        val adapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, objectMapper, delegate)
        adapter.fetchTicketByKey(jiraConfig(), "PROJ-1")

        verify(delegate).fetchTicketByKey(any(), eq("PROJ-1"))
    }

    @Test
    fun `falls back to native adapter when MCP gateway throws`() {
        val mcpRepo: McpServerConfigPort = mock()
        val gateway: McpGatewayPort = mock()
        val delegate: JiraTicketFetchPort = mock()

        val mcp = activeMcp()
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(gateway.callTool(any(), any(), any()))
            .thenThrow(RuntimeException("connection refused"))
        whenever(delegate.fetchTicketByKey(any(), any())).thenReturn(stubTicket())

        val adapter = McpAwareJiraTicketFetchAdapter(mcpRepo, gateway, objectMapper, delegate)
        adapter.fetchTicketByKey(jiraConfig(), "PROJ-1")

        verify(delegate).fetchTicketByKey(any(), eq("PROJ-1"))
    }
}
