package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.search.GrafanaSearchPort
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.GrafanaConfig
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

class McpAwareGrafanaSearchAdapterTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<GrafanaSearchPort>()
    private val mapper = ObjectMapper()
    private lateinit var adapter: McpAwareGrafanaSearchAdapter

    private val cfg =
        GrafanaConfig(
            id = 1L,
            name = "g",
            baseUrl = "https://grafana.example.com",
            username = "u",
            password = "p",
        )
    private val mcpCfg =
        McpServerConfig(
            active = true,
            routeGrafana = true,
            grafanaBaseUrl = "https://grafana.example.com",
        )

    private val fallbackResults =
        listOf(SearchResult(sessionId = 1, keyword = "kw", sourceType = SourceType.GRAFANA))

    @BeforeEach
    fun setUp() {
        adapter = McpAwareGrafanaSearchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    @Test
    fun `searchKeyword falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.searchKeyword(cfg, "kw", 1L, emptyList())).thenReturn(fallbackResults)
        val result = adapter.searchKeyword(cfg, "kw", 1L, emptyList())
        assertEquals(fallbackResults, result)
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `searchKeyword falls back when MCP base url does not match`() {
        val otherCfg =
            GrafanaConfig(
                id = 1L,
                name = "g",
                baseUrl = "https://other.example.com",
                username = "u",
                password = "p",
            )
        whenever(delegate.searchKeyword(otherCfg, "kw", 1L, emptyList())).thenReturn(fallbackResults)
        val result = adapter.searchKeyword(otherCfg, "kw", 1L, emptyList())
        assertEquals(fallbackResults, result)
    }

    @Test
    fun `searchKeyword routes via MCP and returns dashboard hits`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Auth Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Auth Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(isError = false, text = """{"dashboard":{"panels":[]}}"""),
        )
        val result = adapter.searchKeyword(cfg, "auth", 1L, emptyList())
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `searchKeyword falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.searchKeyword(cfg, "kw", 1L, emptyList())).thenReturn(fallbackResults)
        val result = adapter.searchKeyword(cfg, "kw", 1L, emptyList())
        assertEquals(fallbackResults, result)
    }

    @Test
    fun `searchKeyword falls back when MCP gateway throws`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.searchKeyword(cfg, "kw", 1L, emptyList())).thenReturn(fallbackResults)
        val result = adapter.searchKeyword(cfg, "kw", 1L, emptyList())
        assertEquals(fallbackResults, result)
    }

    @Test
    fun `searchKeyword returns empty when MCP returns empty dashboards`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(isError = false, text = """{"dashboards":[]}"""))
        val result = adapter.searchKeyword(cfg, "kw", 1L, emptyList())
        assertTrue(result.isEmpty())
    }
}
