package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.application.port.out.jira.JiraSprintIssueKeyListerPort
import com.stacklens.domain.model.JiraConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class JiraSprintIssueKeyLister(
    private val jiraTicketHttp: JiraTicketHttpClient,
    private val apiVersionResolver: JiraApiVersionResolver = DefaultJiraApiVersionResolver(),
) : JiraSprintIssueKeyListerPort {
    private val log = LoggerFactory.getLogger(JiraSprintIssueKeyLister::class.java)

    override fun listTopLevelIssueKeys(
        config: JiraConfig,
        sprintId: Long,
        pageSize: Int,
    ): List<String> {
        val client = jiraTicketHttp.buildClient(config)
        val collected = mutableListOf<String>()
        val seen = HashSet<String>()
        var startAt = 0
        val jql = "sprint = $sprintId AND issuetype not in subTaskIssueTypes()"
        while (true) {
            val page =
                runCatching {
                    client
                        .get()
                        .uri { b ->
                            b
                                .path(apiVersionResolver.searchPath(config))
                                .queryParam("jql", jql)
                                .queryParam("fields", "key")
                                .queryParam("startAt", startAt)
                                .queryParam("maxResults", pageSize)
                                .build()
                        }.retrieve()
                        .bodyToMono<JsonNode>()
                        .block()
                }.getOrElse { e ->
                    val status = (e as? WebClientResponseException)?.statusCode?.value() ?: 0
                    log.warn(
                        "Sprint key listing failed for sprint {} (HTTP {}): {}",
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
                val key = issue.path("key").asText("")
                if (key.isBlank()) continue
                if (seen.add(key)) collected += key
            }
            val total = page.path("total").asInt(collected.size)
            startAt += issues.size()
            if (startAt >= total || issues.size() < pageSize) break
        }
        return collected
    }
}
