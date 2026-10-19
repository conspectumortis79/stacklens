package com.stacklens.application.port.out

import com.stacklens.domain.model.CreatedJiraTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.JiraCreateField

interface TicketCreationJiraPort {
    fun createTicket(
        config: JiraConfig,
        projectKey: String,
        summary: String,
        description: String,
        issueType: String,
        labels: List<String>,
        priority: String = "Minor",
        customFields: Map<String, Any> = emptyMap(),
    ): CreatedJiraTicket

    fun createSubtask(
        config: JiraConfig,
        parentKey: String,
        projectKey: String,
        summary: String,
        description: String,
        issueType: String = "Subtask",
        labels: List<String> = emptyList(),
        priority: String = "Minor",
    ): CreatedJiraTicket

    fun requiredCreateFields(
        config: JiraConfig,
        projectKey: String,
        issueType: String,
    ): List<JiraCreateField>
}
