package com.stacklens.application.port.out.ticket

import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.domain.model.JiraConfig

interface TicketCreationDemoPort {
    fun isDemo(config: JiraConfig): Boolean

    fun simulate(
        jiraConfigId: Long,
        projectKey: String,
        issueType: String,
        labels: List<String>,
        historyId: Long?,
        configName: String,
        subtasks: List<SubtaskDraft>,
    ): Map<String, Any?>

    fun simulatedSubtasks(
        projectKey: String,
        subtasks: List<SubtaskDraft>,
    ): List<Map<String, Any?>>
}
