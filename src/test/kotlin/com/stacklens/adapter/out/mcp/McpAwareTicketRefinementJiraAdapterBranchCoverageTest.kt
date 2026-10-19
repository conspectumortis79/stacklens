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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class McpAwareTicketRefinementJiraAdapterBranchCoverageTest {
    private val mcpRepo = mock<McpServerConfigPort>()

    private val gateway = mock<McpGatewayPort>()

    private val delegate = mock<TicketRefinementJiraPort>()

    private val mapper = ObjectMapper()

    private val adapter by lazy { McpAwareTicketRefinementJiraAdapter(mcpRepo, gateway, mapper, delegate) }

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
            jiraBaseUrl = "https://jira.example.com/",
            jiraApiToken = "t",
        )

    private val fallback =
        TicketRefinementTicket(
            key = "P-1",
            summary = "fallback",
            description = "D",
            url = "https://jira.example.com/browse/P-1",
        )

    @BeforeEach
    fun routeToMcp() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    private fun answerFor(results: Map<String, McpToolResult>) {
        whenever(gateway.callTool(any(), eq("jira_get_issue"), any())).thenAnswer { call ->
            @Suppress("UNCHECKED_CAST")
            val key = (call.arguments[2] as Map<String, Any>)["key"] as String
            results[key] ?: throw IllegalStateException("unexpected lookup $key")
        }
    }

    private fun issue(
        key: String,
        summary: String,
        descriptionJson: String,
        extraFields: String = "",
    ): McpToolResult =
        McpToolResult(
            """{"key":"$key","fields":{"summary":"$summary","description":$descriptionJson$extraFields}}""",
        )

    @Test
    fun `linked tickets are collected from issue links, subtasks and mentions`() {
        val parent =
            issue(
                key = "P-1",
                summary = "relates PROJ-4",
                descriptionJson = "\"also P-4 again\"",
                extraFields =
                    ""","issuelinks":[{"inwardIssue":{"key":"P-3"},"outwardIssue":{"key":"  "}},""" +
                        """{"outwardIssue":{"key":"P-5"}}],"subtasks":[{"key":"P-2"},{"key":""}]}""",
            )
        answerFor(
            mapOf(
                "P-1" to parent,
                "P-2" to issue("P-2", "sub two", "\"text body\""),
                "P-3" to issue("P-3", "linked three", """{"type":"doc","content":["deep"]}"""),
                "PROJ-4" to McpToolResult("boom", isError = true),
                "P-5" to McpToolResult("not json at all"),
            ),
        )

        val tickets = adapter.fetchParentWithLinkedTickets(cfg, "P-1")

        assertEquals(listOf("P-1", "P-3", "P-2"), tickets.map { it.key })
        assertEquals("deep", tickets[1].description.split(" ").last())
        assertTrue(tickets.all { it.url.endsWith("/browse/${it.key}") })
    }

    @Test
    fun `a missing node structure keeps only the parent`() {
        answerFor(mapOf("P-1" to issue("P-1", "no links", "\"plain\"")))

        assertEquals(listOf("P-1"), adapter.fetchParentWithLinkedTickets(cfg, "P-1").map { it.key })
    }

    @Test
    fun `empty link and subtask collections keep the parent alone`() {
        answerFor(
            mapOf(
                "P-1" to
                    issue(
                        key = "P-1",
                        summary = "nothing linked",
                        descriptionJson = "\"no mentions\"",
                        extraFields = ""","issuelinks":[],"subtasks":[]}""",
                    ),
            ),
        )

        assertEquals(listOf("P-1"), adapter.fetchParentWithLinkedTickets(cfg, "P-1").map { it.key })
    }

    @Test
    fun `a broken parent node answer keeps the parent without any links`() {
        var nodeCalls = 0
        whenever(gateway.callTool(any(), eq("jira_get_issue"), any())).thenAnswer { call ->
            @Suppress("UNCHECKED_CAST")
            val key = (call.arguments[2] as Map<String, Any>)["key"] as String
            when {
                key != "P-1" -> issue(key, "child", "\"body\"")
                nodeCalls++ == 0 -> issue("P-1", "links PROJ-7", "\"mention PROJ-7\"")
                else -> McpToolResult("mcp returned an error", isError = true)
            }
        }

        assertEquals(listOf("P-1", "PROJ-7"), adapter.fetchParentWithLinkedTickets(cfg, "P-1").map { it.key })
    }

    @Test
    fun `routing that disappears while fetching falls back to the direct adapter`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig())
            .thenReturn(mcpCfg)
            .thenReturn(null)
        whenever(delegate.fetchParentWithLinkedTickets(cfg, "P-1")).thenReturn(listOf(fallback))
        answerFor(mapOf("P-1" to issue("P-1", "parent", "\"text\"")))

        assertEquals(listOf(fallback), adapter.fetchParentWithLinkedTickets(cfg, "P-1"))
    }

    @Test
    fun `transport errors during the parent lookup fall back to the direct adapter`() {
        whenever(delegate.fetchParentWithLinkedTickets(cfg, "P-1")).thenReturn(listOf(fallback))
        whenever(gateway.callTool(any(), any(), any())).thenThrow(IllegalStateException("mcp offline"))

        assertEquals(listOf(fallback), adapter.fetchParentWithLinkedTickets(cfg, "P-1"))
    }

    @Test
    fun `a mismatching jira base url uses the direct adapter`() {
        whenever(delegate.fetchParentWithLinkedTickets(cfg, "P-1")).thenReturn(listOf(fallback))
        whenever(mcpRepo.findFirstActiveMcpServerConfig())
            .thenReturn(
                McpServerConfig(
                    active = true,
                    routeJira = true,
                    jiraBaseUrl = "https://other.example.com",
                    jiraApiToken = "t",
                ),
            )

        assertEquals(listOf(fallback), adapter.fetchParentWithLinkedTickets(cfg, "P-1"))
        verifyNoInteractions(gateway)
    }

    @Test
    fun `comments use the identifier answered by the mcp server`() {
        whenever(gateway.callTool(any(), eq("jira_add_comment"), any())).thenReturn(McpToolResult("""{"id":"comment-4"}"""))
        assertEquals("comment-4", adapter.postComment(cfg, "P-1", "body", "markdown", "fallback"))

        whenever(gateway.callTool(any(), eq("jira_add_comment"), any())).thenReturn(McpToolResult("broken json"))
        assertEquals("", adapter.postComment(cfg, "P-1", "body", "markdown", "fallback"))

        whenever(gateway.callTool(any(), eq("jira_add_comment"), any())).thenReturn(McpToolResult("rejected", isError = true))
        whenever(delegate.postComment(cfg, "P-1", "body", "markdown", "fallback")).thenReturn("native")
        assertEquals("native", adapter.postComment(cfg, "P-1", "body", "markdown", "fallback"))

        whenever(gateway.callTool(any(), eq("jira_add_comment"), any())).thenThrow(IllegalStateException("timeout"))
        assertEquals("native", adapter.postComment(cfg, "P-1", "body", "markdown", "fallback"))
    }

    @Test
    fun `comment and rendering calls without active routing go straight through`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.postComment(cfg, "P-1", "body", "markdown", "fallback")).thenReturn("native")
        whenever(delegate.renderAdfToHtml(any())).thenReturn("<p>adf</p>")

        assertEquals("native", adapter.postComment(cfg, "P-1", "body", "markdown", "fallback"))
        assertEquals("<p>adf</p>", adapter.renderAdfToHtml("{}"))
    }
}
