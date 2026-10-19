package com.stacklens.application.service.knowledge.ingestor
import com.stacklens.adapter.out.presentation.GermanKnowledgePresentation
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.domain.model.TicketRefinement
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.TestJsonCodec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class TicketRefinementKnowledgeSourceIngestorTest {
    private val repository: TicketRefinementPort = mock()
    private val mapper =
        AnalysisKnowledgeMapper(TestJsonCodec.create(), GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation)
    private val executor: KnowledgePublication = mock()
    private val sut =
        TicketRefinementKnowledgeSourceIngestor(
            repository = repository,
            mapper = mapper,
            executor = executor,
        )

    @Test
    fun `supports is true for TICKET_REFINEMENT and false for every other type`() {
        assertThat(sut.supports(KnowledgeSourceType.TICKET_REFINEMENT)).isTrue()
        assertThat(sut.supports(KnowledgeSourceType.REVIEW_ANALYSIS)).isFalse()
        assertThat(sut.supports(KnowledgeSourceType.JIRA)).isFalse()
        assertThat(sut.supports(KnowledgeSourceType.MANUAL)).isFalse()
    }

    @Test
    fun `ingest returns zero outcome when external reference is missing`() {
        val source = source(externalReference = null)
        val outcome = sut.ingest(source)

        assertThat(outcome.ingestedCount).isZero()
        assertThat(outcome.anyDocFailed).isFalse()
        verifyNoInteractions(repository, executor)
    }

    @Test
    fun `ingest returns zero outcome when external reference has wrong prefix`() {
        val source = source(externalReference = "review-analysis:42")
        val outcome = sut.ingest(source)

        assertThat(outcome.ingestedCount).isZero()
        verifyNoInteractions(repository, executor)
    }

    @Test
    fun `ingest throws NoSuchElement when the repository has no matching refinement`() {
        val source = source(externalReference = "ticket-refinement:7:PROJ-42")
        whenever(repository.findTicketRefinementById(7L)).thenReturn(null)

        assertThrows(NoSuchElementException::class.java) { sut.ingest(source) }
        verify(executor, never()).publish(any())
    }

    @Test
    fun `ingest publishes exactly the candidate whose title matches the ticket key`() {
        val source = source(externalReference = "ticket-refinement:7:PROJ-42")
        val refinement =
            refinement(
                id = 7L,
                parentTicketKey = "PROJ-1",
                reviewsJson =
                    """
                    [
                      {"ticketKey":"PROJ-42","originalSummary":"Foo PROJ-42","ticketUrl":"https://x/PROJ-42"},
                      {"ticketKey":"PROJ-43","originalSummary":"Bar PROJ-43","ticketUrl":"https://x/PROJ-43"}
                    ]
                    """.trimIndent(),
            )
        whenever(repository.findTicketRefinementById(7L)).thenReturn(refinement)

        val outcome = sut.ingest(source)

        assertThat(outcome.ingestedCount).isEqualTo(1)
        assertThat(outcome.anyDocFailed).isFalse()
        verify(executor).publish(
            org.mockito.kotlin.check { candidate ->
                assertThat(candidate.sourceType).isEqualTo(KnowledgeSourceType.TICKET_REFINEMENT)
                assertThat(candidate.externalId).isEqualTo("ticket-refinement:7:PROJ-42")
                assertThat(candidate.title).contains("PROJ-42")
            },
        )
    }

    @Test
    fun `ingest throws when the matching candidate cannot be found by key`() {
        val source = source(externalReference = "ticket-refinement:7:PROJ-99")
        val refinement =
            refinement(
                id = 7L,
                parentTicketKey = "PROJ-1",
                reviewsJson =
                    """[{"ticketKey":"PROJ-42","originalSummary":"Foo PROJ-42"}]""",
            )
        whenever(repository.findTicketRefinementById(7L)).thenReturn(refinement)

        assertThrows(IllegalStateException::class.java) { sut.ingest(source) }
        verify(executor, never()).publish(any())
    }

    private fun source(externalReference: String?): KnowledgeSource =
        KnowledgeSource(
            id = 1L,
            knowledgeBaseId = 10L,
            name = "refinement-source",
            type = KnowledgeSourceType.TICKET_REFINEMENT,
            externalReference = externalReference,
        )

    private fun refinement(
        id: Long,
        parentTicketKey: String,
        reviewsJson: String?,
    ): TicketRefinement =
        TicketRefinement(
            id = id,
            parentTicketKey = parentTicketKey,
            reviewsJson = reviewsJson,
        )
}
