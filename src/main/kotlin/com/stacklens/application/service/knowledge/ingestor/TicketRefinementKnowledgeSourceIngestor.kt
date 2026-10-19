package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType

@ApplicationService
class TicketRefinementKnowledgeSourceIngestor(
    private val repository: TicketRefinementPort,
    private val mapper: AnalysisKnowledgeMapper,
    private val executor: KnowledgePublication,
) : KnowledgeSourceIngestor {
    override fun supports(type: KnowledgeSourceType): Boolean = type == KnowledgeSourceType.TICKET_REFINEMENT

    override fun ingest(source: KnowledgeSource): KnowledgeSourceIngestionOutcome {
        val parsed = parseParts(source) ?: return zero()
        val (id, ticketKey) = parsed
        val refinement =
            repository.findTicketRefinementById(id)
                ?: throw NoSuchElementException("Ticket-refinement analysis $id not found")
        val candidates = mapper.fromTicketRefinement(refinement)
        val match =
            candidates.firstOrNull { it.title.endsWith(" $ticketKey") || it.metadata["ticketKey"] == ticketKey }
                ?: throw IllegalStateException(
                    "Ticket-refinement $id no longer contains a per-ticket candidate for ticket $ticketKey",
                )
        executor.publish(match)
        return KnowledgeSourceIngestionOutcome(ingestedCount = 1, anyDocFailed = false)
    }

    private fun parseParts(source: KnowledgeSource): Pair<Long, String>? {
        val ref = source.externalReference ?: return null
        val parts = ref.split(":")
        if (parts.size < 3 || parts[0] != "ticket-refinement") return null
        val id = parts[1].toLongOrNull() ?: return null
        val ticketKey = parts.drop(2).joinToString(":")
        return id to ticketKey
    }

    private fun zero(): KnowledgeSourceIngestionOutcome = KnowledgeSourceIngestionOutcome(0, false)
}
