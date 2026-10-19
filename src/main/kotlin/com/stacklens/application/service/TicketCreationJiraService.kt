package com.stacklens.application.service

import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.application.port.out.TicketCreationRecordPort
import com.stacklens.application.port.out.ticket.TicketCreationDemoPort
import com.stacklens.domain.model.JiraCreateField
import com.stacklens.domain.model.TicketCreationRecord

class TicketCreationJiraService(
    private val jiraConfigPort: JiraConfigPort,
    private val jiraPort: TicketCreationJiraPort,
    private val demoTicketSimulator: TicketCreationDemoPort,
    private val subtaskCreator: JiraSubtaskCreator,
    private val recordPort: TicketCreationRecordPort,
) {
    fun createJiraTicket(
        jiraConfigId: Long,
        projectKey: String,
        summary: String,
        description: String,
        issueType: String,
        labels: List<String>,
        historyId: Long?,
        priority: String,
        subtasks: List<SubtaskDraft>,
        customFields: Map<String, Any>,
    ): Map<String, Any?> {
        val config =
            jiraConfigPort.findJiraConfigById(jiraConfigId)
                ?: throw IllegalArgumentException("Jira configuration not found: $jiraConfigId")

        if (demoTicketSimulator.isDemo(config)) {
            return demoTicketSimulator.simulate(
                jiraConfigId,
                projectKey,
                issueType,
                labels,
                historyId,
                config.name,
                subtasks,
            )
        }

        val created = jiraPort.createTicket(config, projectKey, summary, description, issueType, labels, priority, customFields)

        val createdSubtasks = subtaskCreator.createAll(config, created.key, projectKey, subtasks)

        if (historyId != null) {
            val existing = recordPort.findTicketCreationRecordById(historyId)
            if (existing != null) {
                recordPort.saveTicketCreationRecord(
                    TicketCreationRecord(
                        id = existing.id,
                        inputText = existing.inputText,
                        summary = existing.summary,
                        issueType = issueType,
                        labels = existing.labels,
                        resultJson = existing.resultJson,
                        status = existing.status,
                        errorMessage = existing.errorMessage,
                        jiraConfigId = jiraConfigId,
                        jiraProjectKey = projectKey,
                        jiraKey = created.key,
                        jiraUrl = created.url,
                        createdAt = existing.createdAt,
                    ),
                )
            }
        }

        return buildMap<String, Any?> {
            put("key", created.key)
            put("id", created.id)
            put("url", created.url)
            put("subtasks", createdSubtasks)
        }
    }

    fun jiraConfigExists(id: Long): Boolean = jiraConfigPort.findJiraConfigById(id) != null

    fun requiredJiraFields(
        jiraConfigId: Long,
        projectKey: String,
        issueType: String,
    ): List<JiraCreateField> {
        val config =
            jiraConfigPort.findJiraConfigById(jiraConfigId)
                ?: throw IllegalArgumentException("Jira configuration not found: $jiraConfigId")
        if (demoTicketSimulator.isDemo(config)) return emptyList()
        return jiraPort.requiredCreateFields(config, projectKey, issueType)
    }
}
