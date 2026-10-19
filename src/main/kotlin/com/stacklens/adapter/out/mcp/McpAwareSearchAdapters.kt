package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.search.ConfluenceSearchPort
import com.stacklens.application.port.out.search.JiraSearchPort
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.shared.confluenceCloudPageUrl
import org.slf4j.LoggerFactory

internal object McpRouting {
    private val log = LoggerFactory.getLogger(McpRouting::class.java)

    fun activeIfRouted(
        repo: McpServerConfigPort,
        predicate: (McpServerConfig) -> Boolean,
    ): McpServerConfig? {
        val cfg =
            try {
                repo.findFirstActiveMcpServerConfig()
            } catch (e: Exception) {
                log.debug("[MCP] failed to look up active server: {}", e.message)
                null
            } ?: return null
        return if (cfg.active && predicate(cfg)) cfg else null
    }

    fun jiraReady(cfg: McpServerConfig) = cfg.routeJira && cfg.jiraBaseUrl.isNotBlank() && cfg.jiraApiToken.isNotBlank()

    fun confluenceReady(cfg: McpServerConfig) = cfg.routeConfluence && cfg.confluenceBaseUrl.isNotBlank() && cfg.confluenceApiToken.isNotBlank()

    fun gitlabReady(cfg: McpServerConfig) = cfg.routeGitlab && cfg.gitlabAccessToken.isNotBlank()

    fun githubReady(cfg: McpServerConfig) = cfg.routeGithub && cfg.githubToken.isNotBlank()

    fun grafanaReady(cfg: McpServerConfig): Boolean {
        if (!cfg.routeGrafana) return false
        return cfg.grafanaBaseUrl.isNotBlank()
    }

    fun parseJsonOrNull(
        objectMapper: ObjectMapper,
        text: String,
    ): JsonNode? =
        try {
            objectMapper.readTree(text)
        } catch (e: Exception) {
            log.debug("[MCP] response is not JSON: {}", e.message)
            null
        }
}

@org.springframework.context.annotation.Primary
@org.springframework.stereotype.Component("mcpAwareJiraSearchPort")
open class McpAwareJiraSearchAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @org.springframework.beans.factory.annotation.Qualifier("jiraSearchPort")
    private val delegate: JiraSearchPort,
) : JiraSearchPort {
    private val log = LoggerFactory.getLogger(McpAwareJiraSearchAdapter::class.java)

    override fun searchKeyword(
        config: JiraConfig,
        keyword: String,
        projectFilters: List<String>,
        sessionId: Long,
        componentFilters: List<String>,
        labelFilters: List<String>,
    ): List<SearchResult> {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.jiraReady(it) }
                ?: return delegate.searchKeyword(config, keyword, projectFilters, sessionId, componentFilters, labelFilters)

        if (!config.baseUrl.trimEnd('/').equals(mcp.jiraBaseUrl.trimEnd('/'), ignoreCase = true)) {
            log.debug(
                "[MCP\u2192Jira] config '{}' targets '{}' but MCP is configured for '{}', using direct adapter",
                config.name,
                config.baseUrl,
                mcp.jiraBaseUrl,
            )
            return delegate.searchKeyword(config, keyword, projectFilters, sessionId, componentFilters, labelFilters)
        }

        return try {
            val jql = buildJql(projectFilters, keyword, componentFilters, labelFilters)
            log.info("[MCP→Jira:{}] JQL via MCP: {}", config.name, jql)
            val res =
                gateway.callTool(
                    mcp,
                    "jira_search_issues",
                    mapOf("jql" to jql, "maxResults" to 50),
                )
            if (res.isError) {
                log.warn("[MCP→Jira] tool error, falling back to direct adapter: {}", res.text)
                return delegate.searchKeyword(config, keyword, projectFilters, sessionId, componentFilters, labelFilters)
            }
            mapJiraIssuesToSearchResults(res.text, config, keyword, sessionId)
        } catch (e: Exception) {
            log.warn("[MCP→Jira] unexpected failure, falling back: {}", e.message)
            delegate.searchKeyword(config, keyword, projectFilters, sessionId, componentFilters, labelFilters)
        }
    }

    internal fun buildJql(
        projects: List<String>,
        keyword: String,
        components: List<String>,
        labels: List<String>,
    ): String {
        val parts = mutableListOf<String>()
        val keys = projects.map { it.trim().uppercase() }.filter { it.isNotBlank() }
        if (keys.isNotEmpty()) parts += "project in (${keys.joinToString(",") { "\"$it\"" }})"
        val esc = keyword.replace("\"", "\\\"")
        parts += "(text ~ \"$esc\" OR summary ~ \"$esc\")"
        if (components.isNotEmpty()) parts += "component in (${components.joinToString(",") { "\"$it\"" }})"
        if (labels.isNotEmpty()) parts += "labels in (${labels.joinToString(",") { "\"$it\"" }})"
        return parts.joinToString(" AND ") + " ORDER BY updated DESC"
    }

    internal fun mapJiraIssuesToSearchResults(
        text: String,
        config: JiraConfig,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult> {
        val node = McpRouting.parseJsonOrNull(objectMapper, text) ?: return emptyList()
        val issues = node.path("issues")
        if (!issues.isArray) return emptyList()
        val baseUrl = config.baseUrl.trimEnd('/')
        return issues.mapNotNull { issue ->
            val key = issue.path("key").asText("").ifBlank { return@mapNotNull null }
            val fields = issue.path("fields")
            val summary = fields.path("summary").asText("")
            val type = fields.path("issuetype").path("name").asText("")
            val status = fields.path("status").path("name").asText("")
            val project = fields.path("project").path("key").asText(key.substringBefore('-', ""))
            SearchResult(
                sessionId = sessionId,
                keyword = keyword,
                sourceType = SourceType.JIRA,
                resultType = ResultType.JIRA_ISSUE,
                configName = config.name,
                repoName = project,
                filePath = key,
                repoUrl = "$baseUrl/browse/$key",
                className = type,
                functionName = status,
                codeSnippet = summary,
            )
        }
    }
}

