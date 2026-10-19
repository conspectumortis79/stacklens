package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.application.port.out.JiraTicketSummaryPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType

@ApplicationService
class TicketSummaryKnowledgeSourceIngestor(
    private val summaryPort: JiraTicketSummaryPort,
    private val mapper: AnalysisKnowledgeMapper,
    private val executor: KnowledgePublication,
) : KnowledgeSourceIngestor {
    override fun supports(type: KnowledgeSourceType): Boolean = type == KnowledgeSourceType.TICKET_SUMMARY

    override fun ingest(source: KnowledgeSource): KnowledgeSourceIngestionOutcome {
        val id = parseId(source) ?: return zero()
        val summary =
            summaryPort.findJiraTicketSummaryById(id)
                ?: throw NoSuchElementException("Ticket-summary analysis $id not found")
        val candidate =
            mapper.fromTicketSummary(summary)
                ?: throw IllegalStateException(
                    "Ticket-summary $id has no summaryMarkdown yet; cannot re-publish",
                )
        executor.publish(candidate)
        return KnowledgeSourceIngestionOutcome(ingestedCount = 1, anyDocFailed = false)
    }

    private fun parseId(source: KnowledgeSource): Long? {
        val ref = source.externalReference ?: return null
        val parts = ref.split(":")
        if (parts.size != 2 || parts[0] != "ticket-summary") return null
        return parts[1].toLongOrNull()
    }

    private fun zero(): KnowledgeSourceIngestionOutcome = KnowledgeSourceIngestionOutcome(0, false)
}
