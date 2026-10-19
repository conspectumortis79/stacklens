package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.JiraTicketSummaryComment
import com.stacklens.application.port.out.JiraTicketSummaryJiraPort
import com.stacklens.application.port.out.JiraTicketSummaryTicket
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.domain.model.JiraConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component("mcpAwareJiraTicketSummaryJiraPort")
open class McpAwareJiraTicketSummaryAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @Qualifier("jiraTicketSummaryNativeJiraAdapter")
    private val delegate: JiraTicketSummaryJiraPort,
) : JiraTicketSummaryJiraPort {
    private val log = LoggerFactory.getLogger(McpAwareJiraTicketSummaryAdapter::class.java)

    override fun fetchTicketWithComments(
        config: JiraConfig?,
        ticketKey: String,
    ): JiraTicketSummaryTicket? {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.jiraReady(it) }
                ?: return delegate.fetchTicketWithComments(config, ticketKey)

        return try {
            val result = gateway.callTool(mcp, "jira_get_issue", mapOf("key" to ticketKey))
            if (result.isError) {
                log.warn("[MCP] jira_get_issue returned error: {} — falling back to native", result.text)
                return delegate.fetchTicketWithComments(config, ticketKey)
            }
            val node = McpRouting.parseJsonOrNull(objectMapper, result.text) ?: return delegate.fetchTicketWithComments(config, ticketKey)
            JiraTicketSummaryTicket(
                key = node.path("key").asText(ticketKey),
                summary = node.path("summary").asText(""),
                description = node.path("description").asText(""),
                url = node.path("url").asText(""),
                status = node.path("status").asText(""),
                issueType = node.path("issueType").asText("Issue"),
                priority = node.path("priority").asText(""),
                assignee = node.path("assignee").asText(""),
                reporter = node.path("reporter").asText(""),
                labels =
                    node.path("labels").let { l ->
                        if (l.isArray && !l.isEmpty) l.map { it.asText("") } else emptyList()
                    },
                createdAt = node.path("createdAt").asText(""),
                updatedAt = node.path("updatedAt").asText(""),
                comments =
                    node.path("comments").let { c ->
                        if (c.isArray && !c.isEmpty) {
                            c.map { item ->
                                JiraTicketSummaryComment(
                                    author = item.path("author").asText(""),
                                    body = item.path("body").asText(""),
                                    createdAt = item.path("createdAt").asText(""),
                                )
                            }
                        } else {
                            emptyList()
                        }
                    },
            )
        } catch (e: Exception) {
            log.warn("[MCP] jira_get_issue exception: {} — falling back to native", e.message)
            delegate.fetchTicketWithComments(config, ticketKey)
        }
    }
}