@org.springframework.context.annotation.Primary
@org.springframework.stereotype.Component("mcpAwareConfluenceSearchPort")
open class McpAwareConfluenceSearchAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @org.springframework.beans.factory.annotation.Qualifier("confluenceSearchPort")
    private val delegate: ConfluenceSearchPort,
) : ConfluenceSearchPort {
    private val log = LoggerFactory.getLogger(McpAwareConfluenceSearchAdapter::class.java)

    override fun searchKeyword(
        config: ConfluenceConfig,
        keyword: String,
        websiteFilters: List<String>,
        sessionId: Long,
        offset: Int,
    ): List<SearchResult> {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.confluenceReady(it) }
                ?: return delegate.searchKeyword(config, keyword, websiteFilters, sessionId, offset)

        if (!config.baseUrl.trimEnd('/').equals(mcp.confluenceBaseUrl.trimEnd('/'), ignoreCase = true)) {
            log.debug(
                "[MCP\u2192Confluence] config '{}' targets '{}' but MCP is configured for '{}', using direct adapter",
                config.name,
                config.baseUrl,
                mcp.confluenceBaseUrl,
            )
            return delegate.searchKeyword(config, keyword, websiteFilters, sessionId, offset)
        }

        if (offset > 0) {
            return delegate.searchKeyword(config, keyword, websiteFilters, sessionId, offset)
        }

        return try {
            val cql = buildCql(keyword, websiteFilters)
            log.info("[MCP→Confluence:{}] CQL via MCP: {}", config.name, cql)
            val res = gateway.callTool(mcp, "confluence_search", mapOf("cql" to cql, "limit" to 25))
            if (res.isError) {
                log.warn("[MCP→Confluence] tool error, falling back: {}", res.text)
                return delegate.searchKeyword(config, keyword, websiteFilters, sessionId, offset)
            }
            mapToSearchResults(res.text, config, keyword, sessionId)
        } catch (e: Exception) {
            log.warn("[MCP→Confluence] unexpected failure, falling back: {}", e.message)
            delegate.searchKeyword(config, keyword, websiteFilters, sessionId, offset)
        }
    }

    internal fun buildCql(
        keyword: String,
        spaces: List<String>,
    ): String {
        val esc = keyword.replace("\"", "\\\"")
        val parts = mutableListOf("text ~ \"$esc\"")
        val keys = spaces.map { it.trim() }.filter { it.isNotBlank() }
        if (keys.isNotEmpty()) parts += "space in (${keys.joinToString(",") { "\"$it\"" }})"
        return parts.joinToString(" AND ")
    }

    internal fun mapToSearchResults(
        text: String,
        config: ConfluenceConfig,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult> {
        val node = McpRouting.parseJsonOrNull(objectMapper, text) ?: return emptyList()
        val results = node.path("results")
        if (!results.isArray) return emptyList()
        val base = config.baseUrl.trimEnd('/')
        return results.mapNotNull { item ->
            val title = item.path("title").asText("").ifBlank { return@mapNotNull null }
            val spaceName = item.path("space").path("name").asText(item.path("space").path("key").asText(""))
            val webui = item.path("_links").path("webui").asText("")
            val excerpt = item.path("excerpt").asText("")
            val spaceKey = item.path("space").path("key").asText("")
            val pageId = item.path("id").asText("")
            val repoUrl =
                when {
                    webui.startsWith("http") -> webui
                    config.cloudMode && spaceKey.isNotBlank() && pageId.isNotBlank() ->
                        confluenceCloudPageUrl(config.baseUrl, spaceKey, pageId)
                    else -> "$base$webui"
                }
            SearchResult(
                sessionId = sessionId,
                keyword = keyword,
                sourceType = SourceType.CONFLUENCE,
                resultType = ResultType.CONFLUENCE_PAGE,
                configName = config.name,
                repoName = spaceName,
                filePath = title,
                repoUrl = repoUrl,
                codeSnippet = excerpt,
            )
        }
    }
}
