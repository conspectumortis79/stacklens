package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.ResolvedSprint
import com.stacklens.application.port.out.SprintReviewAdfDocument
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SprintReviewRow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component("mcpAwareSprintReviewJiraPort")
open class McpAwareSprintReviewJiraAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @Qualifier("sprintReviewNativeJiraPort")
    private val delegate: SprintReviewJiraPort,
) : SprintReviewJiraPort {
    private val log = LoggerFactory.getLogger(McpAwareSprintReviewJiraAdapter::class.java)

    override fun resolveSprintByName(
        config: JiraConfig?,
        sprintName: String,
    ): ResolvedSprint = delegate.resolveSprintByName(config, sprintName)

    override fun fetchSprintTickets(
        config: JiraConfig?,
        sprintId: Long,
    ): List<SprintReviewTicket> {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.jiraReady(it) }
                ?: return delegate.fetchSprintTickets(config, sprintId)

        if (config != null &&
            !config.baseUrl.trimEnd('/').equals(mcp.jiraBaseUrl.trimEnd('/'), ignoreCase = true)
        ) {
            log.debug(
                "[MCP→SprintReview] config '{}' targets '{}' but MCP is configured for '{}', using direct adapter",
                config.name,
                config.baseUrl,
                mcp.jiraBaseUrl,
            )
            return delegate.fetchSprintTickets(config, sprintId)
        }
        return try {
            val collected = mutableListOf<SprintReviewTicket>()
            var startAt = 0
            val pageSize = 50
            while (true) {
                val r =
                    gateway.callTool(
                        mcp,
                        "jira_list_sprint_issues",
                        mapOf("sprintId" to sprintId, "startAt" to startAt, "maxResults" to pageSize),
                    )
                if (r.isError) throw IllegalStateException(r.text)
                val node =
                    McpRouting.parseJsonOrNull(objectMapper, r.text)
                        ?: throw IllegalStateException("invalid MCP response for jira_list_sprint_issues")
                val issues = node.path("issues")
                if (!issues.isArray || issues.isEmpty) break
                for (issue in issues) {
                    if (issue
                            .path("fields")
                            .path("issuetype")
                            .path("subtask")
                            .asBoolean(false)
                    ) {
                        continue
                    }
                    collected += mapIssue(issue, config)
                }
                val total = node.path("total").asInt(collected.size)
                startAt += issues.size()
                if (startAt >= total || issues.size() < pageSize) break
            }
            collected
        } catch (e: Exception) {
            log.warn("[MCP] sprint-review fetchSprintTickets failed, falling back: {}", e.message)
            delegate.fetchSprintTickets(config, sprintId)
        }
    }

    override fun renderAdfDocument(
        rows: List<SprintReviewRow>,
        includeImprovement: Boolean,
        sprintName: String,
    ): SprintReviewAdfDocument = delegate.renderAdfDocument(rows, includeImprovement, sprintName)

    override fun appendAdfToDescription(
        config: JiraConfig?,
        targetKey: String,
        adfDocument: SprintReviewAdfDocument,
    ): String = delegate.appendAdfToDescription(config, targetKey, adfDocument)

    private fun mapIssue(
        node: com.fasterxml.jackson.databind.JsonNode,
        config: JiraConfig?,
    ): SprintReviewTicket {
        val key = node.path("key").asText("")
        val fields = node.path("fields")
        val summary = fields.path("summary").asText("")
        val description =
            when {
                fields.path("description").isTextual -> fields.path("description").asText()
                fields.path("description").isObject -> fields.path("description").toString()
                else -> ""
            }
        val labels =
            fields
                .path("labels")
                .mapNotNull { it.asText("").takeIf { v -> v.isNotBlank() } }
        val baseUrl = config?.baseUrl?.trimEnd('/').orEmpty()
        val storyPoints = parseStoryPointsWithFallbacks(fields)
        val numericCustomFields = extractNumericCustomFields(fields)
        return SprintReviewTicket(
            key = key,
            summary = summary,
            description = description,
            url = if (baseUrl.isNotBlank()) "$baseUrl/browse/$key" else "",
            issueType = fields.path("issuetype").path("name").asText(""),
            status = fields.path("status").path("name").asText(""),
            assignee =
                fields
                    .path("assignee")
                    .path("displayName")
                    .asText("")
                    .takeIf { it.isNotBlank() },
            priority =
                fields
                    .path("priority")
                    .path("name")
                    .asText("")
                    .takeIf { it.isNotBlank() },
            labels = labels,
            storyPoints = storyPoints,
            numericCustomFields = numericCustomFields,
        )
    }

    private fun parseStoryPointsWithFallbacks(fields: com.fasterxml.jackson.databind.JsonNode): Double? {
        val fallbackIds =
            listOf(
                "customfield_10016",
                "customfield_10026",
                "customfield_10004",
                "customfield_10002",
                "customfield_10028",
            )
        for (id in fallbackIds) {
            val node = fields.path(id)
            if (node.isMissingNode || node.isNull) continue
            if (node.isNumber) return node.asDouble()
            if (node.isObject) {
                for (innerKey in listOf("value", "amount", "estimate", "number")) {
                    val inner = node.path(innerKey)
                    if (inner.isNumber) return inner.asDouble()
                    if (inner.isTextual) {
                        val t = inner.asText().trim()
                        if (t.isNotBlank()) return t.replace(',', '.').toDoubleOrNull()
                    }
                }
                continue
            }
            val text = node.asText("").trim()
            if (text.isNotBlank()) return text.replace(',', '.').toDoubleOrNull()
        }
        return null
    }

    private fun extractNumericCustomFields(fields: com.fasterxml.jackson.databind.JsonNode): List<String> {
        val out = mutableListOf<Pair<String, String>>()
        for (entry in fields.properties()) {
            val key = entry.key
            if (!key.startsWith("customfield_")) continue
            val value = parseNumericField(entry.value) ?: continue
            out += key to value.toString()
        }
        return out.sortedBy { it.first }.map { "${it.first}: ${it.second}" }
    }

    private fun parseNumericField(node: com.fasterxml.jackson.databind.JsonNode): Double? {
        if (node.isMissingNode || node.isNull) return null
        if (node.isNumber) return node.asDouble()
        if (node.isObject) {
            for (innerKey in listOf("value", "amount", "estimate", "number")) {
                val inner = node.path(innerKey)
                if (inner.isNumber) return inner.asDouble()
                if (inner.isTextual) {
                    val t = inner.asText().trim()
                    if (t.isNotBlank()) return t.replace(',', '.').toDoubleOrNull()
                }
            }
            return null
        }
        val text = node.asText("").trim()
        return if (text.isBlank()) null else text.replace(',', '.').toDoubleOrNull()
    }
}
