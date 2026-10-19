package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.ai.KnowledgeRerankPort
import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.domain.model.knowledge.RetrievedKnowledge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeRetrievalServiceTicketKeyTest {
    private val basePort: KnowledgeBasePort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val embeddingDispatcher: KnowledgeEmbeddingDispatcher = mock()
    private val indexPort: KnowledgeIndexPort = mock()
    private val rerankPort: KnowledgeRerankPort = mock()

    private val service =
        KnowledgeRetrievalService(
            chunkPort,
            documentPort,
            basePort,
            configPort,
            embeddingDispatcher,
            indexPort,
            rerankPort,
        )

    private val queryVector = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)

    private fun stubDefaults() {
        whenever(embeddingDispatcher.embed(any())).thenReturn(
            EmbeddingResult(
                vectors = listOf(queryVector),
                dimension = queryVector.size,
                profile = "test|test|test|4|chunker-v1|1600|200",
                model = "test-model",
            ),
        )
        whenever(configPort.get()).thenReturn(defaultConfig())
        whenever(basePort.findAllByActiveTrue()).thenReturn(listOf(activeBase(1L)))
        whenever(indexPort.searchByVector(eq(1L), any(), any())).thenReturn(emptyList())
        whenever(rerankPort.rerankChunks(any(), any(), any(), any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            inv.arguments[1] as List<RetrievedKnowledge>
        }
    }

    @Test
    fun `retrieveForChat resolves a bare ticket key to its indexed chunks`() {
        stubDefaults()
        whenever(indexPort.searchByTicketKey(eq(1L), eq("BINIT-38092"))).thenReturn(
            listOf(
                indexedChunk(
                    chunkId = 100L,
                    documentId = 10L,
                    title = "binit-38092 – Grafana-Dashboard",
                    ticketKey = "binit-38092",
                ),
            ),
        )

        val result = service.retrieveForChat("binit-38092")

        assertEquals(1, result.size)
        assertEquals("binit-38092 – Grafana-Dashboard", result[0].title)
        verify(indexPort).searchByTicketKey(eq(1L), eq("BINIT-38092"))
    }

    @Test
    fun `retrieveForChat returns no knowledge when neither vector nor ticket-key lookup hit`() {
        stubDefaults()
        whenever(indexPort.searchByTicketKey(eq(1L), any())).thenReturn(emptyList())

        val result = service.retrieveForChat("binit-38092")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `retrieveForChat merges exact ticket hits with vector hits and dedupes by chunkId`() {
        stubDefaults()
        val exactHit =
            indexedChunk(
                chunkId = 100L,
                documentId = 10L,
                title = "binit-38092 – title",
                ticketKey = "binit-38092",
                score = 1.5,
            )
        val vectorHit =
            indexedChunk(
                chunkId = 100L,
                documentId = 10L,
                title = "binit-38092 – title",
                ticketKey = null,
                score = 0.62,
            )
        whenever(indexPort.searchByTicketKey(eq(1L), eq("BINIT-38092"))).thenReturn(listOf(exactHit))
        whenever(indexPort.searchByVector(eq(1L), any(), any())).thenReturn(listOf(vectorHit))

        val result = service.retrieveForChat("binit-38092")

        assertEquals(1, result.size, "expected dedup of identical chunkId")
        assertEquals(1.5, result[0].score)
    }

    @Test
    fun `retrieveForChat extracts ticket keys from a sentence, not only from bare queries`() {
        stubDefaults()
        whenever(indexPort.searchByTicketKey(eq(1L), eq("BINIT-38092"))).thenReturn(
            listOf(
                indexedChunk(
                    chunkId = 100L,
                    documentId = 10L,
                    title = "binit-38092 – title",
                    ticketKey = "binit-38092",
                ),
            ),
        )
        whenever(indexPort.searchByTicketKey(eq(1L), eq("OTHER-1"))).thenReturn(emptyList())

        service.retrieveForChat("Was sagt binit-38092 dazu?")

        verify(indexPort).searchByTicketKey(eq(1L), eq("BINIT-38092"))
    }

    private fun indexedChunk(
        chunkId: Long,
        documentId: Long,
        title: String,
        ticketKey: String?,
        score: Double = 1.5,
    ): IndexedKnowledgeChunk =
        IndexedKnowledgeChunk(
            chunkId = chunkId,
            documentId = documentId,
            knowledgeBaseId = 1L,
            title = title,
            sectionTitle = null,
            content = "Body for $title",
            sourceType = KnowledgeSourceType.JIRA_SPRINT.name,
            sourceUri = "https://example.atlassian.net/browse/$ticketKey",
            embedding = null,
            score = score,
            citation = "KB:document-$documentId#chunk-0",
            ticketKey = ticketKey,
            sprintReference = null,
        )

    private fun defaultConfig(): KnowledgeEmbeddingConfig =
        KnowledgeEmbeddingConfig(
            chunkSize = 1600,
            chunkOverlap = 200,
        )

    private fun activeBase(id: Long): KnowledgeBase =
        KnowledgeBase(
            id = id,
            name = "KB-$id",
            description = "",
            includeInGlobalChat = true,
            active = true,
            createdAt = java.time.LocalDateTime.now(),
        )
}
