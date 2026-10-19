package com.stacklens.domain.model.knowledge

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnowledgeEmbeddingConfigAutoIndexTest {
    @Test
    fun `every analysis source type follows its own flag when the flag is enabled`() {
        val config = KnowledgeEmbeddingConfig()

        assertTrue(config.autoIndexFor(KnowledgeSourceType.REVIEW_ANALYSIS))
        assertTrue(config.autoIndexFor(KnowledgeSourceType.SPRINT_REVIEW))
        assertTrue(config.autoIndexFor(KnowledgeSourceType.TICKET_CREATION))
        assertTrue(config.autoIndexFor(KnowledgeSourceType.TICKET_REFINEMENT))
        assertTrue(config.autoIndexFor(KnowledgeSourceType.TICKET_SUMMARY))
    }

    @Test
    fun `every analysis source type follows its own flag when the flag is disabled`() {
        val config =
            KnowledgeEmbeddingConfig(
                autoIndexReview = false,
                autoIndexSprintReview = false,
                autoIndexTicketCreation = false,
                autoIndexTicketRefinement = false,
                autoIndexTicketSummary = false,
            )

        assertFalse(config.autoIndexFor(KnowledgeSourceType.REVIEW_ANALYSIS))
        assertFalse(config.autoIndexFor(KnowledgeSourceType.SPRINT_REVIEW))
        assertFalse(config.autoIndexFor(KnowledgeSourceType.TICKET_CREATION))
        assertFalse(config.autoIndexFor(KnowledgeSourceType.TICKET_REFINEMENT))
        assertFalse(config.autoIndexFor(KnowledgeSourceType.TICKET_SUMMARY))
    }

    @Test
    fun `the flags are independent of each other`() {
        val config =
            KnowledgeEmbeddingConfig(
                autoIndexReview = true,
                autoIndexSprintReview = false,
                autoIndexTicketCreation = true,
                autoIndexTicketRefinement = false,
                autoIndexTicketSummary = true,
            )

        assertTrue(config.autoIndexFor(KnowledgeSourceType.REVIEW_ANALYSIS))
        assertFalse(config.autoIndexFor(KnowledgeSourceType.SPRINT_REVIEW))
        assertTrue(config.autoIndexFor(KnowledgeSourceType.TICKET_CREATION))
        assertFalse(config.autoIndexFor(KnowledgeSourceType.TICKET_REFINEMENT))
        assertTrue(config.autoIndexFor(KnowledgeSourceType.TICKET_SUMMARY))
    }

    @Test
    fun `ingested sources are always indexed regardless of the analysis flags`() {
        val config =
            KnowledgeEmbeddingConfig(
                autoIndexReview = false,
                autoIndexSprintReview = false,
                autoIndexTicketCreation = false,
                autoIndexTicketRefinement = false,
                autoIndexTicketSummary = false,
            )

        KnowledgeSourceType
            .entries
            .filterNot { it in ANALYSIS_SOURCE_TYPES }
            .forEach { assertTrue(config.autoIndexFor(it), it.name) }
    }

    companion object {
        private val ANALYSIS_SOURCE_TYPES =
            setOf(
                KnowledgeSourceType.REVIEW_ANALYSIS,
                KnowledgeSourceType.SPRINT_REVIEW,
                KnowledgeSourceType.TICKET_CREATION,
                KnowledgeSourceType.TICKET_REFINEMENT,
                KnowledgeSourceType.TICKET_SUMMARY,
            )
    }
}
