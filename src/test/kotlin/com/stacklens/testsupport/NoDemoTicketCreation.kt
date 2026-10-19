package com.stacklens.testsupport

import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.ticket.TicketCreationDemoPort
import com.stacklens.domain.model.JiraConfig

object NoDemoTicketCreation : TicketCreationDemoPort {
    override fun isDemo(config: JiraConfig): Boolean = false

    override fun simulate(
        jiraConfigId: Long,
        projectKey: String,
        issueType: String,
        labels: List<String>,
        historyId: Long?,
        configName: String,
        subtasks: List<SubtaskDraft>,
    ): Map<String, Any?> = error("simulate must not be called without an active demo configuration")

    override fun simulatedSubtasks(
        projectKey: String,
        subtasks: List<SubtaskDraft>,
    ): List<Map<String, Any?>> = error("simulatedSubtasks must not be called without an active demo configuration")
}
