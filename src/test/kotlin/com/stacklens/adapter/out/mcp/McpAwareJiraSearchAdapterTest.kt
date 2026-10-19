package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.search.JiraSearchPort
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.domain.model.SearchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareJiraSearchAdapterTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<JiraSearchPort>()
    private val mapper = ObjectMapper()
    private lateinit var adapter: McpAwareJiraSearchAdapter

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

    private val fallbackResults =
        listOf(SearchResult(sessionId = 1, keyword = "kw", sourceType = SourceType.JIRA))

    @BeforeEach
    fun setUp() {
        adapter = McpAwareJiraSearchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    @Test
    fun `searchKeyword falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.searchKeyword(cfg, "kw", emptyList(), 1L, emptyList(), emptyList())).thenReturn(fallbackResults)
        val result = adapter.searchKeyword(cfg, "kw", emptyList(), 1L, emptyList(), emptyList())
        assertEquals(fallbackResults, result)
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `searchKeyword falls back when MCP jira base url does not match`() {
        val otherCfg =
            JiraConfig(
                name = "c",
                baseUrl = "https://other.example.com",
                apiToken = "t",
                projectKey = "PROJ",
                username = "u",
            )
        whenever(delegate.searchKeyword(otherCfg, "kw", emptyList(), 1L, emptyList(), emptyList())).thenReturn(fallbackResults)
        val result = adapter.searchKeyword(otherCfg, "kw", emptyList(), 1L, emptyList(), emptyList())
        assertEquals(fallbackResults, result)
    }

    @Test
    fun `searchKeyword routes via MCP and parses JSON`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{"issues":[{"key":"P-1","fields":{"summary":"S","description":"D","issuetype":{"name":"Bug"},"status":{"name":"Open"}}}]}""",
            ),
        )
        val result = adapter.searchKeyword(cfg, "kw", emptyList(), 1L, emptyList(), emptyList())
        assertEquals(1, result.size)
        assertEquals("P-1", result[0].filePath)
    }

    @Test
    fun `searchKeyword falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.searchKeyword(cfg, "kw", emptyList(), 1L, emptyList(), emptyList())).thenReturn(fallbackResults)
        val result = adapter.searchKeyword(cfg, "kw", emptyList(), 1L, emptyList(), emptyList())
        assertEquals(fallbackResults, result)
    }

    @Test
    fun `searchKeyword returns empty list when MCP returns invalid JSON`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = "not json"),
        )
        val result = adapter.searchKeyword(cfg, "kw", emptyList(), 1L, emptyList(), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword falls back when MCP gateway throws`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.searchKeyword(cfg, "kw", emptyList(), 1L, emptyList(), emptyList())).thenReturn(fallbackResults)
        val result = adapter.searchKeyword(cfg, "kw", emptyList(), 1L, emptyList(), emptyList())
        assertEquals(fallbackResults, result)
    }

    @Test
    fun `searchKeyword returns empty list when MCP returns empty issues array`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"issues":[]}"""),
        )
        val result = adapter.searchKeyword(cfg, "kw", emptyList(), 1L, emptyList(), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildJql with keyword only`() {
        val jql = adapter.buildJql(emptyList(), "foo", emptyList(), emptyList())
        assertTrue(jql.contains("text ~ \"foo\""))
        assertTrue(jql.contains("summary ~ \"foo\""))
        assertTrue(jql.contains("ORDER BY updated DESC"))
    }

    @Test
    fun `buildJql with projects`() {
        val jql = adapter.buildJql(listOf("PROJ", "OPS"), "kw", emptyList(), emptyList())
        assertTrue(jql.contains("project in (\"PROJ\",\"OPS\")"))
    }

    @Test
    fun `buildJql with components and labels`() {
        val jql = adapter.buildJql(emptyList(), "kw", listOf("API"), listOf("bug"))
        assertTrue(jql.contains("component in (\"API\")"))
        assertTrue(jql.contains("labels in (\"bug\")"))
    }

    @Test
    fun `buildJql escapes quotes in keyword`() {
        val jql = adapter.buildJql(emptyList(), """a"b""", emptyList(), emptyList())
        assertTrue(jql.contains("a\\\"b"))
    }

    @Test
    fun `buildJql filters blank projects`() {
        val jql = adapter.buildJql(listOf("", "PROJ", "   "), "kw", emptyList(), emptyList())
        assertTrue(jql.contains("PROJ"))
        assertTrue(!jql.contains("\"\""))
    }
}
