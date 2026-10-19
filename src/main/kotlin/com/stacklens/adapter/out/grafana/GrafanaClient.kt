package com.stacklens.adapter.out.grafana

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.application.port.out.search.GrafanaSearchPort
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.search.KeywordContext
import com.stacklens.shared.http.ReactiveHttpClientFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

open class GrafanaClient(
    @Value("\${webclient.connect-timeout:30000}") private val connectTimeoutMs: Int = 30_000,
    @Value("\${webclient.read-timeout:60000}") private val readTimeoutMs: Int = 60_000,
    private val httpFactory: ReactiveHttpClientFactory = ReactiveHttpClientFactory(connectTimeoutMs, readTimeoutMs),
) : GrafanaSearchPort {
    private val log = LoggerFactory.getLogger(GrafanaClient::class.java)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DashboardSearchItem(
        val id: Long = 0,
        val uid: String = "",
        val title: String = "",
        val type: String = "",
        val url: String = "",
        @JsonProperty("folderTitle") val folderTitle: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DashboardWrapper(
        val dashboard: JsonNode? = null,
        val meta: DashboardMeta? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DashboardMeta(
        val url: String? = null,
        val folderTitle: String? = null,
    )

    override fun searchKeyword(
        config: GrafanaConfig,
        keyword: String,
        sessionId: Long,
        dashboardFilters: List<String>,
    ): List<SearchResult> {
        val client = buildClient(config)
        val results = mutableListOf<SearchResult>()
        val normalizedFilters = dashboardFilters.map { it.trim().lowercase() }.filter { it.isNotBlank() }

        log.info("[Grafana:{}] Searching '{}'", config.name, keyword)

        val dashboards = searchDashboards(client, keyword)
        log.debug("[Grafana:{}] Found {} dashboard hits via title search", config.name, dashboards.size)

        for (dash in dashboards) {
            if (!matchesDashboardFilter(dash, normalizedFilters)) continue
            if (!dash.title.contains(keyword, ignoreCase = true)) continue
            results +=
                SearchResult(
                    sessionId = sessionId,
                    keyword = keyword,
                    sourceType = SourceType.GRAFANA,
                    resultType = ResultType.GRAFANA_DASHBOARD,
                    configName = config.name,
                    repoName = dash.title,
                    repoUrl = "${config.baseUrl.trimEnd('/')}${dash.url}",
                    filePath = null,
                    className = dash.folderTitle,
                    codeSnippet = "Dashboard '${dash.title}' contains the keyword in its title",
                )
        }

        val allDashboardsToScan =
            if (dashboards.size < 200) {
                val all = searchDashboards(client, "")
                (dashboards + all).distinctBy { it.uid }
            } else {
                dashboards
            }

        val dashboardsToScan =
            if (normalizedFilters.isNotEmpty()) {
                allDashboardsToScan.filter { matchesDashboardFilter(it, normalizedFilters) }
            } else {
                allDashboardsToScan
            }

        for (dash in dashboardsToScan.take(100)) {
            val panelResults = scanDashboardPanels(client, config, dash, keyword, sessionId)
            results += panelResults
        }

        return results.distinctBy { resultKey(it) }
    }

    internal fun resultKey(r: SearchResult): String = "${r.repoName}|${r.filePath}|${r.codeSnippet.orEmpty().take(80)}"

    internal fun searchDashboards(
        client: WebClient,
        query: String,
    ): List<DashboardSearchItem> {
        val uri =
            if (query.isBlank()) {
                "/api/search?type=dash-db&limit=500"
            } else {
                "/api/search?query=${encode(query)}&type=dash-db&limit=500"
            }

        return client
            .get()
            .uri(uri)
            .retrieve()
            .bodyToMono<List<DashboardSearchItem>>()
            .onErrorResume { Mono.just(emptyList()) }
            .block() ?: emptyList()
    }

    internal fun scanDashboardPanels(
        client: WebClient,
        config: GrafanaConfig,
        dash: DashboardSearchItem,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        val wrapper =
            client
                .get()
                .uri("/api/dashboards/uid/${dash.uid}")
                .retrieve()
                .bodyToMono<DashboardWrapper>()
                .onErrorResume { Mono.empty() }
                .block() ?: return results

        val dashboardJson = wrapper.dashboard ?: return results
        val dashUrl = "${config.baseUrl.trimEnd('/')}${dash.url}"

        val dashDesc = dashboardJson.path("description").asText("")
        if (dashDesc.contains(keyword, ignoreCase = true)) {
            results +=
                SearchResult(
                    sessionId = sessionId,
                    keyword = keyword,
                    sourceType = SourceType.GRAFANA,
                    resultType = ResultType.GRAFANA_DASHBOARD,
                    configName = config.name,
                    repoName = dash.title,
                    repoUrl = dashUrl,
                    className = dash.folderTitle,
                    codeSnippet = "Beschreibung: ${dashDesc.take(500)}",
                )
        }

        val panels = dashboardJson.path("panels")
        if (panels.isArray) {
            for (panel in panels) {
                results += scanPanel(panel, config, dash, keyword, sessionId, dashUrl)

                val rowPanels = panel.path("panels")
                if (rowPanels.isArray) {
                    for (rp in rowPanels) {
                        results += scanPanel(rp, config, dash, keyword, sessionId, dashUrl)
                    }
                }
            }
        }

        return results
    }

    internal fun scanPanel(
        panel: JsonNode,
        config: GrafanaConfig,
        dash: DashboardSearchItem,
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
                    repoName = dash.title,
                    repoUrl = dashUrl,
                    filePath = panelTitle.ifBlank { null },
                    className = dash.folderTitle,
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
                        val querySnippet = extractQuerySnippet(target, keyword)
                        results +=
                            SearchResult(
                                sessionId = sessionId,
                                keyword = keyword,
                                sourceType = SourceType.GRAFANA,
                                resultType = ResultType.GRAFANA_PANEL,
                                configName = config.name,
                                repoName = dash.title,
                                repoUrl = dashUrl,
                                filePath = panelTitle.ifBlank { "Panel (no title)" },
                                className = dash.folderTitle,
                                functionName = panelType.ifBlank { null },
                                codeSnippet = querySnippet,
                            )
                    }
                }
            }
        }

        return results
    }

    internal fun extractQuerySnippet(
        target: JsonNode,
        keyword: String,
    ): String {
        val candidates =
            listOf(
                "expr",
                "query",
                "rawQuery",
                "rawSql",
                "measurement",
                "metricName",
                "legendFormat",
                "refId",
                "target",
            )
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

    internal fun buildClient(config: GrafanaConfig): WebClient =
        httpFactory.buildBasicAuth(
            baseUrl = config.baseUrl,
            username = config.username,
            password = config.password,
        ) {
            defaultHeader("X-Grafana-Org-Id", config.orgId.toString())
        }

    internal fun encode(s: String) = KeywordContext.encode(s)

    internal fun matchesDashboardFilter(
        dash: DashboardSearchItem,
        filters: List<String>,
    ): Boolean {
        if (filters.isEmpty()) return true
        val url = dash.url.lowercase()
        val title = dash.title.lowercase()
        val uid = dash.uid.lowercase()
        return filters.any { f ->
            val n = f.lowercase()
            url.contains(n) || title.contains(n) || uid == n || n.contains("/$uid/") || n.endsWith("/$uid")
        }
    }
}
