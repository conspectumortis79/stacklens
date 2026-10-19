package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.search.JiraSearchPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.shared.http.WebClientBuilderDsl.acceptJson
import com.stacklens.shared.http.WebClientBuilderDsl.authorizationHeader
import com.stacklens.shared.http.bearerOrBasicAuthHeader
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient

open class JiraClient(
    private val objectMapper: ObjectMapper,
    private val jqlBuilder: JiraJqlBuilder,
    private val issueMapper: JiraIssueMapper,
    private val endpoints: JiraEndpoints = JiraEndpoints(),
    private val httpFactory: ReactiveHttpClientFactory = ReactiveHttpClientFactory(),
) : JiraSearchPort {
    private val log = LoggerFactory.getLogger(JiraClient::class.java)

    override fun searchKeyword(
        config: JiraConfig,
        keyword: String,
        projectFilters: List<String>,
        sessionId: Long,
        componentFilters: List<String>,
        labelFilters: List<String>,
    ): List<SearchResult> {
        if (!config.active) return emptyList()
        return try {
            val client = buildClient(config)
            val projects = projectFilters.map { it.trim().uppercase() }.filter { it.isNotBlank() }
            val effectiveKeys = if (projects.isNotEmpty()) projects else jqlBuilder.projectKeyList(config.projectKey)
            if (effectiveKeys.isEmpty() && config.cloudMode) {
                log.info("[Jira:{}] Cloud search without project filter – searching across all accessible projects.", config.name)
            }
            val jql = jqlBuilder.buildJql(effectiveKeys, keyword, config.cloudMode, componentFilters, labelFilters)
            log.info("[Jira:{}] JQL: {}", config.name, jql)
            searchIssues(client, config, jql, keyword, sessionId)
        } catch (e: Exception) {
            log.error("[Jira:{}] Error searching for '{}': {}", config.name, keyword, e.message, e)
            emptyList()
        }
    }

    internal fun buildClient(config: JiraConfig): WebClient =
        httpFactory
            .builder()
            .baseUrl(config.baseUrl.trimEnd('/'))
            .authorizationHeader(bearerOrBasicAuthHeader(config.username, config.apiToken))
            .acceptJson()
            .build()

    internal fun searchIssues(
        client: WebClient,
        config: JiraConfig,
        jql: String,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult> {
        val fieldList = listOf("summary", "description", "issuetype", "status", "assignee", "priority", "created", "updated", "attachment")
        val fieldsCsv = fieldList.joinToString(",")

        val endpointList: List<JiraEndpointSpec> =
            if (config.cloudMode) endpoints.cloud else endpoints.server

        val pageSize = 100

        fun fetchPage(
            endpoint: JiraEndpointSpec,
            startAt: Int,
        ): String? {
            val (method, path) = endpoint
            val result =
                if (method == "POST") {
                    client
                        .post()
                        .uri(path)
                        .header("X-Atlassian-Token", "no-check")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .bodyValue(mapOf("jql" to jql, "maxResults" to pageSize, "startAt" to startAt, "fields" to fieldList))
                        .exchangeToMono { response ->
                            val status = response.statusCode()
                            response.bodyToMono(String::class.java).map { body ->
                                Pair(status, body)
                            }
                        }.block()
                } else {
                    client
                        .get()
                        .uri { ub ->
                            ub
                                .path(path)
                                .queryParam("jql", jql)
                                .queryParam("maxResults", pageSize.toString())
                                .queryParam("startAt", startAt.toString())
                                .queryParam("fields", fieldsCsv)
                                .build()
                        }.exchangeToMono { response ->
                            val status = response.statusCode()
                            response.bodyToMono(String::class.java).map { body -> Pair(status, body) }
                        }.block()
                }
            val (status, body) = result ?: return null
            return when {
                !status.isError && !status.is3xxRedirection && body.contains("\"issues\"") -> body
                else -> {
                    log.debug("[Jira:{}] {} {} → HTTP {} – trying next", config.name, method, path, status.value())
                    null
                }
            }
        }

        val firstPage =
            endpointList.firstNotNullOfOrNull { ep ->
                fetchPage(ep, 0)?.let { body -> ep to body }
            }
        if (firstPage == null) {
            log.warn("[Jira:{}] No Jira search endpoint returned usable results.", config.name)
            return emptyList()
        }
        val (workingEndpoint, firstBody) = firstPage

        val firstResponse =
            try {
                objectMapper.readValue(firstBody, JiraSearchResponse::class.java)
            } catch (e: Exception) {
                log.warn("[Jira:{}] JSON error: {}", config.name, e.message)
                return emptyList()
            }

        val allIssues = mutableListOf<JiraIssue>()
        allIssues += firstResponse.issues
        val total = firstResponse.total
        if (total != null && allIssues.isNotEmpty() && total > allIssues.size) {
            var startAt = allIssues.size
            while (startAt < total) {
                val pageBody = fetchPage(workingEndpoint, startAt) ?: break
                try {
                    val pageResp = objectMapper.readValue(pageBody, JiraSearchResponse::class.java)
                    if (pageResp.issues.isEmpty()) break
                    allIssues += pageResp.issues
                    startAt += pageResp.issues.size
                } catch (_: Exception) {
                    break
                }
            }
        }
        log.info("[Jira:{}] {} issue(s) found for '{}'", config.name, allIssues.size, keyword)

        return allIssues.flatMap { issue ->
            try {
                listOfNotNull(issueMapper.mapIssueToResult(issue, config, keyword, sessionId)) +
                    issueMapper.findDrawIoAttachmentResults(client, issue, config, keyword, sessionId)
            } catch (e: Exception) {
                log.warn("[Jira] Could not map issue {}: {}", issue.key, e.message)
                emptyList()
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class JiraSearchResponse(
        val issues: List<JiraIssue> = emptyList(),
        val total: Int? = null,
        val startAt: Int? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class JiraIssue(
        val key: String = "",
        val fields: JiraFields = JiraFields(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class JiraFields(
        val summary: String? = null,
        val description: JsonNode? = null,
        val issuetype: JiraNamedValue? = null,
        val status: JiraNamedValue? = null,
        val assignee: JiraAssignee? = null,
        val priority: JiraNamedValue? = null,
        val updated: String? = null,
        val attachment: List<JiraAttachment>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class JiraAttachment(
        val id: String = "",
        val filename: String = "",
        val content: String = "",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class JiraNamedValue(
        val name: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class JiraAssignee(
        val displayName: String? = null,
        val name: String? = null,
    )
}
