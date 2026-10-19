package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.adapter.out.shared.JiraApiVersionResolver
import com.stacklens.application.port.out.JiraTicketSummaryComment
import com.stacklens.application.port.out.JiraTicketSummaryJiraPort
import com.stacklens.application.port.out.JiraTicketSummaryTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.search.KeywordContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

@Component("jiraTicketSummaryNativeJiraAdapter")
class JiraTicketSummaryNativeJiraAdapter(
    private val jiraTicketHttp: JiraTicketHttpClient,
    private val apiVersionResolver: JiraApiVersionResolver = DefaultJiraApiVersionResolver(),
) : JiraTicketSummaryJiraPort {
    override fun fetchTicketWithComments(
        config: JiraConfig?,
        ticketKey: String,
    ): JiraTicketSummaryTicket? {
        if (config == null) {
            throw IllegalStateException("Jira-Konfiguration ist erforderlich.")
        }
        val client = jiraTicketHttp.buildClient(config)
        val v = apiVersionResolver.apiVersion(config)
        val fields =
            "summary,description,issuetype,status,priority,labels,comment," +
                "assignee,reporter,created,updated"
        val node: JsonNode =
            try {
                client
                    .get()
                    .uri(
                        "/rest/api/$v/issue/${KeywordContext.encode(ticketKey)}?fields=$fields",
                    ).retrieve()
                    .bodyToMono(JsonNode::class.java)
                    .block()
                    ?: throw IllegalStateException("Jira returned no response for ticket '$ticketKey'.")
            } catch (e: WebClientResponseException) {
                if (e.statusCode == HttpStatus.NOT_FOUND) {
                    return null
                }
                val hint =
                    when (e.statusCode.value()) {
                        401, 403 -> "Authentifizierung fehlgeschlagen – bitte Benutzername und API-Token der Jira-Konfiguration prüfen."
                        else -> "Jira antwortete mit HTTP ${e.statusCode.value()}: ${e.responseBodyAsString.take(200)}"
                    }
                throw IllegalStateException(hint, e)
            }

        val key = node.path("key").asText(ticketKey)
        val f = node.path("fields")
        val summary = f.path("summary").asText("")
        if (summary.isBlank()) {
            throw IllegalStateException("Ticket '$key' hat keinen Titel (summary-Feld fehlt).")
        }
        val description = jiraTicketHttp.flattenDescriptionNode(f.path("description"))
        val commentsRaw = f.path("comment").path("comments")
        val comments =
            if (commentsRaw.isArray) {
                commentsRaw.mapNotNull { c ->
                    val author = c.path("author").path("displayName").asText("Unbekannt")
                    val body = jiraTicketHttp.flattenDescriptionNode(c.path("body"))
                    if (body.isBlank()) {
                        null
                    } else {
                        JiraTicketSummaryComment(
                            author = author,
                            body = body,
                            createdAt = c.path("created").asText(""),
                        )
                    }
                }
            } else {
                emptyList()
            }
        return JiraTicketSummaryTicket(
            key = key,
            summary = summary,
            description = description,
            url = "${config.baseUrl.trimEnd('/')}/browse/$key",
            status = f.path("status").path("name").asText(""),
            issueType = f.path("issuetype").path("name").asText("Issue"),
            priority = f.path("priority").path("name").asText(""),
            assignee = f.path("assignee").path("displayName").asText(""),
            reporter = f.path("reporter").path("displayName").asText(""),
            labels =
                f.path("labels").let { l ->
                    if (l.isArray) l.mapNotNull { it.asText(null) } else emptyList()
                },
            createdAt = f.path("created").asText(""),
            updatedAt = f.path("updated").asText(""),
            comments = comments,
        )
    }
}
