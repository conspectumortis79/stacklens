package com.stacklens.application.port.out

import com.stacklens.domain.model.JiraTicketSummary

interface JiraTicketSummaryPort {
    fun findAllByOrderByCreatedAtDesc(): List<JiraTicketSummary>

    fun findRecentJiraTicketSummaries(limit: Int): List<JiraTicketSummary>

    fun findJiraTicketSummaryById(id: Long): JiraTicketSummary?

    fun saveJiraTicketSummary(entity: JiraTicketSummary): JiraTicketSummary

    fun deleteJiraTicketSummaryById(id: Long)

    fun deleteAllJiraTicketSummaries()
}

interface JiraTicketSummaryJiraPort {
    fun fetchTicketWithComments(
        config: com.stacklens.domain.model.JiraConfig?,
        ticketKey: String,
    ): JiraTicketSummaryTicket?
}

data class JiraTicketSummaryTicket(
    val key: String,
    val summary: String,
    val description: String,
    val url: String,
    val status: String,
    val issueType: String,
    val priority: String,
    val assignee: String,
    val reporter: String,
    val labels: List<String>,
    val createdAt: String,
    val updatedAt: String,
    val comments: List<JiraTicketSummaryComment>,
)

data class JiraTicketSummaryComment(
    val author: String,
    val body: String,
    val createdAt: String,
)
