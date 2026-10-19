package com.stacklens.application.port.inbound.ticket

import com.stacklens.domain.model.JiraTicketImplementation

interface JiraTicketImplementationUseCase {
    fun getAllAnalyses(): List<JiraTicketImplementation>

    fun findAnalysisById(analysisId: Long): JiraTicketImplementation?

    fun deleteAnalysis(analysisId: Long)

    fun deleteAllFinishedAnalyses()

    fun fetchTicketAsText(
        ticketKey: String,
        jiraConfigId: Long,
    ): String
}
