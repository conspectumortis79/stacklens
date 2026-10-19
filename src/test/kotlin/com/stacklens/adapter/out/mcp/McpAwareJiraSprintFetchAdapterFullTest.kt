package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.LinkedIssue
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareJiraSprintFetchAdapterFullTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<JiraSprintFetchPort>()
    private val mapper = ObjectMapper()

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

    private fun adapter() =
        McpAwareJiraSprintFetchAdapter(
            mcpRepo = mcpRepo,
            gateway = gateway,
            objectMapper = mapper,
            delegate = delegate,
        )

    @Test
    fun `fetchSprintInfo delegates when MCP not active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.fetchSprintInfo(any(), any())).thenReturn(
            com.stacklens.application.port.out
                .SprintInfo(1L, "Sprint 1", "active", null, null, null),
        )
        val info = adapter().fetchSprintInfo(cfg, "https://jira.example.com/sprints/1")
        assertEquals(1L, info.id)
    }

    @Test
    fun `fetchSprintInfo delegates on MCP error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "mcp error", isError = true),
        )
        whenever(delegate.fetchSprintInfo(any(), any())).thenReturn(
            com.stacklens.application.port.out
                .SprintInfo(2L, "Fallback", "closed", null, null, null),
        )
        val info = adapter().fetchSprintInfo(cfg, "https://jira.example.com/sprints/2")
        assertEquals(2L, info.id)
    }

    @Test
    fun `fetchSprintInfo delegates on MCP exception`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenThrow(RuntimeException("boom"))
        whenever(delegate.fetchSprintInfo(any(), any())).thenReturn(
            com.stacklens.application.port.out
                .SprintInfo(3L, "X", "x", null, null, null),
        )
        val info = adapter().fetchSprintInfo(cfg, "https://jira.example.com/sprints/3")
        assertEquals(3L, info.id)
    }

    @Test
    fun `fetchSprintInfo uses MCP with sprint id in URL path`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"id":5,"name":"Sprint 5","state":"active","goal":"ship"}""", isError = false),
        )
        val info = adapter().fetchSprintInfo(cfg, "https://jira.example.com/sprints/5")
        assertEquals(5L, info.id)
        assertEquals("ship", info.goal)
    }

    @Test
    fun `fetchSprintInfo uses MCP with sprint query param`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"id":7,"name":"Sprint 7","state":"future"}""", isError = false),
        )
        val info = adapter().fetchSprintInfo(cfg, "https://jira.example.com/rapid?selectedSprint=7")
        assertEquals(7L, info.id)
    }

    @Test
    fun `fetchSprintInfo uses MCP with board id and looks up active sprint`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            val tool = it.arguments[1] as String
            when (tool) {
                "jira_get_active_sprint" ->
                    McpToolResult(text = """{"values":[{"id":11}]}""", isError = false)
                "jira_get_sprint" ->
                    McpToolResult(text = """{"id":11,"name":"Active","state":"active"}""", isError = false)
                else -> McpToolResult(text = "{}", isError = true)
            }
        }
        val info = adapter().fetchSprintInfo(cfg, "https://jira.example.com/boards/3")
        assertEquals(11L, info.id)
    }

    @Test
    fun `fetchSprintIssues delegates when MCP not active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.fetchSprintIssues(any(), any())).thenReturn(emptyList())
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `fetchSprintIssues uses MCP with single page`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text = """{"total":1,"issues":[{"key":"PROJ-1","fields":{"summary":"s","status":{"name":"Open"}}}]}""",
                isError = false,
            ),
        )
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        assertEquals("PROJ-1", issues[0].key)
    }

    @Test
    fun `fetchSprintIssues handles pagination across multiple pages`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var page = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            page++
            when (page) {
                1 ->
                    McpToolResult(
                        text = """{"total":3,"issues":[{"key":"P-1","fields":{"summary":"a"}},{"key":"P-2","fields":{"summary":"b"}},{"key":"P-3","fields":{"summary":"c"}}]}""",
                        isError = false,
                    )
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertEquals(3, issues.size)
    }

    @Test
    fun `fetchSprintIssues handles textual description`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text = """{"total":1,"issues":[{"key":"P-1","fields":{"summary":"x","description":"plain text","status":{"name":"Open"}}}]}""",
                isError = false,
            ),
        )
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertEquals("plain text", issues[0].description)
    }

    @Test
    fun `fetchSprintIssues handles object description as ADF`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text = """{"total":1,"issues":[{"key":"P-1","fields":{"summary":"x","description":{"type":"doc","content":[{"type":"paragraph"}]},"status":{"name":"Open"}}}]}""",
                isError = false,
            ),
        )
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertNotNull(issues[0].description)
    }

    @Test
    fun `fetchSprintIssues handles missing or non-textual description as empty string`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text = """{"total":1,"issues":[{"key":"P-1","fields":{"summary":"x","status":{"name":"Open"}}}]}""",
                isError = false,
            ),
        )
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertEquals("", issues[0].description)
    }

    @Test
    fun `fetchSprintIssues handles textual comment body`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text =
                    """
                    {"total":1,"issues":[{"key":"P-1","fields":{"summary":"x","comment":{"comments":
                    [{"body":"plain text comment","author":{"displayName":"Alice"}}]}}}]}
                    """.trimIndent(),
                isError = false,
            ),
        )
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertTrue(issues[0].comments.contains("plain text comment"))
    }

    @Test
    fun `fetchSprintIssues handles ADF comment body`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text =
                    """
                    {"total":1,"issues":[{"key":"P-1","fields":{"summary":"x","comment":{"comments":
                    [{"body":{"type":"doc","content":[{"type":"text","text":"adf comment"}]},
                    "author":{"displayName":"Alice"}}]}}}]}
                    """.trimIndent(),
                isError = false,
            ),
        )
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertTrue(issues[0].comments.contains("adf comment"))
    }

    @Test
    fun `fetchSprintIssues handles comment with no author (body only)`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text = """{"total":1,"issues":[{"key":"P-1","fields":{"summary":"x","comment":{"comments":[{"body":"no author comment"}]}}}]}""",
                isError = false,
            ),
        )
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertTrue(issues[0].comments.contains("no author comment"))
        assertFalse(issues[0].comments.startsWith("Alice"))
    }

    @Test
    fun `fetchSprintIssues handles missing or non-textual comment body as empty string`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text = """{"total":1,"issues":[{"key":"P-1","fields":{"summary":"x","comment":{"comments":[{}]}}}]}""",
                isError = false,
            ),
        )
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertEquals("", issues[0].comments)
    }

    @Test
    fun `fetchSprintIssues handles priority assignee createdAt as nullable values`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text =
                    """
                    {"total":1,"issues":[{"key":"P-1","fields":{"summary":"s",
                    "priority":{"name":"High"},"assignee":{"displayName":"Bob"},
                    "created":"2020-01-01T00:00:00Z","status":{"name":"Open"}}}]}
                    """.trimIndent(),
                isError = false,
            ),
        )
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertEquals("High", issues[0].priority)
        assertEquals("Bob", issues[0].assignee)
        assertEquals("2020-01-01T00:00:00Z", issues[0].createdAt)
    }

    @Test
    fun `fetchSprintIssues handles blank priority assignee createdAt as null`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text = """{"total":1,"issues":[{"key":"P-1","fields":{"summary":"s","status":{"name":"Open"}}}]}""",
                isError = false,
            ),
        )
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertNull(issues[0].priority)
        assertNull(issues[0].assignee)
        assertNull(issues[0].createdAt)
    }

    @Test
    fun `fetchSprintIssues handles labels with blank entries filtered out`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text = """{"total":1,"issues":[{"key":"P-1","fields":{"summary":"s","labels":["label1","","label2"],"status":{"name":"Open"}}}]}""",
                isError = false,
            ),
        )
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertEquals(listOf("label1", "label2"), issues[0].labels)
    }

    @Test
    fun `fetchSprintIssues derives startedAt and qaLoopCount from the MCP changelog`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text =
                    """{"total":1,"issues":[{"key":"P-1","fields":{"summary":"s","status":{"name":"Open"},"created":"2020-01-01T00:00:00Z"},
                    "changelog":{"histories":[
                      {"created":"2020-02-01T00:00:00Z","items":[{"field":"status","toString":"In Progress"}]},
                      {"created":"2020-03-01T00:00:00Z","items":[{"field":"status","toString":"QA"}]},
                      {"created":"2020-04-01T00:00:00Z","items":[{"field":"status","toString":"Waiting for QA"}]}
                    ]}}]}""",
                isError = false,
            ),
        )

        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")

        assertEquals("2020-02-01T00:00:00Z", issues[0].startedAt)
        assertEquals(1, issues[0].qaLoopCount)
    }

    @Test
    fun `fetchSprintIssues maps issue links the same way as the native client`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text =
                    """{"total":1,"issues":[{"key":"P-1","fields":{"summary":"s","status":{"name":"Open"},"issuelinks":[
                      {"type":{"inward":"is blocked by","outward":"blocks"},"inwardIssue":{"key":"A-1","fields":{"summary":"in","status":{"name":"To Do"}}}},
                      {"type":{"inward":"is blocked by","outward":"blocks"},"outwardIssue":{"key":"B-2"}},
                      {"type":null,"inwardIssue":{"key":"C-3"}},
                      {"type":null,"outwardIssue":{"key":"D-4"}},
                      {"type":{"inward":"x","outward":"y"}},
                      {"type":{"inward":"x","outward":"y"},"inwardIssue":{}}
                    ]}}]}""",
                isError = false,
            ),
        )

        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        val links = issues[0].linkedIssues

        assertEquals(4, links.size)
        assertEquals(LinkedIssue("A-1", "in", "is blocked by", "To Do"), links[0])
        assertEquals(LinkedIssue("B-2", "", "blocks", null), links[1])
        assertEquals(LinkedIssue("C-3", "", "relates to", null), links[2])
        assertEquals(LinkedIssue("D-4", "", "relates to", null), links[3])
    }

    @Test
    fun `fetchSprintIssues delegates on MCP error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "fail", isError = true),
        )
        whenever(delegate.fetchSprintIssues(any(), any())).thenReturn(emptyList())
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `fetchSprintIssues delegates on MCP exception`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenThrow(RuntimeException("boom"))
        whenever(delegate.fetchSprintIssues(any(), any())).thenReturn(emptyList())
        val issues = adapter().fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `fetchSprintData delegates to native when MCP not active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        val expected =
            com.stacklens.application.port.out
                .SprintInfo(1L, "S", "x", null, null, null) to emptyList<com.stacklens.application.port.out.SprintIssue>()
        whenever(delegate.fetchSprintData(any(), any())).thenReturn(expected)
        val (info, issues) = adapter().fetchSprintData(cfg, "https://jira.example.com/sprints/1")
        assertEquals(1L, info.id)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `fetchSprintData uses MCP and returns pair`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var page = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            page++
            when (it.arguments[1]) {
                "jira_get_sprint" ->
                    McpToolResult(text = """{"id":1,"name":"Sprint","state":"active"}""", isError = false)
                "jira_list_sprint_issues" ->
                    McpToolResult(text = """{"total":0,"issues":[]}""", isError = false)
                else -> McpToolResult(text = "{}", isError = false)
            }
        }
        val (info, issues) = adapter().fetchSprintData(cfg, "https://jira.example.com/sprints/1")
        assertEquals(1L, info.id)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `fetchSprintData delegates on MCP error during issues fetch`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        var page = 0
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            page++
            when (page) {
                1 -> McpToolResult(text = """{"id":1,"name":"S","state":"active"}""", isError = false)
                2 -> McpToolResult(text = "err", isError = true)
                else -> McpToolResult(text = "{}", isError = true)
            }
        }
        val expected =
            com.stacklens.application.port.out
                .SprintInfo(99L, "F", "x", null, null, null) to emptyList<com.stacklens.application.port.out.SprintIssue>()
        whenever(delegate.fetchSprintData(any(), any())).thenReturn(expected)
        val (info, _) = adapter().fetchSprintData(cfg, "https://jira.example.com/sprints/1")
        assertEquals(99L, info.id)
    }

    @Test
    fun `URL with sprint singular is also recognized`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"id":42,"name":"x","state":"x"}""", isError = false),
        )
        val info = adapter().fetchSprintInfo(cfg, "https://jira.example.com/sprint/42")
        assertEquals(42L, info.id)
    }

    @Test
    fun `URL with case-insensitive Sprints is recognized`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"id":50,"name":"x","state":"x"}""", isError = false),
        )
        val info = adapter().fetchSprintInfo(cfg, "https://jira.example.com/Sprints/50")
        assertEquals(50L, info.id)
    }

    @Test
    fun `URL with sprint param alternatives (sprint, selectedSprint, sprints) are recognized`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"id":77,"name":"x","state":"x"}""", isError = false),
        )
        val info = adapter().fetchSprintInfo(cfg, "https://jira.example.com/board?selectedSprint=77")
        assertEquals(77L, info.id)
    }
}
