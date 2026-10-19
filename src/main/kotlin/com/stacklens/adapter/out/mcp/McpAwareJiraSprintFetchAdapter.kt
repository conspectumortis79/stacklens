package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.JiraSprintIssueFieldResolver
import com.stacklens.adapter.out.shared.JiraSprintIssueItem
import com.stacklens.application.port.out.LinkedIssue
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.shared.sprintIdFromRegex
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component("mcpAwareJiraSprintFetchPort")
open class McpAwareJiraSprintFetchAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @Qualifier("jiraSprintFetchPort")
    private val delegate: JiraSprintFetchPort,
) : JiraSprintFetchPort {
    private val log = LoggerFactory.getLogger(McpAwareJiraSprintFetchAdapter::class.java)

    private val fieldResolver = JiraSprintIssueFieldResolver()

    internal companion object {
        val SPRINT_ID_REGEX = Regex("""/sprints?/(\d+)""", RegexOption.IGNORE_CASE)
        val SPRINT_PARAM_REGEX = Regex("""[?&](?:sprint|selectedSprint|sprints?)=(\d+)""", RegexOption.IGNORE_CASE)
        val BOARD_ID_REGEX = Regex("""/boards?/(\d+)""", RegexOption.IGNORE_CASE)
    }

    override fun fetchSprintInfo(
        config: JiraConfig,
        sprintUrl: String,
    ): SprintInfo =
        runWithMcp(config, sprintUrl) { mcp, sprintId ->
            mapSprintInfo(callJsonOrThrow(mcp, "jira_get_sprint", mapOf("sprintId" to sprintId)))
        } ?: delegate.fetchSprintInfo(config, sprintUrl)

    override fun fetchSprintIssues(
        config: JiraConfig,
        sprintUrl: String,
    ): List<SprintIssue> =
        runWithMcp(config, sprintUrl) { mcp, sprintId ->
            fetchAllSprintIssues(mcp, config, sprintId)
        } ?: delegate.fetchSprintIssues(config, sprintUrl)

    override fun fetchSprintData(
        config: JiraConfig,
        sprintUrl: String,
    ): Pair<SprintInfo, List<SprintIssue>> =
        runWithMcp(config, sprintUrl) { mcp, sprintId ->
            val info = mapSprintInfo(callJsonOrThrow(mcp, "jira_get_sprint", mapOf("sprintId" to sprintId)))
            val issues = fetchAllSprintIssues(mcp, config, sprintId)
            info to issues
        } ?: delegate.fetchSprintData(config, sprintUrl)

    private fun <T> runWithMcp(
        config: JiraConfig,
        sprintUrl: String,
        block: (com.stacklens.domain.model.McpServerConfig, Long) -> T,
    ): T? {
        val mcp = McpRouting.activeIfRouted(mcpRepo) { McpRouting.jiraReady(it) } ?: return null
        if (!config.baseUrl.trimEnd('/').equals(mcp.jiraBaseUrl.trimEnd('/'), ignoreCase = true)) {
            log.debug(
                "[MCP→Jira-Sprint] config '{}' targets '{}' but MCP is configured for '{}', using direct adapter",
                config.name,
                config.baseUrl,
                mcp.jiraBaseUrl,
            )
            return null
        }
        return try {
            val sprintId = resolveSprintId(mcp, sprintUrl)
            block(mcp, sprintId)
        } catch (e: Exception) {
            log.warn("[MCP] sprint fetch failed, falling back: {}", e.message)
            null
        }
    }

    private fun resolveSprintId(
        mcp: com.stacklens.domain.model.McpServerConfig,
        sprintUrl: String,
    ): Long {
        sprintIdFromRegex(SPRINT_ID_REGEX, sprintUrl)?.let { return it }
        sprintIdFromRegex(SPRINT_PARAM_REGEX, sprintUrl)?.let { return it }
        val boardId =
            sprintIdFromRegex(BOARD_ID_REGEX, sprintUrl)
                ?: throw IllegalArgumentException("Cannot resolve board/sprint id from: $sprintUrl")
        val active = callJsonOrThrow(mcp, "jira_get_active_sprint", mapOf("boardId" to boardId))
        val values = active.path("values")
        val firstValue = if (values.isArray) values.firstOrNull() else null
        val firstId = firstValue?.path("id")?.asLong()
        return firstId ?: throw IllegalStateException("No active sprint for board $boardId")
    }

    private fun fetchAllSprintIssues(
        mcp: com.stacklens.domain.model.McpServerConfig,
        config: JiraConfig,
        sprintId: Long,
    ): List<SprintIssue> {
        val collected = mutableListOf<SprintIssue>()
        var startAt = 0
        val pageSize = 100
        while (true) {
            val page =
                callJsonOrThrow(
                    mcp,
                    "jira_list_sprint_issues",
                    mapOf("sprintId" to sprintId, "startAt" to startAt, "maxResults" to pageSize),
                )
            val issues = page.path("issues")
            if (!issues.isArray || issues.isEmpty) break
            issues.forEach { collected += mapSprintIssue(config, it) }
            val total = page.path("total").asInt(collected.size)
            startAt += issues.size()
            if (startAt >= total || issues.size() < pageSize) break
        }
        return collected
    }

    private fun mapSprintInfo(node: JsonNode): SprintInfo =
        SprintInfo(
            id = node.path("id").asLong(),
            name = node.path("name").asText(""),
            state = node.path("state").asText(""),
            startDate = node.path("startDate").asText(null),
            endDate = node.path("endDate").asText(null),
            goal = node.path("goal").asText(null),
        )

    private fun mapSprintIssue(
        config: JiraConfig,
        node: JsonNode,
    ): SprintIssue {
        val key = node.path("key").asText("")
        val fields = node.path("fields")
        val item = objectMapper.treeToValue(node, JiraSprintIssueItem::class.java)
        val description =
            when {
                fields.path("description").isTextual -> fields.path("description").asText("")
                fields.path("description").isObject -> fields.path("description").toString()
                else -> ""
            }
        val labels = fields.path("labels").mapNotNull { it.asText().takeIf(String::isNotBlank) }
        val comments =
            fields
                .path("comment")
                .path("comments")
                .take(5)
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
        return SprintIssue(
            key = key,
            summary = fields.path("summary").asText(""),
            description = description,
            issueType = fields.path("issuetype").path("name").asText(""),
            status = fields.path("status").path("name").asText(""),
            priority =
                fields
                    .path("priority")
                    .path("name")
                    .asText("")
                    .takeIf(String::isNotBlank),
            assignee =
                fields
                    .path("assignee")
                    .path("displayName")
                    .asText("")
                    .takeIf(String::isNotBlank),
            labels = labels,
            comments = comments,
            url = "${config.baseUrl.trimEnd('/')}/browse/$key",
            createdAt = fields.path("created").asText("").takeIf(String::isNotBlank),
            startedAt = fieldResolver.resolveStartedTimestamp(item),
            resolvedAt = fieldResolver.resolveDoneTimestamp(item),
            qaLoopCount = fieldResolver.resolveQaLoopCount(item),
            linkedIssues = mapLinkedIssues(item),
        )
    }

    private fun mapLinkedIssues(item: JiraSprintIssueItem): List<LinkedIssue> =
        item.fields.issuelinks?.mapNotNull { link ->
            val ref = link.inwardIssue ?: link.outwardIssue ?: return@mapNotNull null
            val relationship =
                if (link.inwardIssue != null) {
                    link.type?.inward ?: "relates to"
                } else {
                    link.type?.outward ?: "relates to"
                }
            val linkedKey = ref.key ?: return@mapNotNull null
            LinkedIssue(
                key = linkedKey,
                summary = ref.fields?.summary ?: "",
                relationship = relationship,
                status = ref.fields?.status?.name,
            )
        } ?: emptyList()

    private fun callJsonOrThrow(
        mcp: com.stacklens.domain.model.McpServerConfig,
        tool: String,
        args: Map<String, Any?>,
    ): JsonNode {
        val res = gateway.callTool(mcp, tool, args)
        if (res.isError) throw IllegalStateException("$tool error: ${res.text}")
        return McpRouting.parseJsonOrNull(objectMapper, res.text)
            ?: throw IllegalStateException("$tool returned non-JSON")
    }
}
