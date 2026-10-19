package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
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
import org.mockito.kotlin.whenever

class McpAwareTicketRefinementJiraAdapterGraphTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val objectMapper = ObjectMapper()

    private class FakeDelegate(
        var fetchResult: List<TicketRefinementTicket> = emptyList(),
    ) : TicketRefinementJiraPort {
        override fun fetchParentWithLinkedTickets(
            config: JiraConfig?,
            parentKey: String,
        ): List<TicketRefinementTicket> = fetchResult

        override fun postComment(
            config: JiraConfig?,
            ticketKey: String,
            body: String,
            format: String,
            fallbackMarkdown: String,
            greeting: String,
            openPointsLabel: String,
            openPoints: List<String>,
        ): String = ""

        override fun renderAdfToHtml(adfJson: String?): String? = null
    }

    private val delegate = FakeDelegate()

    private fun adapter() =
        McpAwareTicketRefinementJiraAdapter(
            mcpRepo = mcpRepo,
            gateway = gateway,
            objectMapper = objectMapper,
            delegate = delegate,
        )

    private fun mcp() =
        McpServerConfig(
            active = true,
            routeJira = true,
            jiraBaseUrl = "http://jira",
            jiraApiToken = "TOK",
            routeGitlab = true,
            gitlabBaseUrl = "http://gl",
            gitlabAccessToken = "TOK",
            routeGithub = true,
            routeConfluence = true,
            confluenceBaseUrl = "http://conf",
            confluenceApiToken = "TOK",
        )

    private fun cfg() = JiraConfig(name = "j", baseUrl = "http://jira", apiToken = "TOK")

    private fun ticket(key: String) =
        TicketRefinementTicket(
            key = key,
            summary = "sum",
            description = "desc",
            url = "u/$key",
        )

    @Test
    fun `fetchParentWithLinkedTickets handles issuelinks array with inward and outward issues`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp())
        val parentJson =
            """
            {"fields":{"issuelinks":[
                {"inwardIssue":{"key":"INWARD-1"},"outwardIssue":{}},
                {"inwardIssue":{},"outwardIssue":{"key":"OUTWARD-1"}}
            ],"subtasks":[]}}
            """.trimIndent()
        whenever(gateway.callTool(any(), eq("jira_get_issue"), any()))
            .thenReturn(McpToolResult(isError = false, text = parentJson))
        delegate.fetchResult = listOf(ticket("PARENT-1"))
        val results = adapter().fetchParentWithLinkedTickets(cfg(), "PARENT-1")
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `fetchParentWithLinkedTickets handles subtasks array`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp())
        val parentJson =
            """
            {"fields":{"issuelinks":[],"subtasks":[
                {"key":"SUB-1"},
                {"key":"SUB-2"}
            ]}}
            """.trimIndent()
        whenever(gateway.callTool(any(), eq("jira_get_issue"), any()))
            .thenReturn(McpToolResult(isError = false, text = parentJson))
        delegate.fetchResult = listOf(ticket("PARENT-1"))
        val results = adapter().fetchParentWithLinkedTickets(cfg(), "PARENT-1")
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `fetchParentWithLinkedTickets handles gateway isError response`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp())
        whenever(gateway.callTool(any(), eq("jira_get_issue"), any()))
            .thenReturn(McpToolResult(isError = true, text = "err"))
        delegate.fetchResult = listOf(ticket("PARENT-1"))
        val results = adapter().fetchParentWithLinkedTickets(cfg(), "PARENT-1")
        assertEquals(1, results.size)
    }

    @Test
    fun `fetchParentWithLinkedTickets handles invalid JSON gracefully`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp())
        whenever(gateway.callTool(any(), eq("jira_get_issue"), any()))
            .thenReturn(McpToolResult(isError = false, text = "not-json"))
        delegate.fetchResult = listOf(ticket("PARENT-1"))
        val results = adapter().fetchParentWithLinkedTickets(cfg(), "PARENT-1")
        assertEquals(1, results.size)
    }

    @Test
    fun `fetchParentWithLinkedTickets handles missing fields path`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp())
        whenever(gateway.callTool(any(), eq("jira_get_issue"), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"id":"1"}"""))
        delegate.fetchResult = listOf(ticket("PARENT-1"))
        val results = adapter().fetchParentWithLinkedTickets(cfg(), "PARENT-1")
        assertEquals(1, results.size)
    }
}
