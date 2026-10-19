package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.stacklens.adapter.out.jira.JiraTicketSummaryNativeJiraAdapter
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareJiraTicketSummaryAdapterTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val native: JiraTicketSummaryNativeJiraAdapter = mock()
    private val objectMapper = ObjectMapper().apply { registerKotlinModule() }

    private val sut =
        McpAwareJiraTicketSummaryAdapter(
            mcpRepo = mcpRepo,
            gateway = gateway,
            objectMapper = objectMapper,
            delegate = native,
        )

    @Test
    fun `fetchTicketWithComments delegates to the native adapter when no MCP route is active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)

        val nativeTicket =
            com.stacklens.application.port.out.JiraTicketSummaryTicket(
                key = "PROJ-1",
                summary = "native result",
                description = "",
                url = "https://example/browse/PROJ-1",
                status = "Open",
                issueType = "Bug",
                priority = "High",
                assignee = "alice",
                reporter = "bob",
                labels = emptyList(),
                createdAt = "2024-01-01",
                updatedAt = "2024-01-02",
                comments = emptyList(),
            )
        whenever(native.fetchTicketWithComments(any(), any())).thenReturn(nativeTicket)

        val result = sut.fetchTicketWithComments(jiraConfig(), "PROJ-1")

        assertThat(result).isEqualTo(nativeTicket)
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `fetchTicketWithComments calls the MCP tool when jira routing is active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcpConfig())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                """{"key":"PROJ-2","summary":"from MCP","description":"d","url":"u","status":"Open","issueType":"Story","priority":"Medium","assignee":"alice","reporter":"bob","labels":["a","b"],"createdAt":"2024-01-01","updatedAt":"2024-01-02","comments":[{"author":"alice","body":"hi","createdAt":"2024-01-03"}]}""",
            ),
        )

        val result = sut.fetchTicketWithComments(jiraConfig(), "PROJ-2")

        assertThat(result).isNotNull
        assertThat(result!!.key).isEqualTo("PROJ-2")
        assertThat(result.summary).isEqualTo("from MCP")
        assertThat(result.labels).containsExactly("a", "b")
        assertThat(result.comments).hasSize(1)
        verify(gateway).callTool(any(), any(), any())
    }

    @Test
    fun `fetchTicketWithComments falls back to native when the MCP tool errors`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(activeMcpConfig())
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult("ERROR", isError = true))
        whenever(native.fetchTicketWithComments(any(), any())).thenReturn(null)

        val result = sut.fetchTicketWithComments(jiraConfig(), "PROJ-3")

        assertThat(result).isNull()
        verify(native).fetchTicketWithComments(any(), any())
    }

    private fun jiraConfig(): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "j",
            baseUrl = "https://test.atlassian.net",
            username = "u",
            apiToken = "t",
            projectKey = "PROJ",
        )

    private fun activeMcpConfig(): McpServerConfig =
        McpServerConfig(
            id = 1L,
            name = "mcp",
            baseUrl = "http://mcp:7456",
            active = true,
            routeJira = true,
            jiraBaseUrl = "https://test.atlassian.net",
            jiraApiToken = "t",
        )
}
