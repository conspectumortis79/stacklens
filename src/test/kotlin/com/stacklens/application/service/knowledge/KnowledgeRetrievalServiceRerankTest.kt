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
import com.stacklens.domain.model.knowledge.KnowledgeRerankConfig
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeRetrievalServiceRerankTest {
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
        whenever(embeddingDispatcher.embed(any())).thenReturn(
            EmbeddingResult(
                vectors = listOf(queryVector),
                dimension = queryVector.size,
                profile = "test|test|test|4|chunker-v1|1600|200",
                model = "test-model",
            ),
        )
    }

    @Test
    fun `query rewriting disabled → port is never called for rewrite`() {
        stubQueryEmbedding()
        val cfg = configWith(KnowledgeRerankConfig(queryRewritingEnabled = false))
        whenever(configPort.get()).thenReturn(cfg)
        whenever(basePort.findAllByActiveTrue()).thenReturn(listOf(activeBase(1L)))
        whenever(indexPort.searchByVector(eq(1L), any(), any())).thenReturn(emptyList())

        service.retrieveForChat("Warum crasht der MCP-Server?")

        verify(rerankPort, never()).rewriteQueryForSearch(any(), any())
    }

    @Test
    fun `query rewriting enabled and port returns a rewrite → embedding runs on the rewrite`() {
        stubQueryEmbedding()
        val cfg = configWith(KnowledgeRerankConfig(queryRewritingEnabled = true))
        whenever(configPort.get()).thenReturn(cfg)
        whenever(basePort.findAllByActiveTrue()).thenReturn(listOf(activeBase(1L)))
        whenever(indexPort.searchByVector(eq(1L), any(), any())).thenReturn(emptyList())
        whenever(rerankPort.rewriteQueryForSearch(any(), anyOrNull())).thenReturn("MCP crash exception")

        service.retrieveForChat("Warum crasht der MCP-Server?")

        verify(embeddingDispatcher).embed(listOf("MCP crash exception"))
        verify(indexPort).searchByVector(eq(1L), eq(queryVector), eq(cfg.topK))
    }

    @Test
    fun `query rewriting enabled but port returns null → original question is embedded`() {
        stubQueryEmbedding()
        val cfg = configWith(KnowledgeRerankConfig(queryRewritingEnabled = true))
        whenever(configPort.get()).thenReturn(cfg)
        whenever(basePort.findAllByActiveTrue()).thenReturn(listOf(activeBase(1L)))
        whenever(indexPort.searchByVector(eq(1L), any(), any())).thenReturn(emptyList())
        whenever(rerankPort.rewriteQueryForSearch(any(), anyOrNull())).thenReturn(null)

        service.retrieveForChat("Original-Frage")

        verify(embeddingDispatcher).embed(listOf("Original-Frage"))
        verify(indexPort).searchByVector(eq(1L), eq(queryVector), eq(cfg.topK))
    }

    @Test
    fun `rerank disabled → port is never called`() {
        stubQueryEmbedding()
        val cfg = configWith(KnowledgeRerankConfig(rerankEnabled = false))
        whenever(configPort.get()).thenReturn(cfg)
        val hits = (0L until 3L).map { indexedHit(it) }
        whenever(basePort.findAllByActiveTrue()).thenReturn(listOf(activeBase(1L)))
        whenever(indexPort.searchByVector(eq(1L), any(), any())).thenReturn(hits)

        val out = service.retrieveForChat("Frage")

        verify(rerankPort, never()).rerankChunks(any(), any(), any(), any())
        assertEquals(3, out.size)
        assertEquals(listOf(0L, 1L, 2L), out.map { it.chunkId })
    }

    @Test
    fun `rerank enabled and port returns null → topK kept in vector order`() {
        stubQueryEmbedding()
        val cfg =
            configWith(
                KnowledgeRerankConfig(
                    rerankEnabled = true,
                    rerankCandidateCount = 20,
                    rerankFinalCount = 5,
                ),
            )
        whenever(configPort.get()).thenReturn(cfg)
        val hits = (0L until 8L).map { indexedHit(it) }
        whenever(basePort.findAllByActiveTrue()).thenReturn(listOf(activeBase(1L)))
        whenever(indexPort.searchByVector(eq(1L), any(), eq(20))).thenReturn(hits)
        whenever(rerankPort.rerankChunks(any(), any(), any(), any())).thenReturn(null)

        val out = service.retrieveForChat("Frage")

        verify(rerankPort).rerankChunks(eq("Frage"), any(), eq(5), any())
        assertEquals(5, out.size)
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L), out.map { it.chunkId })
    }

    @Test
    fun `rerank enabled and port returns a list → that list used verbatim`() {
        stubQueryEmbedding()
        val cfg =
            configWith(
                KnowledgeRerankConfig(
                    rerankEnabled = true,
                    rerankCandidateCount = 20,
                    rerankFinalCount = 5,
                ),
            )
        whenever(configPort.get()).thenReturn(cfg)
        val luceneHits = (0L until 8L).map { indexedHit(it) }
        val reranked =
            listOf(
                retrieved(7L, score = 0.99),
                retrieved(2L, score = 0.91),
                retrieved(5L, score = 0.83),
            )
        whenever(basePort.findAllByActiveTrue()).thenReturn(listOf(activeBase(1L)))
        whenever(indexPort.searchByVector(eq(1L), any(), eq(20))).thenReturn(luceneHits)
        whenever(rerankPort.rerankChunks(any(), any(), eq(5), any())).thenReturn(reranked)

        val out = service.retrieveForChat("Frage")

        assertEquals(3, out.size)
        assertEquals(listOf(7L, 2L, 5L), out.map { it.chunkId })
    }

    @Test
    fun `rerank enabled but finalCount is bigger than topK → cap at topK`() {
        stubQueryEmbedding()
        val cfg =
            KnowledgeEmbeddingConfig(
                topK = 3,
                postRetrieval =
                    KnowledgeRerankConfig(
                        rerankEnabled = true,
                        rerankCandidateCount = 20,
                        rerankFinalCount = 10,
                    ),
            )
        whenever(configPort.get()).thenReturn(cfg)
        val luceneHits = (0L until 20L).map { indexedHit(it) }
        whenever(basePort.findAllByActiveTrue()).thenReturn(listOf(activeBase(1L)))
        whenever(indexPort.searchByVector(eq(1L), any(), any())).thenReturn(luceneHits)
        whenever(rerankPort.rerankChunks(any(), any(), eq(3), any())).thenReturn(null)

        val out = service.retrieveForChat("Frage")

        verify(rerankPort).rerankChunks(any(), any(), eq(3), any())
        assertEquals(3, out.size)
    }

    @Test
    fun `rerank never called when KNN returns empty`() {
        stubQueryEmbedding()
        val cfg = configWith(KnowledgeRerankConfig(rerankEnabled = true))
        whenever(configPort.get()).thenReturn(cfg)
        whenever(basePort.findAllByActiveTrue()).thenReturn(listOf(activeBase(1L)))
        whenever(indexPort.searchByVector(any(), any(), any())).thenReturn(emptyList())

        val out = service.retrieveForChat("Frage")

        assertEquals(emptyList<com.stacklens.domain.model.knowledge.RetrievedKnowledge>(), out)
        verify(rerankPort, never()).rerankChunks(any(), any(), any(), any())
    }

    private fun configWith(postCfg: KnowledgeRerankConfig): KnowledgeEmbeddingConfig =
        KnowledgeEmbeddingConfig(
            topK = 5,
            postRetrieval = postCfg,
        )

    private fun activeBase(id: Long): KnowledgeBase =
        KnowledgeBase(
            id = id,
            name = "Base $id",
            description = "",
            active = true,
            includeInGlobalChat = true,
        )

    private fun indexedHit(chunkId: Long): IndexedKnowledgeChunk =
        IndexedKnowledgeChunk(
            chunkId = chunkId,
            documentId = chunkId,
            knowledgeBaseId = 1L,
            title = "Chunk $chunkId",
            sectionTitle = null,
            content = "content $chunkId",
            sourceType = "MANUAL",
            sourceUri = null,
            embedding = queryVector,
            score = 1.0 - chunkId * 0.01,
            citation = "KB:document-$chunkId#chunk-0",
        )

    private fun retrieved(
        chunkId: Long,
        score: Double = 0.5,
    ): com.stacklens.domain.model.knowledge.RetrievedKnowledge =
        com.stacklens.domain.model.knowledge.RetrievedKnowledge(
            chunkId = chunkId,
            documentId = chunkId,
            knowledgeBaseId = 1L,
            title = "Chunk $chunkId",
            sourceType = KnowledgeSourceType.MANUAL,
            sourceUri = null,
            content = "content $chunkId",
            score = score,
            citation = "KB:document-$chunkId#chunk-0",
        )
}
