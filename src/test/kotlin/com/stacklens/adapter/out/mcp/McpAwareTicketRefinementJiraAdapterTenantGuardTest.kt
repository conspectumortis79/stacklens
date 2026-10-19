package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.TicketRefinementTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareTicketRefinementJiraAdapterTenantGuardTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val objectMapper = ObjectMapper()
    private val delegate: TicketRefinementJiraPort = mock()

    private fun adapter(): McpAwareTicketRefinementJiraAdapter =
        McpAwareTicketRefinementJiraAdapter(
            mcpRepo = mcpRepo,
            gateway = gateway,
            objectMapper = objectMapper,
            delegate = delegate,
        )

    private fun bananaConfig(): JiraConfig =
        JiraConfig(
            id = 7L,
            name = "banana",
            baseUrl = "https://banana.atlassian.net",
            apiToken = "tok",
            cloudMode = true,
        )

    private fun mcpServerWithDemoJira(): McpServerConfig =
        McpServerConfig(
            id = 1L,
            name = "StackLens MCP",
            baseUrl = "http://localhost:7456",
            active = true,
            routeJira = true,
            jiraBaseUrl = "http://localhost:8081",
            jiraApiToken = "demo-token",
            jiraCloudMode = false,
        )

    private fun ticket(key: String = "binit-1") =
        TicketRefinementTicket(
            key = key,
            summary = "Sum $key",
            description = "Desc $key",
            url = "https://example.atlassian.net/browse/$key",
        )

    @Test
    fun `fetchParentWithLinkedTickets skips MCP when active MCP targets a different Jira tenant`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpServerWithDemoJira())
        whenever(delegate.fetchParentWithLinkedTickets(any(), any())).thenReturn(listOf(ticket()))

        val result = adapter().fetchParentWithLinkedTickets(bananaConfig(), "binit-1")

        verify(gateway, never()).callTool(any(), any(), any())
        verify(delegate).fetchParentWithLinkedTickets(any(), eq("binit-1"))
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchParentWithLinkedTickets uses MCP when active MCP matches the selected JiraConfig`() {
        val matchingMcp =
            mcpServerWithDemoJira().apply {
                jiraBaseUrl = "https://banana.atlassian.net"
                jiraCloudMode = true
                jiraApiToken = "real-token"
            }
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(matchingMcp)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            com.stacklens.application.port.out.McpToolResult(
                text = """{"key":"FA-1","fields":{"summary":"x","issuelinks":[],"subtasks":[]}}""",
                isError = false,
            ),
        )

        adapter().fetchParentWithLinkedTickets(bananaConfig(), "FA-1")

        verify(delegate, never()).fetchParentWithLinkedTickets(any(), any())
        org.mockito.kotlin
            .verify(gateway, org.mockito.kotlin.atLeastOnce())
            .callTool(any(), any(), any())
    }

    @Test
    fun `fetchParentWithLinkedTickets falls back to native when there is no active MCP server`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.fetchParentWithLinkedTickets(any(), any())).thenReturn(listOf(ticket()))

        adapter().fetchParentWithLinkedTickets(bananaConfig(), "FA-1")

        verify(gateway, never()).callTool(any(), any(), any())
        verify(delegate).fetchParentWithLinkedTickets(any(), eq("FA-1"))
    }

    @Test
    fun `postComment skips MCP when active MCP targets a different Jira tenant`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpServerWithDemoJira())
        whenever(delegate.postComment(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn("ok")

        val result =
            adapter().postComment(
                config = bananaConfig(),
                ticketKey = "FA-1",
                body = "body",
                format = "markdown",
                fallbackMarkdown = "fb",
            )

        verify(gateway, never()).callTool(any(), any(), any())
        verify(delegate).postComment(any(), eq("FA-1"), eq("body"), eq("markdown"), eq("fb"), any(), any(), any())
        assertEquals("ok", result)
    }

    @Test
    fun `baseUrl comparison ignores trailing slashes and case differences`() {
        val matchingMcp =
            mcpServerWithDemoJira().apply {
                jiraBaseUrl = "https://Banana.Atlassian.Net/"
                jiraCloudMode = true
                jiraApiToken = "real-token"
            }
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(matchingMcp)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            com.stacklens.application.port.out.McpToolResult(
                text = """{"key":"FA-1","fields":{"summary":"x","issuelinks":[],"subtasks":[]}}""",
                isError = false,
            ),
        )

        adapter().fetchParentWithLinkedTickets(bananaConfig(), "FA-1")
        org.mockito.kotlin
            .verify(gateway, org.mockito.kotlin.atLeastOnce())
            .callTool(any(), any(), any())
        verify(delegate, never()).fetchParentWithLinkedTickets(any(), any())
        assertTrue(true)
    }
}
