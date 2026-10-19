
package com.stacklens.application.service

import com.stacklens.application.port.inbound.ticket.JiraTicketImplementationUseCase
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraTicketImplementationPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.jira.JiraTicketFetchPort
import com.stacklens.application.port.out.ticket.TicketTextFetchPort
import com.stacklens.application.port.out.transaction.TransactionPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.JiraKeyUtils
import com.stacklens.domain.model.JiraTicketImplementation
import org.slf4j.LoggerFactory

@ApplicationService
class JiraTicketImplementationService(
    private val analysisRepo: JiraTicketImplementationPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val jiraHttp: JiraTicketFetchPort,
    private val i18n: I18nPort,
    private val tx: TransactionPort,
) : JiraTicketImplementationUseCase,
    TicketTextFetchPort {
    companion object {
        private val log = LoggerFactory.getLogger(JiraTicketImplementationService::class.java)
    }

    override fun fetchTicketAsText(
        ticketKey: String,
        jiraConfigId: Long,
    ): String {
        val key = ticketKey.trim().uppercase()
        require(key.isNotBlank()) { i18n.t("ticket_implementation.be_invalid_ticket_key_blank") }
        require(JiraKeyUtils.KEY_REGEX.matches(key)) {
            i18n.t("ticket_implementation.be_invalid_ticket_format", key)
        }
        val config =
            jiraConfigRepo.findJiraConfigById(jiraConfigId)
                ?: throw IllegalArgumentException(i18n.t("review_analysis.be_jira_config_not_found"))
        val ticket = jiraHttp.fetchTicketByKey(config, key)
        return buildString {
            appendLine("Ticket: ${ticket.key}")
            appendLine("Type: ${ticket.issueType}")
            appendLine("Status: ${ticket.status}")
            if (!ticket.priority.isNullOrBlank()) appendLine("Priority: ${ticket.priority}")
            appendLine("Summary: ${ticket.summary}")
            if (ticket.components.isNotEmpty()) appendLine("Components: ${ticket.components.joinToString(", ")}")
            if (ticket.labels.isNotEmpty()) appendLine("Labels: ${ticket.labels.joinToString(", ")}")
            appendLine()
            if (ticket.description.isNotBlank()) {
                appendLine("## Description")
                appendLine(ticket.description)
                appendLine()
            }
            if (ticket.acceptanceCriteria.isNotEmpty()) {
                appendLine("## Acceptance Criteria")
                ticket.acceptanceCriteria.forEach { appendLine("- $it") }
                appendLine()
            }
            if (ticket.comments.isNotBlank()) {
                appendLine("## Comments")
                appendLine(ticket.comments.take(20000))
            }
        }.trim()
    }

    override fun getAllAnalyses(): List<JiraTicketImplementation> = analysisRepo.findRecentJiraTicketImplementations(AnalysisConstants.MAX_RECENT_ANALYSES)

    override fun findAnalysisById(analysisId: Long): JiraTicketImplementation? = analysisRepo.findJiraTicketImplementationById(analysisId)

    override fun deleteAnalysis(analysisId: Long) {
        tx.inTransaction { analysisRepo.deleteJiraTicketImplementationById(analysisId) }
    }

    override fun deleteAllFinishedAnalyses() {
        val removed = tx.inTransaction { analysisRepo.deleteFinishedAnalyses() }
        log.info("deleteAllFinishedAnalyses: removed {} rows", removed)
    }
}
