package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.domain.model.JiraConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.concurrent.ConcurrentHashMap

@Component
class SprintIssueLoader(
    private val jiraTicketHttp: JiraTicketHttpClient,
    private val apiVersionResolver: JiraApiVersionResolver = DefaultJiraApiVersionResolver(),
) {
    private val log = LoggerFactory.getLogger(SprintIssueLoader::class.java)

    private val storyPointsFieldCache = ConcurrentHashMap<Long, String>()

    fun fetch(
        config: JiraConfig,
        sprintId: Long,
    ): List<SprintReviewTicket> {
        val client = jiraTicketHttp.buildClient(config)
        val fields = "*all"
        val collected = mutableListOf<SprintReviewTicket>()
        var startAt = 0
        val pageSize = 50
        val jql = "sprint = $sprintId AND issuetype not in subTaskIssueTypes()"
        while (true) {
            val page =
                runCatching {
                    client
                        .get()
                        .uri { b ->
                            b
                                .path(searchPath(config))
                                .queryParam("jql", jql)
                                .queryParam("fields", fields)
                                .queryParam("startAt", startAt)
                                .queryParam("maxResults", pageSize)
                                .build()
                        }.retrieve()
                        .bodyToMono<JsonNode>()
                        .block()
                }.getOrElse { e ->
                    val status = (e as? WebClientResponseException)?.statusCode?.value() ?: 0
                    log.warn(
                        "[SprintReview] listing issues for sprint {} failed (HTTP {}): {}",
                        sprintId,
                        status,
                        e.message,
                    )
                    throw IllegalStateException(
                        "Jira antwortete mit HTTP $status beim Laden der Sprint-Tickets.",
                        e,
                    )
                } ?: break
            val issues = page.path("issues")
            if (!issues.isArray || issues.isEmpty) break
            for (issue in issues) {
                if (isSubtaskIssue(issue)) continue
                collected += jsonToSprintTicket(issue, config)
            }
            val total = page.path("total").asInt(collected.size)
            startAt += issues.size()
            if (startAt >= total || issues.size() < pageSize) break
        }
        return collected
    }

    private fun jsonToSprintTicket(
        issueNode: JsonNode,
        config: JiraConfig,
    ): SprintReviewTicket {
        val key = issueNode.path("key").asText("")
        val fields = issueNode.path("fields")
        val summary = fields.path("summary").asText("")
        val descNode = fields.path("description")
        val description = jiraTicketHttp.flattenDescriptionNode(descNode)
        val labels =
            fields
                .path("labels")
                .mapNotNull { it.asText("").takeIf { v -> v.isNotBlank() } }
        val numericCustomFields = extractNumericCustomFields(fields)
        return SprintReviewTicket(
            key = key,
            summary = summary,
            description = description,
            url = "${config.baseUrl.trimEnd('/')}/browse/$key",
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
            storyPoints =
                parseStoryPointsValue(
                    fields,
                    resolveStoryPointsFieldId(config, key),
                ),
            subtasks = emptyList(),
            referencedTickets = emptyList(),
            numericCustomFields = numericCustomFields,
        )
    }

    private fun isSubtaskIssue(issueNode: JsonNode): Boolean =
        issueNode
            .path("fields")
            .path("issuetype")
            .path("subtask")
            .asBoolean(false)

    private fun resolveStoryPointsFieldId(
        config: JiraConfig,
        sampleIssueKey: String,
    ): String {
        val explicitFieldId = config.storyPointsFieldId
        if (explicitFieldId.isNotBlank()) return explicitFieldId
        return storyPointsFieldCache.getOrPut(config.id) { detectStoryPointsFieldId(config, sampleIssueKey) }
    }

    private fun detectStoryPointsFieldId(
        config: JiraConfig,
        sampleIssueKey: String,
    ): String {
        val v = apiVersionResolver.apiVersion(config)
        val client = jiraTicketHttp.buildClient(config)
        val response =
            runCatching {
                client
                    .get()
                    .uri("/rest/api/$v/field")
                    .retrieve()
                    .bodyToMono<JsonNode>()
                    .block()
            }.getOrElse {
                log.warn(
                    "[SprintReview] field-metadata lookup for story-points failed on " +
                        "{}: {} — falling back to common IDs",
                    config.baseUrl,
                    it.message,
                )
                return DEFAULT_STORY_POINTS_FIELD_ID
            } ?: return DEFAULT_STORY_POINTS_FIELD_ID

        val patterns = STORY_POINTS_NAME_PATTERNS
        val numericFields =
            response.filter { f ->
                val type = f.path("schema").path("type").asText("")
                type == "number" || type == "integer" || type == "double"
            }
        for (pattern in patterns) {
            val match = numericFields.firstOrNull { pattern.matches(it.path("name").asText("").lowercase()) }
            if (match != null) {
                val id = match.path("id").asText("")
                log.info(
                    "[SprintReview] auto-detected story-points field for {}: {} ({})",
                    config.baseUrl,
                    id,
                    match.path("name").asText(""),
                )
                return id
            }
        }
        log.warn(
            "[SprintReview] no story-points field name match for {} — falling back to {}",
            config.baseUrl,
            DEFAULT_STORY_POINTS_FIELD_ID,
        )
        return DEFAULT_STORY_POINTS_FIELD_ID
    }

    private fun parseStoryPointsValue(
        fields: JsonNode,
        primaryFieldId: String,
    ): Double? {
        val primary = parseSingleNode(fields.path(primaryFieldId))
        if (primary != null) return primary
        for (fallback in DEFAULT_STORY_POINTS_FALLBACK_IDS) {
            if (fallback == primaryFieldId) continue
            val value = parseSingleNode(fields.path(fallback))
            if (value != null) return value
        }
        return null
    }

    private fun parseSingleNode(node: JsonNode): Double? {
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
        if (text.isBlank()) return null
        return text.replace(',', '.').toDoubleOrNull()
    }

    private fun extractNumericCustomFields(fields: JsonNode): List<String> {
        val out = mutableListOf<Pair<String, String>>()
        for (entry in fields.properties()) {
            val key = entry.key
            if (!key.startsWith("customfield_")) continue
            val value = parseSingleNode(entry.value) ?: continue
            out += key to value.toString()
        }
        return out.sortedBy { it.first }.map { "${it.first}: ${it.second}" }
    }

    private fun searchPath(config: JiraConfig): String = apiVersionResolver.searchPath(config)

    private companion object {
        const val DEFAULT_STORY_POINTS_FIELD_ID = "customfield_10016"

        val DEFAULT_STORY_POINTS_FALLBACK_IDS =
            listOf(
                "customfield_10016",
                "customfield_10026",
                "customfield_10004",
                "customfield_10002",
                "customfield_10028",
            )

        val STORY_POINTS_NAME_PATTERNS =
            listOf(
                Regex("^story[_ ]?points?$"),
                Regex("^story[_ ]?point[_ ]?estimate$"),
                Regex("^sp[_ ]?estimate$"),
                Regex("^story[_ ]?point[_ ]?size$"),
                Regex("^effort[_ ]?points?$"),
                Regex("^points?$"),
            )
    }
}
