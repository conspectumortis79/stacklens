
package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.adapter.out.shared.connectionFailureKind
import com.stacklens.application.port.out.JiraConnectionTestPort
import com.stacklens.application.port.out.JiraConnectionTestPort.TestResult
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.shared.http.bearerOrBasicAuthHeader
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

class JiraConnectionTestAdapter(
    private val objectMapper: ObjectMapper,
    private val i18n: I18nPort,
    private val webClientBuilder: WebClient.Builder,
    private val apiVersionResolver: JiraApiVersionResolver = DefaultJiraApiVersionResolver(),
) : JiraConnectionTestPort {
    private val log = LoggerFactory.getLogger(JiraConnectionTestAdapter::class.java)

    override fun testConnection(
        config: JiraConfig,
        keyword: String?,
    ): TestResult {
        val apiVersion = apiVersionResolver.apiVersion(config)
        val searchEndpoints =
            if (config.cloudMode) {
                listOf(apiVersionResolver.searchPath(config), JiraEndpoints.SEARCH_PATH_V2)
            } else {
                listOf(apiVersionResolver.searchPath(config))
            }

        return try {
            val client =
                webClientBuilder
                    .clone()
                    .baseUrl(config.baseUrl.trimEnd('/'))
                    .defaultHeader("Authorization", bearerOrBasicAuthHeader(config.username, config.apiToken))
                    .defaultHeader("Accept", "application/json")
                    .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
                    .build()

            fun jiraSearchGet(
                jql: String,
                maxResults: Int,
                fields: String,
            ): String? {
                for (ep in searchEndpoints) {
                    val result =
                        client
                            .get()
                            .uri { b ->
                                b
                                    .path(ep)
                                    .queryParam("jql", jql)
                                    .queryParam("maxResults", maxResults.toString())
                                    .queryParam("fields", fields)
                                    .build()
                            }.exchangeToMono { response ->
                                val status = response.statusCode()
                                response.bodyToMono(String::class.java).map { body ->
                                    Pair(status, body)
                                }
                            }.block()
                    val (status, body) = result ?: continue
                    if (!status.isError && !status.is3xxRedirection && !body.trimStart().startsWith("<") && body.contains("\"issues\"")) {
                        return body
                    }
                    log.debug("[Jira-Test] GET {} → HTTP {} (trying next endpoint)", ep, status.value())
                }
                return null
            }

            val projectKeys =
                config.projectKey
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            val totalIssues: Int
            val ticketMsg: String

            if (projectKeys.isNotEmpty()) {
                val projectClause =
                    if (projectKeys.size == 1) {
                        "project = \"${projectKeys[0]}\""
                    } else {
                        "project IN (${projectKeys.joinToString(",") { "\"$it\"" }})"
                    }
                val testJql = "$projectClause ORDER BY updated DESC"
                val raw = jiraSearchGet(testJql, 50, "summary")
                val searchNode: JsonNode? = decodeNode(raw)
                val totalNode = searchNode?.path("total")
                val hasTotalField = totalNode != null && !totalNode.isMissingNode && !totalNode.isNull
                totalIssues =
                    when {
                        hasTotalField -> totalNode.asInt()
                        searchNode == null -> 0
                        else -> searchNode.path("issues").size()
                    }
                val projectLabel = projectKeys.joinToString(", ")
                val isLast =
                    if (searchNode == null) true else searchNode.path("isLast").asBoolean(true)
                val noIssuesMsg = i18n.t("config.be_jira_projects_no_issues", projectLabel)
                ticketMsg =
                    when {
                        hasTotalField && totalIssues > 0 -> i18n.t("config.be_jira_tickets_count", totalIssues, projectLabel)
                        hasTotalField -> noIssuesMsg
                        totalIssues > 0 && !isLast -> i18n.t("config.be_jira_projects_50plus", projectLabel)
                        totalIssues > 0 -> i18n.t("config.be_jira_projects_count", projectLabel, totalIssues)
                        else -> noIssuesMsg
                    }
            } else if (config.cloudMode) {
                val raw =
                    client
                        .get()
                        .uri(JiraEndpoints.PROJECT_PATH_V3 + "?maxResults=1")
                        .retrieve()
                        .onStatus({ it.isError }) { resp ->
                            resp.bodyToMono(String::class.java).map { body ->
                                WebClientResponseException.create(
                                    resp.statusCode().value(),
                                    resp.statusCode().toString(),
                                    resp.headers().asHttpHeaders(),
                                    body.toByteArray(),
                                    Charsets.UTF_8,
                                )
                            }
                        }.bodyToMono(String::class.java)
                        .block()
                totalIssues = 0
                val reachable = !raw.isNullOrBlank() && (raw.startsWith("[") || raw.contains("\"key\""))
                ticketMsg =
                    if (reachable) {
                        i18n.t("config.be_jira_api_reachable_no_key")
                    } else {
                        i18n.t("config.be_jira_no_projects")
                    }
            } else {
                val raw = jiraSearchGet("ORDER BY updated DESC", 1, "summary")
                totalIssues = 0
                val reachable = raw != null
                ticketMsg = if (reachable) i18n.t("config.be_jira_api_reachable_short") else i18n.t("config.be_jira_no_projects")
            }

            val keywordResult: Map<String, Any> =
                if (!keyword.isNullOrBlank()) {
                    val safe = keyword.replace("\"", "\\\"")
                    val projectPrefix =
                        if (projectKeys.isNotEmpty()) {
                            val clause =
                                if (projectKeys.size ==
                                    1
                                ) {
                                    "project = \"${projectKeys[0]}\""
                                } else {
                                    "project IN (${projectKeys.joinToString(",") { "\"$it\"" }})"
                                }
                            "$clause AND "
                        } else {
                            ""
                        }
                    val jql =
                        if (ISSUE_KEY.matches(keyword.trim())) {
                            "key = \"$safe\" ORDER BY updated DESC"
                        } else {
                            "$projectPrefix(summary ~ \"$safe\" OR description ~ \"$safe\" OR comment ~ \"$safe\") ORDER BY updated DESC"
                        }
                    val kwRaw = jiraSearchGet(jql, 5, "summary,issuetype,status")
                    val kwBody: JsonNode? = decodeNode(kwRaw)
                    val kwTotalNode = kwBody?.path("total")
                    val kwHasTotal = kwTotalNode != null && !kwTotalNode.isMissingNode && !kwTotalNode.isNull
                    val kwIssues = if (kwBody == null) null else kwBody.path("issues")
                    val kwTotal =
                        when {
                            kwHasTotal -> kwTotalNode.asInt()
                            kwIssues == null -> 0
                            else -> kwIssues.size()
                        }
                    val kwExamples =
                        if (kwIssues == null) {
                            emptyList()
                        } else {
                            kwIssues.map { issue ->
                                "${issue.path("key").asText()}: ${issue.path("fields").path("summary").asText()}"
                            }
                        }
                    mapOf("count" to kwTotal, "jql" to jql, "examples" to kwExamples)
                } else {
                    emptyMap()
                }

            val resultMap =
                mutableMapOf<String, Any>(
                    "success" to true,
                    "apiVersion" to apiVersion,
                    "totalAccessibleIssues" to totalIssues,
                    "message" to i18n.t("config.be_jira_conn_success", ticketMsg),
                )
            if (keywordResult.isNotEmpty()) resultMap["keywordSearch"] = keywordResult
            TestResult.Success(resultMap.toMap())
        } catch (e: WebClientResponseException) {
            val status = e.statusCode.value()
            val body = e.responseBodyAsString.take(400)
            val hint =
                when (status) {
                    401 ->
                        i18n.t("config.be_jira_auth_failed", body)
                    403 -> i18n.t("config.be_jira_access_denied", body)
                    404 -> {
                        val base = config.baseUrl.trimEnd('/')
                        i18n.t("config.be_jira_endpoint_not_found", "$base/rest/api/...")
                    }
                    410 -> i18n.t("config.be_jira_api_removed", "/rest/api/...", body)
                    else -> "HTTP $status: $body"
                }
            TestResult.Failure(
                connectionFailureKind(status),
                mapOf("success" to false, "message" to hint, "status" to status),
            )
        } catch (e: Exception) {
            TestResult.Failure(connectionFailureKind(null), mapOf("success" to false, "message" to jiraConnExceptionHint(e, i18n)))
        }
    }

    private fun decodeNode(json: String?): JsonNode? {
        if (json == null) return null
        return runCatching { objectMapper.readTree(json) }.getOrNull()
    }

    private companion object {
        private val ISSUE_KEY = Regex("^[A-Z][A-Z0-9]*-[0-9]+$")
    }
}

internal fun jiraConnExceptionHint(
    e: Exception,
    i18n: I18nPort,
): String =
    when {
        e.message?.contains("Connection refused") == true -> i18n.t("config.be_jira_conn_refused")
        e.message?.contains("SSL") == true || e.message?.contains("PKIX") == true ->
            i18n.t("config.be_jira_ssl_error")
        e.message?.contains("timeout") == true || e.message?.contains("Timeout") == true ->
            i18n.t("config.be_jira_timeout")
        else -> e.message ?: i18n.t("config.be_unknown_error")
    }
