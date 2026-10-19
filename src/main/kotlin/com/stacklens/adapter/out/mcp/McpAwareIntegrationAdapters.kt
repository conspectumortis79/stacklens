
package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.search.GrafanaSearchPort
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.SearchResult
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.http.client.reactive.ClientHttpConnector
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal fun mcpRoutedWebClientBuilder(
    mcpRepo: McpServerConfigPort,
    gateway: McpGatewayPort,
    objectMapper: ObjectMapper,
    tool: String,
    predicate: (com.stacklens.domain.model.McpServerConfig) -> Boolean,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
): WebClient.Builder {
    val baseHttp =
        HttpClient
            .create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .doOnConnected { conn -> conn.addHandlerLast(ReadTimeoutHandler(readTimeoutMs / 1000)) }
    val fallback: ClientHttpConnector = ReactorClientHttpConnector(baseHttp)
    val routed = McpRoutedClientHttpConnector(mcpRepo, gateway, objectMapper, tool, predicate, fallback)
    return WebClient.builder().clientConnector(routed)
}

@Primary
@Component("mcpAwareGrafanaSearchPort")
open class McpAwareGrafanaSearchAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @Qualifier("grafanaSearchPort")
    private val delegate: GrafanaSearchPort,
) : GrafanaSearchPort {
    private val log = LoggerFactory.getLogger(McpAwareGrafanaSearchAdapter::class.java)

    override fun searchKeyword(
        config: GrafanaConfig,
        keyword: String,
        sessionId: Long,
        dashboardFilters: List<String>,
    ): List<SearchResult> {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.grafanaReady(it) }
                ?: return delegate.searchKeyword(config, keyword, sessionId, dashboardFilters)

        if (config.baseUrl.isNotBlank() &&
            !config.baseUrl.trimEnd('/').equals(mcp.grafanaBaseUrl.trimEnd('/'), ignoreCase = true)
        ) {
            log.debug(
                "[MCP→Grafana] config '{}' targets '{}' but MCP is configured for '{}', using direct adapter",
                config.name,
                config.baseUrl,
                mcp.grafanaBaseUrl,
            )
            return delegate.searchKeyword(config, keyword, sessionId, dashboardFilters)
        }

        return try {
            performScan(mcp, config, keyword, sessionId, dashboardFilters)
        } catch (e: Exception) {
            log.warn("[MCP] grafana scan failed, falling back: {}", e.message)
            delegate.searchKeyword(config, keyword, sessionId, dashboardFilters)
        }
    }

    private fun performScan(
        mcp: com.stacklens.domain.model.McpServerConfig,
        config: GrafanaConfig,
        keyword: String,
        sessionId: Long,
        filters: List<String>,
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val normalizedFilters = filters.map { it.trim().lowercase() }.filter(String::isNotBlank)

        val titleHits = listDashboards(mcp, keyword)
        for (dash in titleHits) {
            if (!matchesFilter(dash, normalizedFilters)) continue
            val title = dash.path("title").asText("")
            if (!title.contains(keyword, ignoreCase = true)) continue
            val url = "${config.baseUrl.trimEnd('/')}${dash.path("url").asText("")}"
            results +=
                SearchResult(
                    sessionId = sessionId,
                    keyword = keyword,
                    sourceType = SourceType.GRAFANA,
                    resultType = ResultType.GRAFANA_DASHBOARD,
                    configName = config.name,
                    repoName = title,
                    repoUrl = url,
                    filePath = null,
                    className = dash.path("folderTitle").asText("").takeIf(String::isNotBlank),
                    codeSnippet = "Dashboard '$title' contains the keyword in its title",
                )
        }

        val toScan: List<JsonNode> =
            if (titleHits.size < 200) {
                val all = listDashboards(mcp, "")
                (titleHits + all).distinctBy { it.path("uid").asText("") }
            } else {
                titleHits
            }
        val filtered = if (normalizedFilters.isEmpty()) toScan else toScan.filter { matchesFilter(it, normalizedFilters) }
        val futures =
            filtered.take(100).map { dash ->
                CompletableFuture.supplyAsync({
                    scanDashboard(mcp, config, dash, keyword, sessionId)
                }, scanExecutor)
            }
        results += collectScannedDashboards(futures, keyword)
        return results.distinctBy { resultKey(it) }
    }

    internal fun collectScannedDashboards(
        futures: List<Future<List<SearchResult>>>,
        keyword: String,
    ): List<SearchResult> {
        val collected = mutableListOf<SearchResult>()
        var failedDashboards = 0
        for (future in futures) {
            try {
                collected += future.get()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                failedDashboards++
                log.warn("[MCP Grafana scan] interrupted while collecting dashboard results: {}", e.message)
            } catch (e: Exception) {
                failedDashboards++
                log.warn("[MCP Grafana scan] dashboard scan failed: {}", e.message)
            }
        }
        if (failedDashboards > 0) {
            log.warn(
                "[MCP Grafana scan] {} of {} dashboards could not be scanned for keyword '{}'",
                failedDashboards,
                futures.size,
                keyword,
            )
        }
        return collected
    }

    private val scanExecutor: ExecutorService by lazy {
        Executors.newFixedThreadPool(8) { r ->
            Thread(r, "grafana-scan-${System.nanoTime()}").apply { isDaemon = true }
        }
    }

    private fun listDashboards(
        mcp: com.stacklens.domain.model.McpServerConfig,
        query: String,
    ): List<JsonNode> {
        val args = mutableMapOf<String, Any?>("limit" to 500)
        if (query.isNotBlank()) args["query"] = query
        val res = gateway.callTool(mcp, "grafana_search_dashboards", args)
        if (res.isError) throw IllegalStateException("grafana_search_dashboards: ${res.text}")
        val node = McpRouting.parseJsonOrNull(objectMapper, res.text) ?: return emptyList()
        if (!node.isArray) return emptyList()
        return node.toList()
    }

    private fun scanDashboard(
        mcp: com.stacklens.domain.model.McpServerConfig,
        config: GrafanaConfig,
        dash: JsonNode,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult> {
        val uid = dash.path("uid").asText("").ifBlank { return emptyList() }
        val res = gateway.callTool(mcp, "grafana_get_dashboard", mapOf("uid" to uid))
        if (res.isError) {
            log.debug("[MCP] grafana_get_dashboard error for {}: {}", uid, res.text)
            return emptyList()
        }
        val wrapper = McpRouting.parseJsonOrNull(objectMapper, res.text) ?: return emptyList()
        val dashboardJson = wrapper.path("dashboard")
        if (!dashboardJson.isObject) return emptyList()
        val title = dash.path("title").asText("")
        val folder = dash.path("folderTitle").asText("").takeIf(String::isNotBlank)
        val dashUrl = "${config.baseUrl.trimEnd('/')}${dash.path("url").asText("")}"
        val results = mutableListOf<SearchResult>()
        val dashDesc = dashboardJson.path("description").asText("")
        if (dashDesc.contains(keyword, ignoreCase = true)) {
            results +=
                SearchResult(
                    sessionId = sessionId,
                    keyword = keyword,
                    sourceType = SourceType.GRAFANA,
                    resultType = ResultType.GRAFANA_DASHBOARD,
                    configName = config.name,
                    repoName = title,
                    repoUrl = dashUrl,
                    className = folder,
                    codeSnippet = "Beschreibung: ${dashDesc.take(500)}",
                )
        }
        val panels = dashboardJson.path("panels")
        if (panels.isArray) {
            for (panel in panels) {
                results += scanPanel(panel, config, title, folder, keyword, sessionId, dashUrl)
                val rowPanels = panel.path("panels")
                if (rowPanels.isArray) {
                    for (rp in rowPanels) {
                        results += scanPanel(rp, config, title, folder, keyword, sessionId, dashUrl)
                    }
                }
            }
        }
        return results
    }

    private fun scanPanel(
        panel: JsonNode,
        config: GrafanaConfig,
        dashTitle: String,
        folder: String?,
        keyword: String,
        sessionId: Long,
        dashUrl: String,
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val panelTitle = panel.path("title").asText("")
        val panelType = panel.path("type").asText("")
        val panelDesc = panel.path("description").asText("")
        val titleMatch = panelTitle.contains(keyword, ignoreCase = true)
        val descMatch = panelDesc.contains(keyword, ignoreCase = true)
        if (titleMatch || descMatch) {
            results +=
                SearchResult(
                    sessionId = sessionId,
                    keyword = keyword,
                    sourceType = SourceType.GRAFANA,
                    resultType = ResultType.GRAFANA_PANEL,
                    configName = config.name,
                    repoName = dashTitle,
                    repoUrl = dashUrl,
                    filePath = panelTitle.ifBlank { null },
                    className = folder,
                    functionName = panelType.ifBlank { null },
                    codeSnippet =
                        if (descMatch) {
                            "Panel-Beschreibung: ${panelDesc.take(500)}"
                        } else {
                            "Panel-Titel: $panelTitle"
                        },
                )
        }
        if (config.scanPanelQueries) {
            val targets = panel.path("targets")
            if (targets.isArray) {
                for (target in targets) {
                    val targetJson = target.toString()
                    if (targetJson.contains(keyword, ignoreCase = true)) {
                        results +=
                            SearchResult(
                                sessionId = sessionId,
                                keyword = keyword,
                                sourceType = SourceType.GRAFANA,
                                resultType = ResultType.GRAFANA_PANEL,
                                configName = config.name,
                                repoName = dashTitle,
                                repoUrl = dashUrl,
                                filePath = panelTitle.ifBlank { "Panel (no title)" },
                                className = folder,
                                functionName = panelType.ifBlank { null },
                                codeSnippet = extractQuerySnippet(target, keyword),
                            )
                    }
                }
            }
        }
        return results
    }

    private fun extractQuerySnippet(
        target: JsonNode,
        keyword: String,
    ): String {
        val candidates =
            listOf("expr", "query", "rawQuery", "rawSql", "measurement", "metricName", "legendFormat", "refId", "target")
        val parts =
            candidates.mapNotNull { field ->
                val v = target.path(field).asText("")
                if (v.isNotBlank() && v.contains(keyword, ignoreCase = true)) "$field: $v" else null
            }
        return if (parts.isNotEmpty()) {
            parts.joinToString("\n")
        } else {
            "Query contains '$keyword': ${target.toString().take(500)}"
        }
    }

    private fun matchesFilter(
        dash: JsonNode,
        filters: List<String>,
    ): Boolean {
        if (filters.isEmpty()) return true
        val url = dash.path("url").asText("").lowercase()
        val title = dash.path("title").asText("").lowercase()
        val uid = dash.path("uid").asText("").lowercase()
        return filters.any { f ->
            val trimmed = f.trim('/')
            url.contains(f) ||
                title.contains(f) ||
                uid == f ||
                f.contains("/$uid/") ||
                f.endsWith("/$uid") ||
                (trimmed.isNotEmpty() && (url.contains(trimmed) || title.contains(trimmed)))
        }
    }

    private fun resultKey(r: SearchResult): String = "${r.repoName}|${r.filePath}|${r.codeSnippet.orEmpty().take(80)}"
}
