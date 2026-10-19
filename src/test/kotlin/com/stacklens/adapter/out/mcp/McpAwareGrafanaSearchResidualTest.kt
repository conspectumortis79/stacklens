package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.search.GrafanaSearchPort
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareGrafanaSearchResidualTest {
    private val mcpRepo = mock<McpServerConfigPort>()

    private val gateway = mock<McpGatewayPort>()

    private val delegate = mock<GrafanaSearchPort>()

    private val routing =
        McpServerConfig(
            active = true,
            routeGrafana = true,
            grafanaBaseUrl = "http://grafana:3000",
        )

    private val config =
        GrafanaConfig(
            name = "graf",
            baseUrl = "http://grafana:3000",
            username = "u",
            password = "p",
            scanPanelQueries = true,
        )

    private val queues = mutableMapOf<String, ArrayDeque<McpToolResult>>()

    private val broken = mutableSetOf<String>()

    private fun adapter() = McpAwareGrafanaSearchAdapter(mcpRepo, gateway, ObjectMapper(), delegate)

    private fun route(
        answers: List<Pair<String, List<McpToolResult>>>,
        brokenTools: List<String> = emptyList(),
    ) {
        queues.clear()
        broken.clear()
        broken += brokenTools
        answers.forEach { (tool, results) -> queues[tool] = ArrayDeque(results) }
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(routing)
        whenever(gateway.callTool(any(), any(), any())).thenAnswer { invocation ->
            val tool = invocation.getArgument<String>(1)
            if (tool in broken) throw RuntimeException("tool '$tool' unavailable")
            val queue = queues[tool]
            if (queue != null && queue.isNotEmpty()) queue.removeFirst() else McpToolResult("[]")
        }
    }

    private val searchResult =
        listOf(
            "grafana_search_dashboards" to
                listOf(
                    McpToolResult(
                        """[{"uid":"u1","title":"Fehler Dashboard","url":"/d/u1/fehler","folderTitle":"Team"},
                          {"uid":"u2","title":"Anderes","url":"/d/u2/andere","folderTitle":""}]""",
                    ),
                ),
        )

    private val dashboardJson =
        """{"dashboard":{"uid":"u1","description":"Ein Dashboard über FEHLER","panels":[
            {"title":"Fehler Quote","type":"graph","description":"",
             "targets":[{"expr":"rate(FEHLER[5m])"},{"expr":"count(ok)"},{"measurement":"   ","legendFormat":"anders"}]},
            {"title":"","type":"","description":"FEHLER Beschreibung","targets":{"kein":"array"}},
            {"title":"Tabelle","type":"table","description":"d","panels":[
             {"title":"Unterpanel","type":"stat","description":"FEHLER drin",
              "targets":[{"measurement":"   ","legendFormat":"anderes"}]}]}
        ]}}"""

    @Test
    fun `the scan filters dashboards normalises filters and keeps panel hits`() {
        route(
            searchResult +
                listOf(
                    "grafana_get_dashboard" to
                        listOf(
                            McpToolResult(dashboardJson),
                            McpToolResult("kein json{"),
                        ),
                ),
        )

        val results = adapter().searchKeyword(config, "FEHLER", 7L, listOf("fehler", "dashboard", "   ", "//", "/d/u1/", "andere"))

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.keyword == "FEHLER" })
        assertTrue(results.any { it.functionName == "graph" })
        assertTrue(results.any { it.functionName == null })
        assertTrue(results.any { it.className == "Team" })
        assertTrue(results.none { it.repoName == "Drittes" })
    }

    @Test
    fun `the second scan reuses the executor pool and drops unparseable answers`() {
        route(
            searchResult +
                listOf(
                    "grafana_get_dashboard" to
                        listOf(
                            McpToolResult(dashboardJson),
                            McpToolResult("""{"dashboard":"kein objekt"}"""),
                        ),
                ),
        )

        val target = adapter()
        val first = target.searchKeyword(config, "FEHLER", 7L, emptyList())
        val second = target.searchKeyword(config, "FEHLER", 7L, emptyList())

        assertTrue(first.isNotEmpty())
        assertTrue(second.isEmpty())
    }

    @Test
    fun `a dashboard lookup that throws inside the pool is skipped per dashboard`() {
        route(searchResult, brokenTools = listOf("grafana_get_dashboard"))

        val results = adapter().searchKeyword(config, "FEHLER", 7L, listOf("/d/u1", "u1"))

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.filePath == null }, results.joinToString { it.filePath.toString() })
    }

    @Test
    fun `a failing dashboard search falls back to the direct adapter`() {
        route(
            listOf(
                "grafana_search_dashboards" to listOf(McpToolResult("abgelehnt", isError = true)),
            ),
        )
        whenever(delegate.searchKeyword(any(), any(), any(), any())).thenReturn(emptyList())

        assertTrue(adapter().searchKeyword(config, "FEHLER", 7L, emptyList()).isEmpty())
    }

    @Test
    fun `a dashboard without a uid is never looked up`() {
        route(
            listOf(
                "grafana_search_dashboards" to listOf(McpToolResult("""[{"uid":"  ","title":"Ohne Schluesselwort"}]""")),
            ),
        )

        assertTrue(adapter().searchKeyword(config, "Fehler", 7L, emptyList()).isEmpty())
    }
}
