package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.application.port.out.ReviewAnalysisPort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ReviewAnalysisKnowledgeSourceIngestorParseIdCoverageTest {
    private val ingestor =
        ReviewAnalysisKnowledgeSourceIngestor(
            mock<ReviewAnalysisPort>(),
            mock<AnalysisKnowledgeMapper>(),
            mock<KnowledgePublication>(),
        )

    @Test
    fun `a source without an external reference has no identifier`() {
        assertNull(parseId(null))
    }

    @Test
    fun `a reference with a single segment has no identifier`() {
        assertNull(parseId("review-analysis"))
    }

    @Test
    fun `a two segment reference of another namespace has no identifier`() {
        assertNull(parseId("code-qa:12"))
    }

    @Test
    fun `a reference whose numeric tail is not a number has no identifier`() {
        assertNull(parseId("review-analysis:abc"))
    }

    @Test
    fun `a well formed reference resolves the analysis identifier`() {
        assertEquals(12L, parseId("review-analysis:12"))
    }

    private fun parseId(externalReference: String?): Any? = PrivateInvocation.invoke(ingestor, "parseId", source(externalReference))

    private fun source(externalReference: String?): KnowledgeSource =
        KnowledgeSource(
            knowledgeBaseId = 1L,
            name = "review analysis",
            type = KnowledgeSourceType.REVIEW_ANALYSIS,
            externalReference = externalReference,
        )
}
