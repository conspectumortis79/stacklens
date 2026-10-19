package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.JiraSprintIssueFieldResolver
import com.stacklens.adapter.out.shared.JiraSprintIssueItem
import com.stacklens.application.port.out.LinkedIssue
import com.stacklens.application.port.out.SprintInfo
import com.stacklens.application.port.out.SprintIssue
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import com.stacklens.domain.model.JiraConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.shared.http.bearerOrBasicAuthHeader
import com.stacklens.shared.sprintIdFromRegex
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.net.ConnectException
import java.net.URI

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraSprintResponse(
    val id: Long = 0,
    val name: String = "",
    val state: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val goal: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraSprintListResponse(
    val values: List<JiraSprintResponse> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JiraSprintIssuesResponse(
    val total: Int = 0,
    val issues: List<JiraSprintIssueItem> = emptyList(),
)

internal class JiraSprintHttpClient(
    private val fieldResolver: JiraSprintIssueFieldResolver,
    @Value("\${webclient.connect-timeout:30000}") private val connectTimeoutMs: Int = 30_000,
    @Value("\${webclient.read-timeout:180000}") private val readTimeoutMs: Int = 180_000,
    private val httpFactory: ReactiveHttpClientFactory = ReactiveHttpClientFactory(connectTimeoutMs, readTimeoutMs),
) : JiraSprintFetchPort {
    private val log = LoggerFactory.getLogger(JiraSprintHttpClient::class.java)

    companion object {
        private val SPRINT_ID_REGEX = Regex("""/sprints?/(\d+)""", RegexOption.IGNORE_CASE)
        private val SPRINT_PARAM_REGEX = Regex("""[?&](?:sprint|selectedSprint|sprints?)=(\d+)""", RegexOption.IGNORE_CASE)
        private val BOARD_ID_REGEX = Regex("""/boards?/(\d+)""", RegexOption.IGNORE_CASE)
        private const val MAX_COMMENTS_PER_ISSUE = 5
        private const val PAGE_SIZE = 100
    }

    override fun fetchSprintInfo(
        config: JiraConfig,
        sprintUrl: String,
    ): SprintInfo {
        val client = buildClient(config)
        val sprintId = resolveSprintId(config, client, sprintUrl)
        return fetchSprintById(client, sprintId)
    }

    override fun fetchSprintIssues(
        config: JiraConfig,
        sprintUrl: String,
    ): List<SprintIssue> {
        val client = buildClient(config)
        val sprintId = resolveSprintId(config, client, sprintUrl)
        return fetchAllSprintIssues(config, client, sprintId)
    }

    override fun fetchSprintData(
        config: JiraConfig,
        sprintUrl: String,
    ): Pair<SprintInfo, List<SprintIssue>> {
        val client = buildClient(config)
        val sprintId = resolveSprintId(config, client, sprintUrl)
        val sprintInfo = fetchSprintById(client, sprintId)
        val issues = fetchAllSprintIssues(config, client, sprintId)
        return sprintInfo to issues
    }

    private fun resolveSprintId(
        config: JiraConfig,
        client: WebClient,
        sprintUrl: String,
    ): Long {
        sprintIdFromRegex(SPRINT_ID_REGEX, sprintUrl)?.let { return it }
        sprintIdFromRegex(SPRINT_PARAM_REGEX, sprintUrl)?.let { return it }

        val boardId =
            sprintIdFromRegex(BOARD_ID_REGEX, sprintUrl)
                ?: throw IllegalArgumentException(
                    "Could not determine board ID or sprint ID from URL: $sprintUrl. " +
                        "Bitte eine URL mit /boards/{id} oder /sprints/{id} angeben.",
                )

        log.debug("[ReviewAnalyse] No sprint found in URL – loading active sprint for board {}", boardId)
        val sprintList =
            try {
                client
                    .get()
                    .uri("/rest/agile/1.0/board/$boardId/sprint?state=active&maxResults=1")
                    .retrieve()
                    .bodyToMono<JiraSprintListResponse>()
                    .block()
            } catch (e: WebClientResponseException) {
                throw IllegalStateException(
                    "Jira Agile API responded with HTTP ${e.statusCode.value()} for board $boardId. " +
                        "Please verify that the Jira configuration is correct and the user has access to the board.",
                    e,
                )
            }

        val activeSprint = sprintList?.values?.firstOrNull()
        return activeSprint?.id
            ?: throw IllegalStateException(
                "No active sprint found for board $boardId. " +
                    "Bitte die URL eines Sprints direkt angeben.",
            )
    }

    internal fun refusedTargetHost(config: JiraConfig): String =
        runCatching {
            val uri = URI.create(config.baseUrl.trim())
            "${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
        }.getOrDefault(config.baseUrl)

    private fun fetchSprintById(
        client: WebClient,
        sprintId: Long,
    ): SprintInfo {
        val resp =
            client
                .get()
                .uri("/rest/agile/1.0/sprint/$sprintId")
                .retrieve()
                .bodyToMono<JiraSprintResponse>()
                .onErrorResume { Mono.empty() }
                .block()
                ?: return SprintInfo(sprintId, "Sprint $sprintId", "unknown", null, null, null)

        return SprintInfo(
            id = resp.id,
            name = resp.name,
            state = resp.state,
            startDate = resp.startDate,
            endDate = resp.endDate,
            goal = resp.goal?.takeIf { it.isNotBlank() },
        )
    }

    private fun fetchAllSprintIssues(
        config: JiraConfig,
        client: WebClient,
        sprintId: Long,
    ): List<SprintIssue> {
        val fields =
            "summary,description,issuetype,status,priority,assignee," +
                "labels,comment,created,resolutiondate,statuscategorychangedate,issuelinks"
        val issues = mutableListOf<SprintIssue>()
        var startAt = 0

        while (issues.size < 500) {
            val response =
                try {
                    client
                        .get()
                        .uri { b ->
                            b
                                .path("/rest/agile/1.0/sprint/$sprintId/issue")
                                .queryParam("fields", fields)
                                .queryParam("expand", "changelog")
                                .queryParam("maxResults", PAGE_SIZE)
                                .queryParam("startAt", startAt)
                                .build()
                        }.retrieve()
                        .bodyToMono<JiraSprintIssuesResponse>()
                        .block()
                } catch (e: WebClientResponseException) {
                    log.warn("[ReviewAnalyse] HTTP {} while loading sprint issues for sprint {}", e.statusCode, sprintId)
                    throw IllegalStateException(
                        "Jira error loading sprint issues (HTTP ${e.statusCode.value()}): ${e.responseBodyAsString.take(200)}",
                        e,
                    )
                } catch (e: Exception) {
                    val isConnRefused = generateSequence(e as Throwable?) { it.cause }.any { it is ConnectException }
                    if (isConnRefused) {
                        throw StackLensFailureException(
                            StackLensFailure.JiraConnectionRefused(refusedTargetHost(config)),
                            e,
                        )
                    }
                    throw e
                } ?: break

            for (item in response.issues) {
                val description = flattenNode(item.fields.description).take(1500)
                val comments =
                    item.fields.comment
                        ?.comments
                        ?.take(MAX_COMMENTS_PER_ISSUE)
                        ?.joinToString("\n---\n") { c ->
                            val author = c.author?.displayName ?: "Unbekannt"
                            val body = flattenNode(c.body).take(400)
                            "$author: $body"
                        } ?: ""

                issues +=
                    SprintIssue(
                        key = item.key,
                        summary = item.fields.summary ?: "",
                        description = description,
                        issueType = item.fields.issuetype?.name ?: "Issue",
                        status = item.fields.status?.name ?: "",
                        priority = item.fields.priority?.name,
                        assignee = item.fields.assignee?.displayName,
                        labels = item.fields.labels ?: emptyList(),
                        comments = comments,
                        url = "${config.baseUrl.trimEnd('/')}/browse/${item.key}",
                        createdAt = item.fields.created,
                        startedAt = fieldResolver.resolveStartedTimestamp(item),
                        resolvedAt = fieldResolver.resolveDoneTimestamp(item),
                        qaLoopCount = fieldResolver.resolveQaLoopCount(item),
                        linkedIssues = mapLinkedIssues(item),
                    )
            }

            if (issues.size >= response.total || response.issues.size < PAGE_SIZE) break
            startAt += response.issues.size
        }

        log.debug("[ReviewAnalyse] {} issues loaded for sprint {}", issues.size, sprintId)
        return issues
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
            val key = ref.key ?: return@mapNotNull null
            LinkedIssue(
                key = key,
                summary = ref.fields?.summary ?: "",
                relationship = relationship,
                status = ref.fields?.status?.name,
            )
        } ?: emptyList()

    private fun buildClient(config: JiraConfig): WebClient =
        httpFactory.buildAuthHeader(
            baseUrl = config.baseUrl,
            authHeaderValue = bearerOrBasicAuthHeader(config.username, config.apiToken),
        )

    private fun flattenNode(node: JsonNode?): String {
        if (node == null) return ""
        if (node.isTextual) return node.asText()
        val sb = StringBuilder()

        fun walk(n: JsonNode) {
            when {
                n.isTextual -> sb.append(n.asText())
                n.has("text") -> sb.append(n.get("text").asText())
                n.isArray -> n.forEach { walk(it) }
                n.isObject -> {
                    val content = n.get("content")
                    if (content != null) walk(content) else n.properties().forEach { walk(it.value) }
                }
            }
        }
        walk(node)
        return sb.toString().trim()
    }
}
