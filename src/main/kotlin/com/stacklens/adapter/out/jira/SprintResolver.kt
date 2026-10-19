package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.application.port.out.ResolvedSprint
import com.stacklens.domain.model.JiraConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class SprintResolver(
    private val jiraTicketHttp: JiraTicketHttpClient,
    private val apiVersionResolver: JiraApiVersionResolver = DefaultJiraApiVersionResolver(),
) {
    private val log = LoggerFactory.getLogger(SprintResolver::class.java)

    fun resolve(
        config: JiraConfig,
        sprintName: String,
    ): ResolvedSprint {
        val client = jiraTicketHttp.buildClient(config)
        val candidates = resolveByJql(client, config, sprintName)
        if (candidates.isEmpty()) {
            throw IllegalArgumentException(
                "Sprint '$sprintName' wurde nicht gefunden. " +
                    "Bitte den exakten Sprint-Namen eingeben (Groß-/Kleinschreibung wird ignoriert).",
            )
        }
        val distinct = candidates.distinctBy { it.id }
        if (distinct.size == 1) {
            return distinct.single()
        }
        return distinct.first()
    }

    private fun resolveByJql(
        client: WebClient,
        config: JiraConfig,
        sprintName: String,
    ): List<ResolvedSprint> {
        val needle = sprintName.trim().lowercase()
        val safeName = sprintName.trim().replace("\"", "\\\"")
        val jql = "sprint = \"$safeName\""
        val searchPath = searchPath(config)
        val node =
            runCatching {
                client
                    .get()
                    .uri { b ->
                        b
                            .path(searchPath)
                            .queryParam("jql", jql)
                            .queryParam("fields", "*all")
                            .queryParam("maxResults", 100)
                            .build()
                    }.retrieve()
                    .bodyToMono<JsonNode>()
                    .block()
            }.getOrElse {
                log.warn("[SprintReview] JQL sprint lookup '{}' failed: {}", jql, it.message)
                return emptyList()
            } ?: return emptyList()

        val issues = node.path("issues")
        if (!issues.isArray || issues.isEmpty()) {
            log.info("[SprintReview] JQL '{}' returned no issues for sprint lookup", jql)
            return emptyList()
        }

        val out = mutableListOf<ResolvedSprint>()
        val seenIds = mutableSetOf<Long>()
        for (issue in issues) {
            val projectKey = entityProjectKey(issue)
            for (sprint in extractSprintsFromIssue(issue)) {
                if (sprint.id in seenIds) continue
                if (sprint.name.trim().lowercase() != needle) continue
                seenIds += sprint.id
                out +=
                    ResolvedSprint(
                        id = sprint.id,
                        name = sprint.name,
                        state = sprint.state,
                        boardId = sprint.boardId,
                        boardName = null,
                        url =
                            if (config.cloudMode) {
                                "${config.baseUrl.trimEnd('/')}/jira/software/c/projects/$projectKey/backlog"
                            } else {
                                "${config.baseUrl.trimEnd('/')}/secure/RapidBoard.jspa"
                            },
                    )
            }
        }
        if (out.isEmpty()) {
            log.info(
                "[SprintReview] JQL '{}' matched {} issue(s) but none carried a sprint named '{}'",
                jql,
                issues.size(),
                sprintName,
            )
        }
        return out
    }

    private fun extractSprintsFromIssue(issue: JsonNode): List<ParsedSprint> {
        val fields = issue.path("fields")
        if (!fields.isObject) return emptyList()
        val out = mutableListOf<ParsedSprint>()
        for (entry in fields.properties()) {
            val value = entry.value
            if (!value.isArray || value.isEmpty) continue
            for (element in value) {
                when {
                    element.isObject && element.has("id") && element.has("name") -> {
                        val id = element.path("id").asLong(0L)
                        val name = element.path("name").asText("")
                        if (id > 0 && name.isNotBlank()) {
                            out +=
                                ParsedSprint(
                                    id = id,
                                    name = name,
                                    state = element.path("state").asText("unknown"),
                                    boardId = element.path("boardId").asLong(0L).takeIf { it > 0 },
                                )
                        }
                    }
                    element.isTextual -> {
                        parseLegacySprintBlob(element.asText(""))?.let { out += it }
                    }
                }
            }
        }
        return out
    }

    private fun parseLegacySprintBlob(blob: String): ParsedSprint? {
        if (!blob.contains("[") || !blob.contains("id=")) return null
        val inner = blob.substringAfter('[', "").substringBeforeLast(']')
        if (inner.isBlank()) return null
        val attrs =
            inner
                .split(',')
                .mapNotNull {
                    val idx = it.indexOf('=')
                    if (idx <= 0) null else it.substring(0, idx).trim() to it.substring(idx + 1).trim()
                }.toMap()
        val id = attrs["id"]?.toLongOrNull() ?: return null
        val name = attrs["name"] ?: return null
        if (id <= 0 || name.isBlank()) return null
        return ParsedSprint(
            id = id,
            name = name,
            state = attrs["state"]?.lowercase() ?: "unknown",
            boardId = attrs["rapidViewId"]?.toLongOrNull()?.takeIf { it > 0 },
        )
    }

    private fun entityProjectKey(issue: JsonNode): String {
        val project = issue.path("fields").path("project")
        return project.path("key").asText("")
    }

    private fun searchPath(config: JiraConfig): String = apiVersionResolver.searchPath(config)

    private data class ParsedSprint(
        val id: Long,
        val name: String,
        val state: String,
        val boardId: Long?,
    )
}
