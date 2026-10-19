package com.stacklens.domain.model.jira

interface TicketSummaryLine {
    val key: String

    val summary: String

    val description: String
}
