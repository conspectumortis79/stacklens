package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.AdfCommentPreparer
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.TicketRefinementSubtask
import com.stacklens.application.port.out.TicketRefinementTicket
import com.stacklens.domain.model.JiraConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component("mcpAwareTicketRefinementJiraPort")
open class McpAwareTicketRefinementJiraAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @Qualifier("sprintPrepJiraPort")
    private val delegate: TicketRefinementJiraPort,
) : TicketRefinementJiraPort {
    private val log = LoggerFactory.getLogger(McpAwareTicketRefinementJiraAdapter::class.java)
    private val adfCommentPreparer = AdfCommentPreparer(objectMapper)

    override fun fetchParentWithLinkedTickets(
        config: JiraConfig?,
        parentKey: String,
    ): List<TicketRefinementTicket> {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.jiraReady(it) }
                ?: return delegate.fetchParentWithLinkedTickets(config, parentKey)
        if (config != null &&
            !config.baseUrl.trimEnd('/').equals(mcp.jiraBaseUrl.trimEnd('/'), ignoreCase = true)
        ) {
            log.debug(
                "[MCP→TicketRefinement] config '{}' targets '{}' but MCP is configured for '{}', using direct adapter",
                config.name,
                config.baseUrl,
                mcp.jiraBaseUrl,
            )
            return delegate.fetchParentWithLinkedTickets(config, parentKey)
        }
        return try {
            val parent = fetchOne(parentKey, config)
            val parentNode =
                runCatching {
                    val r = gateway.callTool(mcp, "jira_get_issue", mapOf("key" to parentKey))
                    if (r.isError) null else McpRouting.parseJsonOrNull(objectMapper, r.text)
                }.getOrNull()
            val keys = LinkedHashSet<String>()
            parentNode
                ?.path("fields")
                ?.path("issuelinks")
                ?.forEach { l ->
                    l
                        .path("inwardIssue")
                        .path("key")
                        .asText("")
                        .takeIf { it.isNotBlank() }
                        ?.let(keys::add)
                    l
                        .path("outwardIssue")
                        .path("key")
                        .asText("")
                        .takeIf { it.isNotBlank() }
                        ?.let(keys::add)
                }
            parentNode
                ?.path("fields")
                ?.path("subtasks")
                ?.forEach {
                    it
                        .path("key")
                        .asText("")
                        .takeIf { k -> k.isNotBlank() }
                        ?.let(keys::add)
                }
            MENTION_REGEX
                .findAll(parent.summary + "\n" + parent.description)
                .map { it.value }
                .forEach(keys::add)
            keys.remove(parent.key)
            val children =
                keys.mapNotNull { k -> runCatching { fetchOne(k, config) }.getOrNull() }
            listOf(parent) + children
        } catch (e: Exception) {
            log.warn("[MCP] ticket-refinement fetch failed, falling back: {}", e.message)
            delegate.fetchParentWithLinkedTickets(config, parentKey)
        }
    }

    override fun postComment(
        config: JiraConfig?,
        ticketKey: String,
        body: String,
        format: String,
        fallbackMarkdown: String,
        greeting: String,
        openPointsLabel: String,
        openPoints: List<String>,
    ): String {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.jiraReady(it) }
                ?: return delegate.postComment(config, ticketKey, body, format, fallbackMarkdown, greeting, openPointsLabel, openPoints)
        if (config != null &&
            !config.baseUrl.trimEnd('/').equals(mcp.jiraBaseUrl.trimEnd('/'), ignoreCase = true)
        ) {
            log.debug(
                "[MCP→TicketRefinement] config '{}' targets '{}' but MCP is configured for '{}', using direct adapter",
                config.name,
                config.baseUrl,
                mcp.jiraBaseUrl,
            )
            return delegate.postComment(config, ticketKey, body, format, fallbackMarkdown, greeting, openPointsLabel, openPoints)
        }

        val mcpBody =
            if (format == "adf" && greeting.isNotBlank()) {
                adfCommentPreparer.prependGreeting(body, greeting, openPointsLabel, openPoints)
            } else {
                body
            }
        return try {
            val r =
                gateway.callTool(
                    mcp,
                    "jira_add_comment",
                    mapOf("key" to ticketKey, "body" to mcpBody),
                )
            if (r.isError) {
                log.warn("[MCP] jira_add_comment error, falling back: {}", r.text)
                delegate.postComment(config, ticketKey, body, format, fallbackMarkdown, greeting, openPointsLabel, openPoints)
            } else {
                McpRouting.parseJsonOrNull(objectMapper, r.text)?.path("id")?.asText("") ?: ""
            }
        } catch (e: Exception) {
            log.warn("[MCP] jira_add_comment exception, falling back: {}", e.message)
            delegate.postComment(config, ticketKey, body, format, fallbackMarkdown, greeting, openPointsLabel, openPoints)
        }
    }

    override fun renderAdfToHtml(adfJson: String?): String? = delegate.renderAdfToHtml(adfJson)

    private fun fetchOne(
        key: String,
        config: JiraConfig?,
    ): TicketRefinementTicket {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.jiraReady(it) }
                ?: throw IllegalStateException("mcp not active")
        val r = gateway.callTool(mcp, "jira_get_issue", mapOf("key" to key))
        if (r.isError) throw IllegalStateException(r.text)
        val node =
            McpRouting.parseJsonOrNull(objectMapper, r.text)
                ?: throw IllegalStateException("invalid MCP response for $key")
        return mapNode(node, key, config)
    }

    private fun mapNode(
        node: JsonNode,
        keyHint: String,
        config: JiraConfig?,
    ): TicketRefinementTicket {
        val key = node.path("key").asText("").ifBlank { keyHint }
        val fields = node.path("fields")
        val summary = fields.path("summary").asText("")
        val description =
            when {
                fields.path("description").isTextual -> fields.path("description").asText("")
                fields.path("description").isObject -> flattenAdf(fields.path("description"))
                else -> ""
            }
        val baseUrl = config?.baseUrl?.trimEnd('/') ?: ""
        val subtasks =
            fields
                .path("subtasks")
                .mapNotNull { st ->
                    val stKey = st.path("key").asText("").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val stSummary = st.path("fields").path("summary").asText("")
                    TicketRefinementSubtask(key = stKey, summary = stSummary)
                }
        return TicketRefinementTicket(
            key = key,
            summary = summary,
            description = description,
            url = if (baseUrl.isNotBlank()) "$baseUrl/browse/$key" else "",
            subtasks = subtasks,
        )
    }

    private fun flattenAdf(node: JsonNode): String {
        if (node.isTextual) return node.asText()
        if (!node.isContainerNode) return ""
        val sb = StringBuilder()
        node.forEach { sb.append(flattenAdf(it)).append(' ') }
        return sb.toString().trim()
    }

    companion object {
        private val MENTION_REGEX = Regex("[A-Z][A-Z0-9]+-\\d+")
    }
}
