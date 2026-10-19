package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.JiraProblemAnalysis
import com.stacklens.application.port.out.JiraSimilarTicket
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.jira.JiraTicketFetchPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.text.AcceptanceCriteriaExtractor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component("mcpAwareJiraTicketFetchPort")
open class McpAwareJiraTicketFetchAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @Qualifier("jiraTicketFetchPort")
    private val delegate: JiraTicketFetchPort,
) : JiraTicketFetchPort {
    private val log = LoggerFactory.getLogger(McpAwareJiraTicketFetchAdapter::class.java)

    override fun fetchTicketByKey(
        config: JiraConfig,
        key: String,
    ): JiraTicketData {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.jiraReady(it) }
                ?: return delegate.fetchTicketByKey(config, key)
        if (!config.baseUrl.trimEnd('/').equals(mcp.jiraBaseUrl.trimEnd('/'), ignoreCase = true)) {
            log.debug(
                "[MCP→Jira] config '{}' targets '{}' but MCP is configured for '{}', using direct adapter",
                config.name,
                config.baseUrl,
                mcp.jiraBaseUrl,
            )
            return delegate.fetchTicketByKey(config, key)
        }
        return try {
            val result = gateway.callTool(mcp, "jira_get_issue", mapOf("key" to key))
            if (result.isError) {
                log.warn("[MCP] jira_get_issue error, falling back: {}", result.text)
                return delegate.fetchTicketByKey(config, key)
            }
            val node =
                McpRouting.parseJsonOrNull(objectMapper, result.text)
                    ?: return delegate.fetchTicketByKey(config, key)
            mapJiraIssue(config, key, node)
        } catch (e: Exception) {
            log.warn("[MCP] jira_get_issue exception, falling back: {}", e.message)
            delegate.fetchTicketByKey(config, key)
        }
    }

    override fun searchSimilarTickets(
        config: JiraConfig,
        originalTicket: JiraTicketData,
        problem: JiraProblemAnalysis,
    ): List<JiraSimilarTicket> = delegate.searchSimilarTickets(config, originalTicket, problem)

    private fun mapJiraIssue(
        config: JiraConfig,
        key: String,
        node: JsonNode,
    ): JiraTicketData {
        val fields = node.path("fields")
        val description =
            when {
                fields.path("description").isTextual -> fields.path("description").asText("")
                fields.path("description").isObject -> fields.path("description").toString()
                else -> ""
            }
        val labels = fields.path("labels").mapNotNull { it.asText().takeIf(String::isNotBlank) }
        val components =
            fields
                .path("components")
                .mapNotNull { it.path("name").asText().takeIf(String::isNotBlank) }
        val comments =
            fields
                .path("comment")
                .path("comments")
                .joinToString("\n---\n") { c ->
                    val author = c.path("author").path("displayName").asText("")
                    val body =
                        when {
                            c.path("body").isTextual -> c.path("body").asText()
                            c.path("body").isObject -> c.path("body").toString()
                            else -> ""
                        }
                    if (author.isBlank()) body else "$author: $body"
                }
        return JiraTicketData(
            key = node.path("key").asText(key),
            summary = fields.path("summary").asText(""),
            description = description,
            comments = comments,
            issueType =
                fields
                    .path("issuetype")
                    .path("name")
                    .asText(""),
            status =
                fields
                    .path("status")
                    .path("name")
                    .asText(""),
            priority =
                fields
                    .path("priority")
                    .path("name")
                    .asText("")
                    .takeIf(String::isNotBlank),
            labels = labels,
            components = components,
            url = "${config.baseUrl.trimEnd('/')}/browse/$key",
            acceptanceCriteria = AcceptanceCriteriaExtractor.extract(description),
        )
    }
}

