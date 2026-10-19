package com.stacklens.application.port.out

import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.jira.TicketSummaryLine

interface TicketRefinementJiraPort {
    fun fetchParentWithLinkedTickets(
        config: JiraConfig?,
        parentKey: String,
    ): List<TicketRefinementTicket>

    fun postComment(
        config: JiraConfig?,
        ticketKey: String,
        body: String,
        format: String,
        fallbackMarkdown: String,
        greeting: String = "",
        openPointsLabel: String = "",
        openPoints: List<String> = emptyList(),
    ): String

    fun renderAdfToHtml(adfJson: String?): String?
}

data class TicketRefinementTicket(
    val key: String,
    val summary: String,
    val description: String,
    val url: String,
    val descriptionHtml: String? = null,
    val descriptionNative: String = "",
    val descriptionFormat: String = "text",
    val subtasks: List<TicketRefinementSubtask> = emptyList(),
    val referencedTickets: List<TicketRefinementSubtask> = emptyList(),
    val labels: List<String> = emptyList(),
)

data class TicketRefinementSubtask(
    override val key: String,
    override val summary: String,
    override val description: String = "",
) : TicketSummaryLine
