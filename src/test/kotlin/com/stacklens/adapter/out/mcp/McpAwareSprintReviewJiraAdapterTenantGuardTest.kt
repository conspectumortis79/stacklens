package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.SprintReviewAdfDocument
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.SprintReviewTicket
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

class McpAwareSprintReviewJiraAdapterTenantGuardTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val objectMapper = ObjectMapper()
    private val delegate: SprintReviewJiraPort = mock()

    private fun adapter() =
        McpAwareSprintReviewJiraAdapter(
            mcpRepo = mcpRepo,
            gateway = gateway,
            objectMapper = objectMapper,
            delegate = delegate,
        )

    private fun bananaConfig() =
        JiraConfig(
            id = 7L,
            name = "banana",
            baseUrl = "https://banana.atlassian.net",
            apiToken = "tok",
            cloudMode = true,
        )

    private fun mcpServerWithDemoJira() =
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

    private fun ticket(key: String = "PROJ-1") =
        SprintReviewTicket(
            key = key,
            summary = "Sum $key",
            description = "Desc $key",
            url = "https://example.atlassian.net/browse/$key",
            issueType = "Story",
            status = "Open",
        )

    @Test
    fun `fetchSprintTickets skips MCP when active MCP targets a different Jira tenant than the selected JiraConfig`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpServerWithDemoJira())
        val result = adapter().fetchSprintTickets(bananaConfig(), sprintId = 30027)

        verify(gateway, never()).callTool(any(), any(), any())
        verify(delegate).fetchSprintTickets(any(), eq(30027L))
        assertEquals(0, result.size, "native adapter returned an empty list — that's fine for the guard assertion")
    }

    @Test
    fun `fetchSprintTickets uses MCP when active MCP matches the selected JiraConfig`() {
        val matchingMcp =
            mcpServerWithDemoJira().apply {
                jiraBaseUrl = "https://banana.atlassian.net"
                jiraCloudMode = true
                jiraApiToken = "real-token"
            }
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(matchingMcp)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            com.stacklens.application.port.out.McpToolResult(
                text = """{"issues":[{"key":"FA-1","fields":{"summary":"x","issuetype":{"name":"Story"},"status":{"name":"Open"},"labels":[]}}],"total":1}""",
                isError = false,
            ),
        )

        adapter().fetchSprintTickets(bananaConfig(), sprintId = 30027)

        verify(delegate, never()).fetchSprintTickets(any(), any())
        verify(gateway).callTool(any(), any(), any())
    }

    @Test
    fun `fetchSprintTickets falls back to native when there is no active MCP server`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.fetchSprintTickets(any(), any())).thenReturn(listOf(ticket()))

        adapter().fetchSprintTickets(bananaConfig(), sprintId = 30027)

        verify(gateway, never()).callTool(any(), any(), any())
        verify(delegate).fetchSprintTickets(any(), eq(30027L))
    }

    @Test
    fun `appendAdfToDescription skips MCP when active MCP targets a different Jira tenant`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpServerWithDemoJira())
        whenever(delegate.appendAdfToDescription(any(), any(), any())).thenReturn("ok")

        val result =
            adapter().appendAdfToDescription(
                config = bananaConfig(),
                targetKey = "FA-1",
                adfDocument = SprintReviewAdfDocument("""{"type":"doc","version":1,"content":[]}"""),
            )

        verify(gateway, never()).callTool(any(), any(), any())
        verify(delegate).appendAdfToDescription(any(), eq("FA-1"), any())
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
                text = """{"issues":[],"total":0}""",
                isError = false,
            ),
        )

        adapter().fetchSprintTickets(bananaConfig(), sprintId = 1L)
        verify(gateway).callTool(any(), any(), any())
        verify(delegate, never()).fetchSprintTickets(any(), any())
        assertTrue(true)
    }
}
