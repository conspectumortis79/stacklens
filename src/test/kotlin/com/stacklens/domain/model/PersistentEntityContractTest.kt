package com.stacklens.domain.model

import com.stacklens.domain.model.knowledge.KnowledgeChunk
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.domain.model.knowledge.KnowledgeIngestionJob
import com.stacklens.domain.model.knowledge.KnowledgeJobStatus
import com.stacklens.domain.model.knowledge.KnowledgeRerankConfig
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.testsupport.BeanContractProbe
import com.stacklens.testsupport.PersistentTypes
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class PersistentEntityContractTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("persistentTypes")
    fun `every persistent type is instantiable without arguments`(type: Class<*>) {
        assertNotNull(type.getDeclaredConstructor().newInstance(), type.name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("persistentTypes")
    fun `every mutable property round trips through its getter`(type: Class<*>) {
        val report = BeanContractProbe.roundTrip(type.getDeclaredConstructor().newInstance())

        assertTrue(report.properties.isNotEmpty(), "${type.name} exposes no JavaBean property")
        assertTrue(
            report.unsupported.isEmpty(),
            "${type.name} has setters the JavaBean contract cannot exercise: ${report.unsupported}",
        )
        assertEquals(
            report.properties
                .filterNot { it.setter == null }
                .map { it.name }
                .toSet(),
            report.exercised.toSet(),
            type.name,
        )
    }

    @Test
    fun `embedding config keeps the defaults the knowledge search relies on`() {
        val config = KnowledgeEmbeddingConfig()

        assertEquals(1L, config.id)
        assertTrue(config.enabled)
        assertEquals(KnowledgeEmbeddingProvider.OLLAMA, config.provider)
        assertEquals("nomic-embed-text", config.model)
        assertEquals("http://localhost:11434", config.ollamaUrl)
        assertEquals(1600, config.chunkSize)
        assertEquals(200, config.chunkOverlap)
        assertEquals(8, config.topK)
        assertEquals(0.0, config.minimumBm25Score)
        assertEquals(12000, config.maximumContextCharacters)
        assertEquals(KnowledgeRerankConfig.DISABLED, config.postRetrieval)
    }

    @Test
    fun `embedding config defaults every auto index flag to enabled`() {
        val config = KnowledgeEmbeddingConfig()

        assertTrue(config.autoIndexReview)
        assertTrue(config.autoIndexSprintReview)
        assertTrue(config.autoIndexTicketCreation)
        assertTrue(config.autoIndexTicketRefinement)
        assertTrue(config.autoIndexTicketSummary)
    }

    @Test
    fun `rerank defaults disable both post retrieval stages`() {
        val rerank = KnowledgeRerankConfig()

        assertFalse(rerank.queryRewritingEnabled)
        assertEquals(256, rerank.queryRewriteMaxOutputTokens)
        assertFalse(rerank.rerankEnabled)
        assertEquals(20, rerank.rerankCandidateCount)
        assertEquals(5, rerank.rerankFinalCount)
        assertEquals(512, rerank.rerankMaxOutputTokens)
        assertEquals("auto", rerank.rerankModel)
        assertEquals(rerank, KnowledgeRerankConfig.DISABLED)
    }

    @Test
    fun `ingestion job starts queued without progress`() {
        val job = KnowledgeIngestionJob()

        assertEquals(0L, job.id)
        assertEquals(KnowledgeJobStatus.QUEUED, job.status)
        assertEquals("queued", job.phase)
        assertEquals(0, job.processedDocuments)
        assertEquals(0, job.totalDocuments)
        assertEquals(0, job.processedChunks)
        assertEquals(0, job.totalChunks)
        assertNull(job.sourceId)
        assertNull(job.errorMessage)
    }

    @Test
    fun `source defaults to an enabled pending manual source that scans ten levels`() {
        val source = KnowledgeSource(knowledgeBaseId = 3L, name = "docs", type = KnowledgeSourceType.MANUAL)

        assertTrue(source.active)
        assertEquals(KnowledgeSourceStatus.PENDING, source.status)
        assertEquals(10, source.maxDepth)
        assertTrue(source.includeLinkedTickets)
        assertFalse(source.includeChildren)
        assertFalse(source.includeComments)
        assertFalse(source.includeParent)
        assertFalse(source.autoManaged)
        assertEquals(3L, source.knowledgeBaseId)
        assertNotNull(source.stableId)
        assertNotNull(source.createdAt)
        assertNotNull(source.updatedAt)
    }

    @Test
    fun `document and chunk start unindexed with their mandatory fields intact`() {
        val document =
            KnowledgeDocument(
                sourceId = 11L,
                knowledgeBaseId = 12L,
                title = "Handbuch",
                sourceType = KnowledgeSourceType.FILE,
                plainText = "text",
                contentHash = "abc",
            )
        val chunk =
            KnowledgeChunk(
                documentId = 21L,
                knowledgeBaseId = 22L,
                ordinal = 2,
                content = "chunk",
                contentHash = "def",
            )

        assertEquals(0L, document.id)
        assertNull(document.externalId)
        assertNull(document.sourceUri)
        assertNull(document.mimeType)
        assertNull(document.metadataJson)
        assertNull(document.indexedAt)
        assertNull(chunk.sectionTitle)
        assertNull(chunk.embeddingProfile)
        assertNull(chunk.embeddingDimension)
        assertNull(chunk.embeddingBlob)
        assertNull(chunk.embeddingNorm)
        assertEquals(0, chunk.tokenEstimate)
        assertNull(chunk.metadataJson)
    }

    companion object {
        @JvmStatic
        fun persistentTypes(): List<Class<*>> =
            PersistentTypes.annotatedIn(
                "com.stacklens.domain.model",
                Entity::class,
                Embeddable::class,
            )
    }
}
