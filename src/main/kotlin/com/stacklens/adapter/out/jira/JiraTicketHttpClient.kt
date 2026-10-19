package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.application.port.out.JiraProblemAnalysis
import com.stacklens.application.port.out.JiraSimilarTicket
import com.stacklens.application.port.out.JiraTicketConstants.MAX_COMMENTS
import com.stacklens.application.port.out.JiraTicketConstants.MAX_SEARCH_KEYWORDS
import com.stacklens.application.port.out.JiraTicketConstants.MAX_SIMILAR_TICKETS
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.application.port.out.jira.JiraTicketFetchPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.search.KeywordContext
import com.stacklens.domain.text.AcceptanceCriteriaExtractor
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.shared.http.bearerOrBasicAuthHeader
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

class JiraTicketHttpClient(
    @Value("\${webclient.connect-timeout:30000}") private val connectTimeoutMs: Int = 30_000,
    @Value("\${webclient.read-timeout:180000}") private val readTimeoutMs: Int = 180_000,
    private val httpFactory: ReactiveHttpClientFactory = ReactiveHttpClientFactory(connectTimeoutMs, readTimeoutMs),
    private val apiVersionResolver: JiraApiVersionResolver = DefaultJiraApiVersionResolver(),
) : JiraTicketFetchPort {
    private val log = LoggerFactory.getLogger(JiraTicketHttpClient::class.java)

    override fun fetchTicketByKey(
        config: JiraConfig,
        key: String,
    ): JiraTicketData {
        val client = buildClient(config)
        val apiVersion = apiVersionResolver.apiVersion(config)
        return try {
            val response =
                client
                    .get()
                    .uri(
                        "/rest/api/$apiVersion/issue/${KeywordContext.encode(key)}" +
                            "?fields=summary,description,issuetype,status,priority,labels,components,comment,created,updated",
                    ).retrieve()
                    .bodyToMono<JiraIssueResponse>()
                    .block() ?: throw IllegalStateException("Jira returned no response for ticket '$key'.")

            val summary =
                response.fields.summary
                    ?: throw IllegalStateException("Ticket '$key' has no summary (summary field missing).")
            val description = flattenDescriptionNode(response.fields.description)
            val comments =
                response.fields.comment
                    ?.comments
                    ?.take(MAX_COMMENTS)
                    ?.joinToString("\n---\n") { c ->
                        val author = c.author?.displayName ?: "Unbekannt"
                        val body = flattenDescriptionNode(c.body).take(3000)
                        "$author: $body"
                    } ?: ""

            JiraTicketData(
                key = response.key,
                summary = summary,
                description = description.take(3000),
                comments = comments,
                issueType = nameOrDefault(response.fields.issuetype, "Issue"),
                status = nameOrDefault(response.fields.status, ""),
                priority = response.fields.priority?.name,
                labels = response.fields.labels ?: emptyList(),
                components = nonBlankNames(response.fields.components),
                url = "${config.baseUrl.trimEnd('/')}/browse/${response.key}",
                acceptanceCriteria = AcceptanceCriteriaExtractor.extract(description),
            )
        } catch (e: WebClientResponseException) {
            val hint =
                when (e.statusCode.value()) {
                    401, 403 -> "Authentication failed – please check the username and API token in the Jira configuration."
                    404 -> "Ticket '$key' not found – please check the ticket key and Jira project name."
                    else -> "Jira responded with HTTP ${e.statusCode.value()}: ${e.responseBodyAsString.take(200)}"
                }
            log.warn("[TicketImplementation] HTTP error loading ticket {} ({}): {}", key, e.statusCode, hint)
            throw IllegalStateException(hint, e)
        } catch (e: Exception) {
            log.warn("[TicketImplementation] Error loading ticket {}: {}", key, e.message)
            throw IllegalStateException("Ticket '$key' could not be loaded: ${e.message}", e)
        }
    }

    override fun searchSimilarTickets(
        config: JiraConfig,
        originalTicket: JiraTicketData,
        problem: JiraProblemAnalysis,
    ): List<JiraSimilarTicket> {
        if (problem.searchKeywords.isEmpty()) return emptyList()

        val client = buildClient(config)
        val projects =
            config.projectKey
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        val projectClause =
            when {
                projects.size == 1 -> "project = \"${projects.first()}\""
                projects.size > 1 -> "project IN (${projects.joinToString(",") { "\"$it\"" }})"
                else -> null
            }

        val seen = mutableSetOf(originalTicket.key)
        val results = mutableListOf<JiraSimilarTicket>()

        for (keyword in problem.searchKeywords.take(MAX_SEARCH_KEYWORDS)) {
            if (results.size >= MAX_SIMILAR_TICKETS) break
            try {
                val safeKeyword = keyword.replace("\"", "\\\"")
                val textClause =
                    "(summary ~ \"$safeKeyword\" OR description ~ \"$safeKeyword\" OR comment ~ \"$safeKeyword\")"
                val conditions = listOfNotNull(projectClause, textClause, "key != \"${originalTicket.key}\"")
                val jql =
                    "${conditions.joinToString(" AND ")} ORDER BY updated DESC"
                val searchPath = apiVersionResolver.searchPath(config)
                val response =
                    client
                        .get()
                        .uri { b ->
                            b
                                .path(searchPath)
                                .queryParam("jql", jql)
                                .queryParam("fields", "summary,description,issuetype,status")
                                .queryParam("maxResults", 10)
                                .build()
                        }.retrieve()
                        .bodyToMono<JiraSearchResponse>()
                        .onErrorResume { Mono.empty() }
                        .block() ?: continue

                for (issue in response.issues) {
                    if (seen.contains(issue.key) || results.size >= MAX_SIMILAR_TICKETS) continue
                    seen.add(issue.key)
                    val desc = flattenDescriptionNode(issue.fields.description)
                    results +=
                        JiraSimilarTicket(
                            key = issue.key,
                            summary = issue.fields.summary ?: "",
                            description = desc.take(500),
                            issueType = issue.fields.issuetype?.name ?: "Issue",
                            status = issue.fields.status?.name ?: "",
                            url = "${config.baseUrl.trimEnd('/')}/browse/${issue.key}",
                            foundViaKeyword = keyword,
                        )
                }
            } catch (e: Exception) {
                log.warn(
                    "[TicketImplementation] Error searching for similar tickets (keyword={}): {}",
                    keyword,
                    e.message,
                )
            }
        }
        return results
    }

    fun buildClient(config: JiraConfig): WebClient =
        httpFactory.buildAuthHeader(
            baseUrl = config.baseUrl,
            authHeaderValue = bearerOrBasicAuthHeader(config.username, config.apiToken),
        )

    private fun nameOrDefault(
        named: JiraNamedValue?,
        default: String,
    ): String {
        val n = named?.name
        return if (n.isNullOrBlank()) default else n
    }

    private fun nonBlankNames(components: List<JiraComponentDto>?): List<String> = components?.map { it.name }?.filter { it.isNotBlank() } ?: emptyList()

    fun flattenDescriptionNode(node: JsonNode?): String {
        if (node == null || node.isNull) return ""
        if (node.isTextual) return node.asText()
        return flattenAdfNode(node).replace(WHITESPACE_COLLAPSE, " ").trim()
    }

    private fun flattenAdf(nodes: List<JiraAdfNode>): String =
        nodes
            .joinToString(" ") { node ->
                when {
                    node.text != null -> node.text
                    node.content.isNotEmpty() -> flattenAdf(node.content)
                    else -> ""
                }
            }.replace(WHITESPACE_COLLAPSE, " ")
            .trim()

    private companion object {
        private val WHITESPACE_COLLAPSE = Regex("\\s+")
    }
}
