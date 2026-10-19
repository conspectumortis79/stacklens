package com.stacklens.application.service.knowledge.ingestor
import com.stacklens.adapter.out.presentation.GermanKnowledgePresentation
import com.stacklens.application.port.out.ReviewAnalysisPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.domain.model.ReviewAnalysis
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

class ReviewAnalysisKnowledgeSourceIngestorTest {
    private val repository: ReviewAnalysisPort = mock()
    private val mapper =
        AnalysisKnowledgeMapper(TestJsonCodec.create(), GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation, GermanKnowledgePresentation)
    private val executor: KnowledgePublication = mock()
    private val sut =
        ReviewAnalysisKnowledgeSourceIngestor(
            repository = repository,
            mapper = mapper,
            executor = executor,
        )

    @Test
    fun `supports is true for REVIEW_ANALYSIS and false for every other type`() {
        assertThat(sut.supports(KnowledgeSourceType.REVIEW_ANALYSIS)).isTrue()
        assertThat(sut.supports(KnowledgeSourceType.TICKET_REFINEMENT)).isFalse()
        assertThat(sut.supports(KnowledgeSourceType.JIRA)).isFalse()
        assertThat(sut.supports(KnowledgeSourceType.MANUAL)).isFalse()
    }

    @Test
    fun `ingest returns zero outcome when external reference is missing`() {
        val source = source(externalReference = null)
        val outcome = sut.ingest(source)

        assertThat(outcome.ingestedCount).isZero()
        verifyNoInteractions(repository, executor)
    }

    @Test
    fun `ingest returns zero outcome when external reference has wrong prefix`() {
        val source = source(externalReference = "ticket-refinement:42")
        val outcome = sut.ingest(source)

        assertThat(outcome.ingestedCount).isZero()
        verifyNoInteractions(repository, executor)
    }

    @Test
    fun `ingest throws NoSuchElement when the repository has no matching analysis`() {
        val source = source(externalReference = "review-analysis:42")
        whenever(repository.findReviewAnalysisById(42L)).thenReturn(null)

        assertThrows(NoSuchElementException::class.java) { sut.ingest(source) }
        verify(executor, never()).publish(any())
    }

    @Test
    fun `ingest throws when the analysis has no resultMarkdown`() {
        val source = source(externalReference = "review-analysis:42")
        whenever(repository.findReviewAnalysisById(42L)).thenReturn(
            reviewAnalysis(id = 42L, resultMarkdown = null),
        )

        assertThrows(IllegalStateException::class.java) { sut.ingest(source) }
        verify(executor, never()).publish(any())
    }

    @Test
    fun `ingest publishes a candidate built from the resultMarkdown`() {
        val source = source(externalReference = "review-analysis:42")
        whenever(repository.findReviewAnalysisById(42L)).thenReturn(
            reviewAnalysis(id = 42L, resultMarkdown = "# Sprint review\n\nAll good."),
        )

        val outcome = sut.ingest(source)

        assertThat(outcome.ingestedCount).isEqualTo(1)
        assertThat(outcome.anyDocFailed).isFalse()
        verify(executor).publish(
            org.mockito.kotlin.check { candidate ->
                assertThat(candidate.sourceType).isEqualTo(KnowledgeSourceType.REVIEW_ANALYSIS)
                assertThat(candidate.externalId).isEqualTo("review-analysis:42")
                assertThat(candidate.title).contains("42")
                assertThat(candidate.content).contains("Sprint review")
            },
        )
    }

    private fun source(externalReference: String?): KnowledgeSource =
        KnowledgeSource(
            id = 1L,
            knowledgeBaseId = 10L,
            name = "review-source",
            type = KnowledgeSourceType.REVIEW_ANALYSIS,
            externalReference = externalReference,
        )

    private fun reviewAnalysis(
        id: Long,
        resultMarkdown: String?,
    ): ReviewAnalysis =
        ReviewAnalysis(
            id = id,
            jiraSprintUrl = "https://test.atlassian.net/jira/software/c/projects/PROJ/boards/1?sprint=42",
            resultMarkdown = resultMarkdown,
        )
}
