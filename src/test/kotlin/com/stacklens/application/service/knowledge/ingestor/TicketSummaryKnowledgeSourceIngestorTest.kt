package com.stacklens.application.service.knowledge.ingestor
import com.stacklens.adapter.out.presentation.GermanKnowledgePresentation
import com.stacklens.application.port.out.JiraTicketSummaryPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.domain.model.JiraTicketSummary
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class TicketSummaryKnowledgeSourceIngestorTest {
    private val summaryPort: JiraTicketSummaryPort = mock()

    private val mapper =
        AnalysisKnowledgeMapper(TestJsonCodec.create(), GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation)

    private val executor: KnowledgePublication = mock()

    private val sut = TicketSummaryKnowledgeSourceIngestor(summaryPort, mapper, executor)

    @Test
    fun `supports only the ticket summary source type`() {
        assertTrue(sut.supports(KnowledgeSourceType.TICKET_SUMMARY))
        assertTrue(!sut.supports(KnowledgeSourceType.JIRA))
    }

    @Test
    fun `a source without an external reference ingests nothing`() {
        val outcome = sut.ingest(source(null))

        assertEquals(0, outcome.ingestedCount)
        assertTrue(!outcome.anyDocFailed)
        verifyNoInteractions(summaryPort, executor)
    }

    @Test
    fun `a reference of another analysis type is ignored`() {
        listOf("review-analysis:7", "ticket-summary", "ticket-summary:", "ticket-summary:abc", "jira:5:6", "ticket-summary:5:6")
            .forEach { reference ->
                val outcome = sut.ingest(source(reference))

                assertEquals(0, outcome.ingestedCount, "reference $reference must be ignored")
            }
        verifyNoInteractions(summaryPort, executor)
    }

    @Test
    fun `a missing summary fails the ingestion`() {
        whenever(summaryPort.findJiraTicketSummaryById(42L)).thenReturn(null)

        assertThrows(NoSuchElementException::class.java) { sut.ingest(source("ticket-summary:42")) }
    }

    @Test
    fun `a summary without markdown cannot be published`() {
        whenever(summaryPort.findJiraTicketSummaryById(42L)).thenReturn(summary(markdown = null))

        assertThrows(IllegalStateException::class.java) { sut.ingest(source("ticket-summary:42")) }
        verify(executor, never()).publish(any())
    }

    @Test
    fun `a summarized ticket is published as a knowledge candidate`() {
        whenever(summaryPort.findJiraTicketSummaryById(42L)).thenReturn(summary(markdown = "# Kurzfassung\n\nKern:"))

        val outcome = sut.ingest(source("ticket-summary:42"))

        assertEquals(1, outcome.ingestedCount)
        verify(executor)
            .publish(
                check { candidate ->
                    assertEquals(KnowledgeSourceType.TICKET_SUMMARY, candidate.sourceType)
                    assertEquals("ticket-summary:42", candidate.externalId)
                    assertEquals("Ticket-Zusammenfassung: PROJ-42", candidate.title)
                    assertTrue(candidate.content.contains("Kern:"), candidate.content)
                },
            )
    }

    private fun source(externalReference: String?): KnowledgeSource =
        KnowledgeSource(
            id = 3L,
            knowledgeBaseId = 9L,
            name = "summary-source",
            type = KnowledgeSourceType.TICKET_SUMMARY,
            externalReference = externalReference,
        )

    private fun summary(markdown: String?): JiraTicketSummary =
        JiraTicketSummary(
            id = 42L,
            ticketKey = "PROJ-42",
            summaryMarkdown = markdown,
        )
}
