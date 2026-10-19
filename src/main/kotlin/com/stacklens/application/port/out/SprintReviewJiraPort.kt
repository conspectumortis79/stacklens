package com.stacklens.application.port.out

import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SprintReviewRow
import com.stacklens.domain.model.jira.TicketSummaryLine

class SprintReviewAdfDocument(
    val json: String,
)

interface SprintReviewJiraPort {
    fun resolveSprintByName(
        config: JiraConfig?,
        sprintName: String,
    ): ResolvedSprint

    fun fetchSprintTickets(
        config: JiraConfig?,
        sprintId: Long,
    ): List<SprintReviewTicket>

    fun renderAdfDocument(
        rows: List<SprintReviewRow>,
        includeImprovement: Boolean,
        sprintName: String,
    ): SprintReviewAdfDocument

    fun appendAdfToDescription(
        config: JiraConfig?,
        targetKey: String,
        adfDocument: SprintReviewAdfDocument,
    ): String
}

data class ResolvedSprint(
    val id: Long,
    val name: String,
    val state: String,
    val boardId: Long? = null,
    val boardName: String? = null,
    val url: String,
)

data class SprintReviewTicket(
    override val key: String,
    override val summary: String,
    override val description: String,
    val url: String,
    val issueType: String = "",
    val status: String = "",
    val assignee: String? = null,
    val priority: String? = null,
    val labels: List<String> = emptyList(),
    val storyPoints: Double? = null,
    val subtasks: List<SprintReviewTicket> = emptyList(),
    val referencedTickets: List<SprintReviewTicket> = emptyList(),
    val numericCustomFields: List<String> = emptyList(),
) : TicketSummaryLine
