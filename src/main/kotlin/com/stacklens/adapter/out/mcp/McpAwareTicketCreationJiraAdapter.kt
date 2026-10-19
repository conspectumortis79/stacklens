package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.domain.model.CreatedJiraTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.JiraCreateField
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component("mcpAwareTicketCreationJiraPort")
open class McpAwareTicketCreationJiraAdapter(
    private val mcpRepo: McpServerConfigPort,
    private val gateway: McpGatewayPort,
    private val objectMapper: ObjectMapper,
    @Qualifier("ticketCreationNativeJiraAdapter")
    private val delegate: TicketCreationJiraPort,
) : TicketCreationJiraPort {
    private val log = LoggerFactory.getLogger(McpAwareTicketCreationJiraAdapter::class.java)

    override fun createTicket(
        config: JiraConfig,
        projectKey: String,
        summary: String,
        description: String,
        issueType: String,
        labels: List<String>,
        priority: String,
        customFields: Map<String, Any>,
    ): CreatedJiraTicket {
        if (customFields.isNotEmpty()) {
            return delegate.createTicket(config, projectKey, summary, description, issueType, labels, priority, customFields)
        }
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.jiraReady(it) }
                ?: return delegate.createTicket(config, projectKey, summary, description, issueType, labels, priority)

        val args =
            buildMap<String, Any> {
                put("project_key", projectKey)
                put("summary", summary)
                put("issue_type", issueType)
                if (description.isNotBlank()) put("description", description)
                if (labels.isNotEmpty()) put("labels", labels)
                if (priority.isNotBlank()) put("priority", priority)
            }
        val result =
            try {
                gateway.callTool(mcp, "jira_create_issue", args)
            } catch (e: Exception) {
                log.warn("[MCP] jira_create_issue exception: {} — falling back to native", e.message)
                null
            }
        if (result == null || result.isError) {
            result?.let { log.warn("[MCP] jira_create_issue returned error: {} — falling back to native", it.text) }
            return delegate.createTicket(config, projectKey, summary, description, issueType, labels, priority)
        }
        val node = McpRouting.parseJsonOrNull(objectMapper, result.text)
        val key = node?.path("key")?.asText("") ?: ""
        val id = node?.path("id")?.asText("") ?: ""
        val url =
            if (key.isNotBlank()) {
                "${config.baseUrl.trimEnd('/')}/browse/$key"
            } else {
                ""
            }
        log.info("[MCP] jira_create_issue succeeded: key={}", key)
        return CreatedJiraTicket(key = key, id = id, url = url)
    }

    override fun requiredCreateFields(
        config: JiraConfig,
        projectKey: String,
        issueType: String,
    ): List<JiraCreateField> = delegate.requiredCreateFields(config, projectKey, issueType)

    override fun createSubtask(
        config: JiraConfig,
        parentKey: String,
        projectKey: String,
        summary: String,
        description: String,
        issueType: String,
        labels: List<String>,
        priority: String,
    ): CreatedJiraTicket {
        val mcp =
            McpRouting.activeIfRouted(mcpRepo) { McpRouting.jiraReady(it) }
                ?: return delegate.createSubtask(config, parentKey, projectKey, summary, description, issueType, labels, priority)

        val args =
            buildMap<String, Any> {
                put("project_key", projectKey)
                put("summary", summary)
                put("issue_type", issueType)
                put("parent_key", parentKey)
                if (description.isNotBlank()) put("description", description)
                if (labels.isNotEmpty()) put("labels", labels)
                if (priority.isNotBlank()) put("priority", priority)
            }
        val result =
            try {
                gateway.callTool(mcp, "jira_create_issue", args)
            } catch (e: Exception) {
                log.warn("[MCP] jira_create_subtask exception: {} — falling back to native", e.message)
                null
            }
        if (result == null || result.isError) {
            result?.let { log.warn("[MCP] jira_create_subtask returned error: {} — falling back to native", it.text) }
            return delegate.createSubtask(config, parentKey, projectKey, summary, description, issueType, labels, priority)
        }
        val node = McpRouting.parseJsonOrNull(objectMapper, result.text)
        val key = node?.path("key")?.asText("") ?: ""
        val id = node?.path("id")?.asText("") ?: ""
        val url =
            if (key.isNotBlank()) {
                "${config.baseUrl.trimEnd('/')}/browse/$key"
            } else {
                ""
            }
        log.info("[MCP] jira_create_subtask succeeded: parent={}, key={}", parentKey, key)
        return CreatedJiraTicket(key = key, id = id, url = url)
    }
}
