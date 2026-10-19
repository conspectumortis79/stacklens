package com.stacklens.application.port.inbound

import com.stacklens.domain.model.JiraTicketSummary

interface JiraTicketSummaryReadUseCase {
    fun getSummary(id: Long): JiraTicketSummary?

    fun getAllSummaries(): List<JiraTicketSummary>

    fun firstActiveJiraConfigId(): Long?

    fun jiraConfigExists(id: Long): Boolean

    fun defaultPromptTemplate(): String
}

interface JiraTicketSummaryWriteUseCase {
    fun startSummary(
        ticketKey: String,
        jiraConfigId: Long?,
        templateOverride: String? = null,
    ): JiraTicketSummary

    fun cancelSummary(id: Long)

    fun deleteSummary(id: Long)

    fun deleteAllSummaries()
}

interface JiraTicketSummaryUseCase :
    JiraTicketSummaryReadUseCase,
    JiraTicketSummaryWriteUseCase
