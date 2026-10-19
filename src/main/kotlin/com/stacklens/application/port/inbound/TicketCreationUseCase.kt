package com.stacklens.application.port.inbound

import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.domain.model.IssueType
import com.stacklens.domain.model.JiraCreateField
import com.stacklens.domain.model.TicketCreationRecord

interface TicketCreationReadUseCase {
    fun getAllRecords(): List<TicketCreationRecord>

    fun getRecord(id: Long): TicketCreationRecord?

    fun jiraConfigExists(id: Long): Boolean

    fun requiredJiraFields(
        jiraConfigId: Long,
        projectKey: String,
        issueType: String,
    ): List<JiraCreateField>
}

interface TicketCreationWriteUseCase {
    fun generateTicket(
        inputText: String,
        attachmentSection: String = "",
        issueType: IssueType? = null,
    ): Map<String, Any?>

    fun createJiraTicket(
        jiraConfigId: Long,
        projectKey: String,
        summary: String,
        description: String,
        issueType: String,
        labels: List<String>,
        historyId: Long? = null,
        priority: String = "Minor",
        subtasks: List<SubtaskDraft> = emptyList(),
        customFields: Map<String, Any> = emptyMap(),
    ): Map<String, Any?>

    fun cancelRecord(id: Long): Boolean

    fun deleteRecord(id: Long)

    fun deleteAllRecords()
}

interface TicketCreationUseCase :
    TicketCreationReadUseCase,
    TicketCreationWriteUseCase
