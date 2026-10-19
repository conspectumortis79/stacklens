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
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeRetrievalServiceIndexTest {
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

    private fun stubQueryEmbedding() {
        whenever(embeddingDispatcher.embed(listOf("hello"))).thenReturn(
            EmbeddingResult(
                vectors = listOf(queryVector),
                dimension = queryVector.size,
                profile = "test|test|test|4|chunker-v1|1600|200",
                model = "test-model",
            ),
        )
        whenever(configPort.get()).thenReturn(
            KnowledgeEmbeddingConfig(
                enabled = true,
                provider = KnowledgeEmbeddingProvider.OLLAMA,
                model = "test-model",
            ),
        )
    }

    @Test
    fun `index hits are returned as RetrievedKnowledge`() {
        stubQueryEmbedding()
        whenever(basePort.findAllByActiveTrue()).thenReturn(
            listOf(
                KnowledgeBase(id = 1L, name = "B1", includeInGlobalChat = true, systemManaged = false),
            ),
        )
        whenever(indexPort.searchByVector(eq(1L), eq(queryVector), eq(8))).thenReturn(
            listOf(
                IndexedKnowledgeChunk(
                    chunkId = 42L,
                    documentId = 9L,
                    knowledgeBaseId = 1L,
                    title = "Doc",
                    sectionTitle = null,
                    content = "hello world",
                    sourceType = "MANUAL",
                    sourceUri = null,
                    embedding = queryVector,
                    score = 0.91,
                    citation = "KB:document-9#chunk-1",
                ),
            ),
        )
        val result = service.retrieveForChat("hello", limit = 8)
        assertEquals(1, result.size)
        assertEquals(42L, result[0].chunkId)
        assertEquals("KB:document-9#chunk-1", result[0].citation)
        assertEquals(0.91, result[0].score, 0.0001)
    }

    @Test
    fun `empty KNN result returns empty list (no keyword fallback)`() {
        stubQueryEmbedding()
        whenever(basePort.findAllByActiveTrue()).thenReturn(
            listOf(
                KnowledgeBase(id = 1L, name = "B1", includeInGlobalChat = true, systemManaged = false),
            ),
        )
        whenever(indexPort.searchByVector(any(), any(), any())).thenReturn(emptyList())

        val result = service.retrieveForChat("hello", limit = 8)

        assertTrue(result.isEmpty(), "vector-only path must not fall back to BM25")
        verify(chunkPort, never()).findByKnowledgeBaseId(any())
        verify(documentPort, never()).findById(any())
    }

    @Test
    fun `embedding failure yields empty result without throwing`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig())
        whenever(basePort.findAllByActiveTrue()).thenReturn(
            listOf(
                KnowledgeBase(id = 1L, name = "B1", includeInGlobalChat = true, systemManaged = false),
            ),
        )
        whenever(embeddingDispatcher.embed(any())).thenThrow(
            IllegalStateException("provider down"),
        )

        val result = service.retrieveForChat("hello", limit = 8)

        assertTrue(result.isEmpty())
        verify(indexPort, never()).searchByVector(any(), any(), any())
    }

    @Test
    fun `disabled embedding config short-circuits to empty result`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(enabled = false))
        whenever(basePort.findAllByActiveTrue()).thenReturn(
            listOf(
                KnowledgeBase(id = 1L, name = "B1", includeInGlobalChat = true, systemManaged = false),
            ),
        )

        val result = service.retrieveForChat("hello", limit = 8)

        assertTrue(result.isEmpty())
        verify(embeddingDispatcher, never()).embed(any())
        verify(indexPort, never()).searchByVector(any(), any(), any())
    }

    @Test
    fun `empty query returns empty list without consulting any port`() {
        assertTrue(service.retrieveForChat("", limit = 8).isEmpty())
        verify(embeddingDispatcher, never()).embed(any())
        verify(indexPort, never()).searchByVector(any(), any(), any())
    }

    @Test
    fun `no active bases returns empty list without embedding the question`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig())
        whenever(basePort.findAllByActiveTrue()).thenReturn(emptyList())

        val result = service.retrieveForChat("hello", limit = 8)

        assertTrue(result.isEmpty())
        verify(embeddingDispatcher, never()).embed(any())
    }

    @Test
    fun `hits across multiple bases are deduplicated by chunk id`() {
        stubQueryEmbedding()
        whenever(basePort.findAllByActiveTrue()).thenReturn(
            listOf(
                KnowledgeBase(id = 1L, name = "B1", includeInGlobalChat = true, systemManaged = false),
                KnowledgeBase(id = 2L, name = "B2", includeInGlobalChat = true, systemManaged = false),
            ),
        )
        whenever(indexPort.searchByVector(eq(1L), any(), any())).thenReturn(
            listOf(indexedHit(chunkId = 7L, baseId = 1L, score = 0.5)),
        )
        whenever(indexPort.searchByVector(eq(2L), any(), any())).thenReturn(
            listOf(indexedHit(chunkId = 7L, baseId = 2L, score = 0.8)),
        )

        val result = service.retrieveForChat("hello", limit = 8)

        assertEquals(1, result.size, "duplicate chunkId must collapse to one hit")
        assertEquals(0.8, result[0].score, 0.0001)
        assertEquals(2L, result[0].knowledgeBaseId)
    }

    @Test
    fun `hits are returned sorted by descending cosine score`() {
        stubQueryEmbedding()
        whenever(basePort.findAllByActiveTrue()).thenReturn(
            listOf(
                KnowledgeBase(id = 1L, name = "B1", includeInGlobalChat = true, systemManaged = false),
            ),
        )
        whenever(indexPort.searchByVector(eq(1L), any(), any())).thenReturn(
            listOf(
                indexedHit(chunkId = 0L, baseId = 1L, score = 0.30),
                indexedHit(chunkId = 1L, baseId = 1L, score = 0.95),
                indexedHit(chunkId = 2L, baseId = 1L, score = 0.62),
            ),
        )

        val result = service.retrieveForChat("hello", limit = 8)

        assertEquals(listOf(1L, 2L, 0L), result.map { it.chunkId })
    }

    @Test
    fun `embedding success with empty vectors list returns empty results`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig())
        whenever(basePort.findAllByActiveTrue()).thenReturn(emptyList())
        val emptyResult = EmbeddingResult(vectors = emptyList(), dimension = 0, profile = "", model = "")
        whenever(embeddingDispatcher.embed(listOf("hello"))).thenReturn(emptyResult)

        val result = service.retrieveForChat("hello", limit = 8)
        assertTrue(result.isEmpty())
        verify(indexPort, never()).searchByVector(any(), any(), any())
    }

    private fun indexedHit(
        chunkId: Long,
        baseId: Long,
        score: Double,
    ): IndexedKnowledgeChunk =
        IndexedKnowledgeChunk(
            chunkId = chunkId,
            documentId = chunkId,
            knowledgeBaseId = baseId,
            title = "Chunk $chunkId",
            sectionTitle = null,
            content = "content $chunkId",
            sourceType = "MANUAL",
            sourceUri = null,
            embedding = queryVector,
            score = score,
            citation = "KB:document-$chunkId#chunk-0",
        )
}
