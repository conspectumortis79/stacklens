package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.knowledge.KnowledgePublicationCandidate
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TicketRefinementKnowledgeSourceIngestorShapeCoverageTest {
    private val repository = mock<TicketRefinementPort>()

    private val mapper = mock<AnalysisKnowledgeMapper>()

    private val executor = mock<KnowledgePublication>()

    private val ingestor = TicketRefinementKnowledgeSourceIngestor(repository, mapper, executor)

    private fun candidate(
        title: String,
        ticketKey: String? = null,
    ): KnowledgePublicationCandidate =
        KnowledgePublicationCandidate(
            sourceType = KnowledgeSourceType.TICKET_REFINEMENT,
            externalId = "x",
            title = title,
            sourceUri = null,
            content = "c",
            metadata = if (ticketKey == null) emptyMap() else mapOf("ticketKey" to ticketKey),
        )

    private fun source(reference: String?): KnowledgeSource {
        val source = mock<KnowledgeSource>()
        whenever(source.externalReference).thenReturn(reference)
        whenever(source.id).thenReturn(9L)
        return source
    }

    @Test
    fun `a reference that is not a triple is skipped`() {
        whenever(repository.findTicketRefinementById(any())).thenReturn(mock<TicketRefinement>())
        whenever(mapper.fromTicketRefinement(any())).thenReturn(listOf(candidate("Done KEY-1")))

        listOf(null, "jira:1:KEY-1", "ticket-refinement:abc:KEY-1", "ticket-refinement:7:KEY-1").forEach { reference ->
            val outcome = ingestor.ingest(source(reference))
            val skipped = reference == null || reference.startsWith("jira:") || reference == "ticket-refinement:abc:KEY-1"
            assertEquals(if (skipped) 0 else 1, outcome.ingestedCount, reference.toString())
        }
    }

    @Test
    fun `a refinement id that is unknown fails the ingestion`() {
        whenever(repository.findTicketRefinementById(anyOrNull())).thenReturn(null)

        assertThrows(NoSuchElementException::class.java) {
            ingestor.ingest(source("ticket-refinement:7:KEY-1"))
        }
    }

    @Test
    fun `the ticket is found by its metadata when the title does not carry the key`() {
        whenever(repository.findTicketRefinementById(any())).thenReturn(mock<TicketRefinement>())
        whenever(mapper.fromTicketRefinement(any()))
            .thenReturn(listOf(candidate("Review", "KEY-1"), candidate("Other KEY-2"), candidate("Done KEY-1")))

        val outcome = ingestor.ingest(source("ticket-refinement:7:KEY-1"))

        assertEquals(1, outcome.ingestedCount)
    }

    @Test
    fun `a missing ticket inside the analysis is reported`() {
        whenever(repository.findTicketRefinementById(any())).thenReturn(mock<TicketRefinement>())
        whenever(mapper.fromTicketRefinement(any())).thenReturn(listOf(candidate("Other KEY-2")))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                ingestor.ingest(source("ticket-refinement:7:KEY-1"))
            }

        assertEquals(true, failure.message!!.contains("KEY-1"))
    }

    @Test
    fun `supports only the ticket refinement type`() {
        assertEquals(true, ingestor.supports(KnowledgeSourceType.TICKET_REFINEMENT))
        assertEquals(false, ingestor.supports(KnowledgeSourceType.JIRA))
    }

    @Test
    fun `the reference parser keeps the ticket key when it contains colons itself`() {
        val parsed = PrivateInvocation.invoke(ingestor, "parseParts", source("ticket-refinement:7:KEY:1")) as Pair<*, *>?

        assertEquals(7L to "KEY:1", parsed)
    }
}
