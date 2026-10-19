package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.shared.sprintIdFromRegex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareJiraSprintFetchAdapterTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<JiraSprintFetchPort>()
    private val mapper = ObjectMapper()
    private lateinit var adapter: McpAwareJiraSprintFetchAdapter

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

    private val fallbackSprint = SprintInfo(id = 1L, name = "fb", state = "active", startDate = null, endDate = null, goal = null)
    private val fallbackIssues =
        listOf(
            SprintIssue(
                key = "P-1",
                summary = "fb",
                description = "",
                issueType = "Task",
                status = "Open",
                priority = null,
                assignee = null,
                labels = emptyList(),
                comments = "",
                url = "https://jira/browse/P-1",
            ),
        )

    @BeforeEach
    fun setUp() {
        adapter = McpAwareJiraSprintFetchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    @Test
    fun `fetchSprintInfo falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.fetchSprintInfo(cfg, "x")).thenReturn(fallbackSprint)
        val result = adapter.fetchSprintInfo(cfg, "x")
        assertEquals(fallbackSprint, result)
    }

    @Test
    fun `fetchSprintInfo falls back when MCP base url does not match`() {
        val otherCfg =
            JiraConfig(
                name = "c",
                baseUrl = "https://other.example.com",
                apiToken = "t",
                projectKey = "PROJ",
                username = "u",
            )
        whenever(delegate.fetchSprintInfo(otherCfg, "https://other.example.com/sprints/42")).thenReturn(fallbackSprint)
        val result = adapter.fetchSprintInfo(otherCfg, "https://other.example.com/sprints/42")
        assertEquals(fallbackSprint, result)
    }

    @Test
    fun `fetchSprintInfo routes via MCP and parses sprint JSON`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{"id":42,"name":"Sprint 1","state":"active","startDate":"2024-01-01","endDate":"2024-01-14","goal":"ship"}""",
            ),
        )
        val result = adapter.fetchSprintInfo(cfg, "https://jira.example.com/sprints/42")
        assertEquals(42L, result.id)
        assertEquals("Sprint 1", result.name)
        assertEquals("active", result.state)
    }

    @Test
    fun `fetchSprintInfo falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.fetchSprintInfo(cfg, "https://jira.example.com/sprints/42")).thenReturn(fallbackSprint)
        val result = adapter.fetchSprintInfo(cfg, "https://jira.example.com/sprints/42")
        assertEquals(fallbackSprint, result)
    }

    @Test
    fun `fetchSprintInfo falls back when MCP returns non-JSON text`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = "not a json response"),
        )
        whenever(delegate.fetchSprintInfo(cfg, "https://jira.example.com/sprints/42")).thenReturn(fallbackSprint)
        val result = adapter.fetchSprintInfo(cfg, "https://jira.example.com/sprints/42")
        assertEquals(fallbackSprint, result)
    }

    @Test
    fun `fetchSprintInfo falls back when MCP gateway throws`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.fetchSprintInfo(cfg, "https://jira.example.com/sprints/42")).thenReturn(fallbackSprint)
        val result = adapter.fetchSprintInfo(cfg, "https://jira.example.com/sprints/42")
        assertEquals(fallbackSprint, result)
    }

    @Test
    fun `fetchSprintIssues falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.fetchSprintIssues(cfg, "x")).thenReturn(fallbackIssues)
        val result = adapter.fetchSprintIssues(cfg, "x")
        assertEquals(fallbackIssues, result)
    }

    @Test
    fun `fetchSprintIssues routes via MCP and returns empty when no issues`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"issues":[]}"""),
        )
        val result = adapter.fetchSprintIssues(cfg, "https://jira.example.com/sprints/42")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchSprintIssues returns parsed issues`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{"issues":[
                    {"key":"P-1","fields":{"summary":"Issue 1","status":{"name":"Open"},"assignee":{"displayName":"Alice"}}}
                ]}""",
            ),
        )
        val result = adapter.fetchSprintIssues(cfg, "https://jira.example.com/sprints/42")
        assertEquals(1, result.size)
        assertEquals("P-1", result[0].key)
        assertEquals("Issue 1", result[0].summary)
        assertEquals("Open", result[0].status)
        assertEquals("Alice", result[0].assignee)
    }

    @Test
    fun `fetchSprintIssues falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.fetchSprintIssues(cfg, "https://jira.example.com/sprints/42")).thenReturn(fallbackIssues)
        val result = adapter.fetchSprintIssues(cfg, "https://jira.example.com/sprints/42")
        assertEquals(fallbackIssues, result)
    }

    @Test
    fun `fetchSprintData falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.fetchSprintData(cfg, "x")).thenReturn(fallbackSprint to fallbackIssues)
        val result = adapter.fetchSprintData(cfg, "x")
        assertEquals(fallbackSprint, result.first)
        assertEquals(fallbackIssues, result.second)
    }

    @Test
    fun `fetchSprintData routes via MCP and returns info+issues`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"id":7,"name":"S","state":"active"}"""))
            .thenReturn(McpToolResult(isError = false, text = """{"issues":[]}"""))
        val result = adapter.fetchSprintData(cfg, "https://jira.example.com/sprints/7")
        assertEquals(7L, result.first.id)
        assertTrue(result.second.isEmpty())
    }

    @Test
    fun `fetchSprintData falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.fetchSprintData(cfg, "https://jira.example.com/sprints/7")).thenReturn(fallbackSprint to fallbackIssues)
        val result = adapter.fetchSprintData(cfg, "https://jira.example.com/sprints/7")
        assertEquals(fallbackSprint, result.first)
        assertEquals(fallbackIssues, result.second)
    }

    @Test
    fun `sprintIdFromRegex returns null when no match`() {
        val result =
            sprintIdFromRegex(
                Regex("/sprints?/(\\d+)"),
                "https://jira.example.com/no-sprint-here",
            )
        assertEquals(null, result)
    }

    @Test
    fun `sprintIdFromRegex returns null when capture group is non-numeric`() {
        val result =
            sprintIdFromRegex(
                Regex("/sprints?/(\\d+)"),
                "https://jira.example.com/sprints/abc",
            )
        assertEquals(null, result)
    }

    @Test
    fun `sprintIdFromRegex returns Long when matched and numeric`() {
        val result =
            sprintIdFromRegex(
                Regex("/sprints?/(\\d+)"),
                "https://jira.example.com/sprints/42",
            )
        assertEquals(42L, result)
    }

    @Test
    fun `fetchSprintInfo throws when board has no active sprint`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(isError = false, text = """{"values":[]}"""),
            )
        whenever(delegate.fetchSprintInfo(any(), any())).thenReturn(fallbackSprint)
        val result =
            adapter.fetchSprintInfo(cfg, "https://jira.example.com/boards/3")
        assertEquals(fallbackSprint, result)
    }

    @Test
    fun `fetchSprintInfo throws when URL has no board or sprint id`() {
        whenever(delegate.fetchSprintInfo(any(), any())).thenReturn(fallbackSprint)
        val result = adapter.fetchSprintInfo(cfg, "https://jira.example.com/something-else")
        assertEquals(fallbackSprint, result)
    }
}

class McpAwareJiraSprintFetchAdapterExtractSprintIdTest {
    @Test
    fun `sprintIdFromRegex returns the captured long when URL matches`() {
        val regex = Regex(""".*/sprint/(\d+)/?""")

        assertEquals(42L, sprintIdFromRegex(regex, "https://example.com/sprint/42/"))
    }

    @Test
    fun `sprintIdFromRegex returns null when URL does not match`() {
        val regex = Regex(""".*/sprint/(\d+)/?""")

        assertNull(sprintIdFromRegex(regex, "https://example.com/no-match"))
    }

    @Test
    fun `sprintIdFromRegex returns null when captured group is not a Long`() {
        val regex = Regex(""".*/sprint/(\w+)/?""")

        assertNull(sprintIdFromRegex(regex, "https://example.com/sprint/abc/"))
    }
}
