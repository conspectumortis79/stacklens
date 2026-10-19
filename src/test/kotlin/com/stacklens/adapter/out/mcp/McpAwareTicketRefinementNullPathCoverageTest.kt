package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.TextNode
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.TicketRefinementTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareTicketRefinementNullPathCoverageTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val objectMapper: ObjectMapper = mock()
    private val delegate: TicketRefinementJiraPort = mock()

    private val adapter = McpAwareTicketRefinementJiraAdapter(mcpRepo, gateway, objectMapper, delegate)

    private val parent =
        TicketRefinementTicket(
            key = "PARENT-1",
            summary = "sum",
            description = "desc",
            url = "https://jira.example.com/browse/PARENT-1",
        )

    private fun fetchWith(rawParentNode: JsonNode): List<TicketRefinementTicket> {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(
            McpServerConfig(
                active = true,
                routeJira = true,
                jiraBaseUrl = "https://jira.example.com",
                jiraUsername = "u",
                jiraApiToken = "t",
            ),
        )
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = false, text = "{}"))
        whenever(delegate.fetchParentWithLinkedTickets(any(), any())).thenReturn(listOf(parent))
        val mapped = ticketNode()
        whenever(objectMapper.readTree(any<String>())).thenReturn(mapped, rawParentNode)

        return adapter.fetchParentWithLinkedTickets(
            JiraConfig(name = "jira", baseUrl = "https://jira.example.com", apiToken = "t"),
            "PARENT-1",
        )
    }

    private fun ticketNode(): JsonNode {
        val fields = mock<JsonNode>()
        whenever(fields.path("summary")).thenReturn(TextNode("sum"))
        whenever(fields.path("description")).thenReturn(MissingNode.getInstance())
        whenever(fields.path("subtasks")).thenReturn(MissingNode.getInstance())
        val node = mock<JsonNode>()
        whenever(node.path("key")).thenReturn(TextNode("PARENT-1"))
        whenever(node.path("fields")).thenReturn(fields)
        return node
    }

    @Test
    fun `a fields document that reports null stops both link walks`() {
        val node = mock<JsonNode>()
        whenever(node.path("fields")).thenReturn(null)

        assertEquals(listOf("PARENT-1"), fetchWith(node).map { it.key })
    }

    @Test
    fun `a document whose issuelinks and subtasks are null stops the element walks`() {
        val fields = mock<JsonNode>()
        whenever(fields.path("issuelinks")).thenReturn(null)
        whenever(fields.path("subtasks")).thenReturn(null)
        val node = mock<JsonNode>()
        whenever(node.path("fields")).thenReturn(fields)

        assertEquals(listOf("PARENT-1"), fetchWith(node).map { it.key })
    }
}
