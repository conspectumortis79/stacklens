package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.search.GrafanaSearchPort
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.domain.model.SearchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareGrafanaSearchAdapterFullTest {
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
        listOf(SearchResult(sessionId = 1L, keyword = "kw", sourceType = SourceType.GRAFANA))

    @BeforeEach
    fun setUp() {
        adapter = McpAwareGrafanaSearchAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    @Test
    fun `title hit dashboard is returned when title contains keyword`() {
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
        assertTrue(result.any { it.resultType == ResultType.GRAFANA_DASHBOARD && it.repoName == "Auth Dash" })
    }

    @Test
    fun `panel description match returns panel result`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Auth Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Auth Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text =
                    """{"dashboard":{"description":"Other","panels":[{"title":"P","type":"graph","description":"auth panel"}]}}""",
            ),
        )
        val result = adapter.searchKeyword(cfg, "auth", 1L, emptyList())
        assertTrue(result.any { it.resultType == ResultType.GRAFANA_PANEL })
    }

    @Test
    fun `panel title match returns panel result`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text =
                    """{"dashboard":{"panels":[{"title":"Auth Panel","type":"graph","description":""}]}}""",
            ),
        )
        val result = adapter.searchKeyword(cfg, "auth", 1L, emptyList())
        assertTrue(result.any { it.resultType == ResultType.GRAFANA_PANEL && it.filePath == "Auth Panel" })
    }

    @Test
    fun `row panels are scanned too`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text =
                    """{"dashboard":{"panels":[{"title":"Row","type":"row","panels":[{"title":"AuthSub","type":"graph"}]}]}}""",
            ),
        )
        val result = adapter.searchKeyword(cfg, "authsub", 1L, emptyList())
        assertTrue(result.any { it.filePath == "AuthSub" })
    }

    @Test
    fun `dashboard description match returns dashboard result`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Some","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Some","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """{"dashboard":{"description":"contains auth in description","panels":[]}}""",
            ),
        )
        val result = adapter.searchKeyword(cfg, "auth", 1L, emptyList())
        assertTrue(result.any { it.resultType == ResultType.GRAFANA_DASHBOARD && it.codeSnippet!!.contains("Beschreibung") })
    }

    @Test
    fun `panel targets matching returns panel result when scanPanelQueries is true`() {
        val cfgWithQueries =
            GrafanaConfig(
                id = 1L,
                name = "g",
                baseUrl = cfg.baseUrl,
                username = cfg.username,
                password = cfg.password,
                scanPanelQueries = true,
            )
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text =
                    """{"dashboard":{"panels":[{"title":"P","type":"graph","targets":[{"expr":"up{job='auth'}"}]}]}}""",
            ),
        )
        val result = adapter.searchKeyword(cfgWithQueries, "auth", 1L, emptyList())
        assertTrue(result.any { it.codeSnippet!!.contains("expr") })
    }

    @Test
    fun `panel targets skipped when scanPanelQueries is false`() {
        val cfgNoQueries =
            GrafanaConfig(
                id = 1L,
                name = "g",
                baseUrl = cfg.baseUrl,
                username = cfg.username,
                password = cfg.password,
                scanPanelQueries = false,
            )
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text =
                    """{"dashboard":{"panels":[{"title":"P","type":"graph","targets":[{"expr":"up{job='auth'"}]}]}}""",
            ),
        )
        val result = adapter.searchKeyword(cfgNoQueries, "auth", 1L, emptyList())
        assertTrue(result.none { it.codeSnippet!!.contains("expr") })
    }

    @Test
    fun `dashboard with empty uid is skipped`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"","title":"Dash","type":"dash-db","url":"/d/","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"","title":"Dash","type":"dash-db","url":"/d/","folderTitle":"F"}]""",
            ),
        )
        val result = adapter.searchKeyword(cfg, "dash", 1L, emptyList())
        assertTrue(result.isNotEmpty())
        verify(gateway, atLeastOnce()).callTool(any(), any(), any())
    }

    @Test
    fun `get_dashboard error is logged and dashboard skipped`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(isError = true, text = "dashboard missing"),
        )
        val result = adapter.searchKeyword(cfg, "dash", 1L, emptyList())
        assertTrue(result.any { it.repoName == "Dash" })
    }

    @Test
    fun `listDashboards returns empty when result is not an array`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"dashboards":[]}"""),
        )
        val result = adapter.searchKeyword(cfg, "kw", 1L, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filter matches URL segment`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Title1","type":"dash-db","url":"/d/u1/prod","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Title1","type":"dash-db","url":"/d/u1/prod","folderTitle":"F"}]""",
            ),
            McpToolResult(isError = false, text = """{"dashboard":{"panels":[]}}"""),
        )
        val result = adapter.searchKeyword(cfg, "title1", 1L, listOf("/prod/"))
        assertTrue(result.any { it.repoName == "Title1" })
    }

    @Test
    fun `filter matches title`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"AuthProd","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"AuthProd","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(isError = false, text = """{"dashboard":{"panels":[]}}"""),
        )
        val result = adapter.searchKeyword(cfg, "authprod", 1L, listOf("authprod"))
        assertTrue(result.any { it.repoName == "AuthProd" })
    }

    @Test
    fun `filter matches uid`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"abc123","title":"Some","type":"dash-db","url":"/d/abc123","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"abc123","title":"Some","type":"dash-db","url":"/d/abc123","folderTitle":"F"}]""",
            ),
            McpToolResult(isError = false, text = """{"dashboard":{"panels":[]}}"""),
        )
        val result = adapter.searchKeyword(cfg, "some", 1L, listOf("abc123"))
        assertTrue(result.any { it.repoName == "Some" })
    }

    @Test
    fun `filter matches when filter ends with slash-uid`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"abc123","title":"Some","type":"dash-db","url":"/d/abc123","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"abc123","title":"Some","type":"dash-db","url":"/d/abc123","folderTitle":"F"}]""",
            ),
            McpToolResult(isError = false, text = """{"dashboard":{"panels":[]}}"""),
        )
        val result = adapter.searchKeyword(cfg, "some", 1L, listOf("/abc123"))
        assertTrue(result.any { it.repoName == "Some" })
    }

    @Test
    fun `dashboard filtered out when filter does not match`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash1","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash1","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
        )
        val result = adapter.searchKeyword(cfg, "dash1", 1L, listOf("nonexistent"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filter with whitespace is trimmed and lowercased`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash1","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash1","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
        )
        val result = adapter.searchKeyword(cfg, "dash1", 1L, listOf("  U1  "))
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `empty filter list means no filtering`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(isError = false, text = """{"dashboard":{"panels":[]}}"""),
        )
        val result = adapter.searchKeyword(cfg, "dash", 1L, emptyList())
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `skips deep scan when there are more than 200 dashboards`() {
        val dashboards = (1..250).joinToString(",") { """{"id":$it,"uid":"u$it","title":"T$it","url":"/d/u$it"}""" }
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = "[$dashboards]"),
        )
        val result = adapter.searchKeyword(cfg, "T1", 1L, emptyList())
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `panel without title gets fallback filePath`() {
        val cfgWithQueries =
            GrafanaConfig(
                id = 1L,
                name = "g",
                baseUrl = cfg.baseUrl,
                username = cfg.username,
                password = cfg.password,
                scanPanelQueries = true,
            )
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text =
                    """{"dashboard":{"panels":[{"type":"graph","targets":[{"expr":"up{job='auth'"}]}]}}""",
            ),
        )
        val result = adapter.searchKeyword(cfgWithQueries, "auth", 1L, emptyList())
        assertTrue(result.any { it.filePath == "Panel (no title)" })
    }

    @Test
    fun `dashboard without folderTitle has null className`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1"}]""",
            ),
            McpToolResult(isError = false, text = """{"dashboard":{"panels":[]}}"""),
        )
        val result = adapter.searchKeyword(cfg, "dash", 1L, emptyList())
        assertTrue(result.any { it.className == null })
    }

    @Test
    fun `duplicate results are deduplicated`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text =
                    """{"dashboard":{"description":"auth","panels":[{"title":"Auth","type":"graph","description":"auth panel"}]}}""",
            ),
        )
        val result = adapter.searchKeyword(cfg, "auth", 1L, emptyList())
        val titles = result.map { it.repoName }
        val dashCount = titles.count { it == "Dash" }
        assertNotNull(result)
        assertTrue(dashCount >= 1)
    }

    @Test
    fun `empty baseUrl results in empty url prefix`() {
        val cfgEmpty = GrafanaConfig(id = 1L, name = "g", baseUrl = "", username = cfg.username, password = cfg.password)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(isError = false, text = """{"dashboard":{"panels":[]}}"""),
        )
        val result = adapter.searchKeyword(cfgEmpty, "dash", 1L, emptyList())
        assertTrue(result.any { it.repoUrl == "/d/u1" })
    }

    @Test
    fun `baseUrl trailing slash is trimmed`() {
        val cfgSlash =
            GrafanaConfig(id = 1L, name = "g", baseUrl = "https://grafana.example.com/", username = cfg.username, password = cfg.password)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(isError = false, text = """{"dashboard":{"panels":[]}}"""),
        )
        val result = adapter.searchKeyword(cfgSlash, "dash", 1L, emptyList())
        assertTrue(result.any { it.repoUrl == "https://grafana.example.com/d/u1" })
    }

    @Test
    fun `non-object dashboardJson is skipped`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(isError = false, text = """{"dashboard":"not an object"}"""),
        )
        val result = adapter.searchKeyword(cfg, "dash", 1L, emptyList())
        assertTrue(result.any { it.repoName == "Dash" })
    }

    @Test
    fun `non-array panels are skipped`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(isError = false, text = """{"dashboard":{"panels":"not an array"}}"""),
        )
        val result = adapter.searchKeyword(cfg, "dash", 1L, emptyList())
        assertTrue(result.any { it.repoName == "Dash" })
    }

    @Test
    fun `listDashboards parse error returns empty`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = "not valid json {{{"),
        )
        val result = adapter.searchKeyword(cfg, "kw", 1L, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `panel with empty title but matching desc gets null filePath`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """{"dashboard":{"panels":[{"type":"graph","description":"contains auth"}]}}""",
            ),
        )
        val result = adapter.searchKeyword(cfg, "auth", 1L, emptyList())
        assertTrue(result.any { it.filePath == null })
    }

    @Test
    fun `panel with empty type gets null functionName`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """{"dashboard":{"panels":[{"title":"Auth","description":""}]}}""",
            ),
        )
        val result = adapter.searchKeyword(cfg, "auth", 1L, emptyList())
        assertTrue(result.any { it.functionName == null })
    }

    @Test
    fun `query snippet fallback when no candidate field matches`() {
        val cfgWithQueries =
            GrafanaConfig(
                id = 1L,
                name = "g",
                baseUrl = cfg.baseUrl,
                username = cfg.username,
                password = cfg.password,
                scanPanelQueries = true,
            )
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text =
                    """{"dashboard":{"panels":[{"title":"P","type":"graph","targets":[{"unrelated":"auth"}]}]}}""",
            ),
        )
        val result = adapter.searchKeyword(cfgWithQueries, "auth", 1L, emptyList())
        assertTrue(result.any { it.codeSnippet!!.contains("Query contains") })
    }

    @Test
    fun `query snippet uses first matching candidate field`() {
        val cfgWithQueries =
            GrafanaConfig(
                id = 1L,
                name = "g",
                baseUrl = cfg.baseUrl,
                username = cfg.username,
                password = cfg.password,
                scanPanelQueries = true,
            )
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text =
                    """{"dashboard":{"panels":[{"title":"P","type":"graph","targets":[{"expr":"up{job='auth'}","query":"backup{job='auth'}"}]}]}}""",
            ),
        )
        val result = adapter.searchKeyword(cfgWithQueries, "auth", 1L, emptyList())
        assertTrue(result.any { it.codeSnippet!!.startsWith("expr:") })
    }

    @Test
    fun `falls back to delegate when routeGrafana is false`() {
        val cfgNoRoute = McpServerConfig(active = true, routeGrafana = false, grafanaBaseUrl = "https://grafana.example.com")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(cfgNoRoute)
        whenever(delegate.searchKeyword(cfg, "kw", 1L, emptyList())).thenReturn(fallbackResults)
        val result = adapter.searchKeyword(cfg, "kw", 1L, emptyList())
        assertEquals(fallbackResults, result)
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `falls back to delegate when grafana base url mismatches`() {
        val cfgMismatch = McpServerConfig(active = true, routeGrafana = true, grafanaBaseUrl = "https://other.example.com")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(cfgMismatch)
        whenever(delegate.searchKeyword(cfg, "kw", 1L, emptyList())).thenReturn(fallbackResults)
        val result = adapter.searchKeyword(cfg, "kw", 1L, emptyList())
        assertEquals(fallbackResults, result)
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `listDashboards called with query when keyword is non-blank`() {
        var firstListDashboardsSeen = false
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val args = it.arguments[2] as Map<String, Any?>
            if (!firstListDashboardsSeen && args.containsKey("query")) {
                firstListDashboardsSeen = true
                assertEquals("auth", args["query"])
            }
            McpToolResult(isError = false, text = "[]")
        }
        val result = adapter.searchKeyword(cfg, "auth", 1L, emptyList())
        assertTrue(result.isEmpty())
        assertTrue(firstListDashboardsSeen, "Expected first listDashboards call to carry query=auth")
    }

    @Test
    fun `listDashboards called without query when keyword is blank`() {
        whenever(gateway.callTool(any(), any(), any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val args = it.arguments[2] as Map<String, Any?>
            assertNotNull(args["limit"])
            McpToolResult(isError = false, text = "[]")
        }
        val result = adapter.searchKeyword(cfg, "", 1L, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `gateway exception falls back to delegate`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(IllegalStateException("gateway down"))
        whenever(delegate.searchKeyword(cfg, "kw", 1L, emptyList())).thenReturn(fallbackResults)
        val result = adapter.searchKeyword(cfg, "kw", 1L, emptyList())
        assertEquals(fallbackResults, result)
    }

    @Test
    fun `gateway error response falls back to delegate`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = true, text = "tool error"),
        )
        whenever(delegate.searchKeyword(cfg, "kw", 1L, emptyList())).thenReturn(fallbackResults)
        val result = adapter.searchKeyword(cfg, "kw", 1L, emptyList())
        assertEquals(fallbackResults, result)
    }

    @Test
    fun `title hit but filter mismatch skips dashboard in title phase`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Auth Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
            McpToolResult(
                isError = false,
                text = """[{"id":1,"uid":"u1","title":"Auth Dash","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
            ),
        )
        val result = adapter.searchKeyword(cfg, "auth", 1L, listOf("xyz"))
        assertTrue(result.isEmpty())
    }
}