@Primary
@Component("mcpAwareMrFetchPort")
open class McpAwareMrFetchAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @Qualifier("mrFetchPort")
    private val delegate: MrFetchPort,
) : MrFetchPort {
    private val log = LoggerFactory.getLogger(McpAwareMrFetchAdapter::class.java)

    override fun fetchMrData(
        mrUrl: String,
        gitlabConfigs: List<GitlabConfig>,
        githubConfigs: List<GithubRepoConfig>,
    ): MrFetchPort.MrFetchResult {
        val gitlab = parseGitlabUrl(mrUrl)
        if (gitlab != null) {
            val mcp = McpRouting.activeIfRouted(mcpRepo) { McpRouting.gitlabReady(it) }
            if (mcp != null) {
                fetchGitlabViaMcp(mcp, mrUrl, gitlab.first, gitlab.second)
                    ?.let { return MrFetchPort.MrFetchResult.Success(it) }
            }
        }
        val github = parseGithubUrl(mrUrl)
        if (github != null) {
            val mcp = McpRouting.activeIfRouted(mcpRepo) { McpRouting.githubReady(it) }
            if (mcp != null) {
                fetchGithubViaMcp(mcp, mrUrl, github.first, github.second, github.third)
                    ?.let { return MrFetchPort.MrFetchResult.Success(it) }
            }
        }
        return delegate.fetchMrData(mrUrl, gitlabConfigs, githubConfigs)
    }

    private fun fetchGitlabViaMcp(
        mcp: com.stacklens.domain.model.McpServerConfig,
        mrUrl: String,
        projectPath: String,
        iid: String,
    ): MrFetchPort.MrData? =
        try {
            val mr = gateway.callTool(mcp, "gitlab_get_merge_request", mapOf("idOrPath" to projectPath, "iid" to iid))
            if (mr.isError) {
                log.warn("[MCP] gitlab_get_merge_request error, falling back: {}", mr.text)
                null
            } else {
                val mrNode = McpRouting.parseJsonOrNull(objectMapper, mr.text) ?: return null
                val changes =
                    gateway.callTool(mcp, "gitlab_get_mr_changes", mapOf("idOrPath" to projectPath, "iid" to iid))
                val changesNode =
                    if (changes.isError) {
                        null
                    } else {
                        McpRouting.parseJsonOrNull(objectMapper, changes.text)
                    }
                mapGitlabMr(mrUrl, mrNode, changesNode)
            }
        } catch (e: Exception) {
            log.warn("[MCP] gitlab MR via MCP failed, falling back: {}", e.message)
            null
        }

    private fun fetchGithubViaMcp(
        mcp: com.stacklens.domain.model.McpServerConfig,
        mrUrl: String,
        owner: String,
        repo: String,
        number: String,
    ): MrFetchPort.MrData? =
        try {
            val pr =
                gateway.callTool(
                    mcp,
                    "github_get_pull_request",
                    mapOf("owner" to owner, "repo" to repo, "number" to number),
                )
            if (pr.isError) {
                log.warn("[MCP] github_get_pull_request error, falling back: {}", pr.text)
                null
            } else {
                val prNode = McpRouting.parseJsonOrNull(objectMapper, pr.text) ?: return null
                val files =
                    gateway.callTool(
                        mcp,
                        "github_get_pr_files",
                        mapOf("owner" to owner, "repo" to repo, "number" to number),
                    )
                val filesNode =
                    if (files.isError) {
                        null
                    } else {
                        McpRouting.parseJsonOrNull(objectMapper, files.text)
                    }
                mapGithubPr(mrUrl, prNode, filesNode)
            }
        } catch (e: Exception) {
            log.warn("[MCP] github PR via MCP failed, falling back: {}", e.message)
            null
        }

    private fun mapGitlabMr(
        mrUrl: String,
        mr: JsonNode,
        changes: JsonNode?,
    ): MrFetchPort.MrData {
        val changesArr =
            when {
                changes == null -> null
                changes.isArray -> changes
                changes.path("changes").isArray -> changes.path("changes")
                else -> null
            }
        val files = mutableListOf<String>()
        val diffBuilder = StringBuilder()
        changesArr?.forEach { c ->
            val newPath = c.path("new_path").asText("")
            val oldPath = c.path("old_path").asText("")
            val path = newPath.ifBlank { oldPath }
            if (path.isNotBlank()) files += path
            val diff = c.path("diff").asText("")
            if (diff.isNotBlank()) {
                diffBuilder.append("--- ").append(oldPath).append('\n')
                diffBuilder.append("+++ ").append(newPath).append('\n')
                diffBuilder.append(diff).append('\n')
            }
        }
        val refs = changes?.path("diff_refs") ?: mr.path("diff_refs")
        return MrFetchPort.MrData(
            mrUrl = mrUrl,
            platform = "gitlab",
            title = mr.path("title").asText(""),
            description = mr.path("description").asText(""),
            sourceBranch = mr.path("source_branch").asText(""),
            targetBranch = mr.path("target_branch").asText(""),
            authorName = mr.path("author").path("name").asText(""),
            state = mr.path("state").asText(""),
            diffText = diffBuilder.toString(),
            changedFiles = files,
            diffBaseSha = refs.path("base_sha").asText(""),
            diffStartSha = refs.path("start_sha").asText(""),
            diffHeadSha = refs.path("head_sha").asText(""),
        )
    }

    private fun mapGithubPr(
        mrUrl: String,
        pr: JsonNode,
        files: JsonNode?,
    ): MrFetchPort.MrData {
        val filesArr =
            when {
                files == null -> null
                files.isArray -> files
                else -> null
            }
        val changedFiles = mutableListOf<String>()
        val diffBuilder = StringBuilder()
        filesArr?.forEach { f ->
            val name = f.path("filename").asText("")
            if (name.isNotBlank()) changedFiles += name
            val patch = f.path("patch").asText("")
            if (patch.isNotBlank()) {
                diffBuilder.append("--- a/").append(name).append('\n')
                diffBuilder.append("+++ b/").append(name).append('\n')
                diffBuilder.append(patch).append('\n')
            }
        }
        return MrFetchPort.MrData(
            mrUrl = mrUrl,
            platform = "github",
            title = pr.path("title").asText(""),
            description = pr.path("body").asText(""),
            sourceBranch = pr.path("head").path("ref").asText(""),
            targetBranch = pr.path("base").path("ref").asText(""),
            authorName = pr.path("user").path("login").asText(""),
            state = pr.path("state").asText(""),
            diffText = diffBuilder.toString(),
            changedFiles = changedFiles,
            diffBaseSha = pr.path("base").path("sha").asText(""),
            diffStartSha = pr.path("base").path("sha").asText(""),
            diffHeadSha = pr.path("head").path("sha").asText(""),
        )
    }

    private fun parseGitlabUrl(url: String): Pair<String, String>? {
        val regex = Regex("""https?://[^/]+/(.+?)/-/merge_requests/(\d+)""")
        val m = regex.find(url) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }

    private fun parseGithubUrl(url: String): Triple<String, String, String>? {
        val regex = Regex("""https?://github\.com/([^/]+)/([^/]+)/pull/(\d+)""")
        val m = regex.find(url) ?: return null
        return Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }
}
