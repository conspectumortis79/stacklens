package com.stacklens.adapter.out.knowledge

import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LuceneKnowledgeIndexAdapterKnnTest {
    private val adapter =
        LuceneKnowledgeIndexAdapter(
            hashing =
                com.stacklens.adapter.out.knowledge
                    .DefaultKnowledgeHashing(),
            indexDir = "/tmp/stacklens-knn-test-${System.nanoTime()}",
        )

    @Test
    fun `searchByVector returns chunks ordered by cosine similarity`() {
        val baseId = 2001L
        val chunks =
            listOf(
                sampleChunk(baseId, 0L, embedding = floatArrayOf(0.9f, 0.1f, 0.1f, 0.1f)),
                sampleChunk(baseId, 1L, embedding = floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f)),
                sampleChunk(baseId, 2L, embedding = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f)),
            )
        adapter.replaceAllChunks(baseId, "g0", chunks)

        val hits =
            adapter.searchByVector(
                baseId,
                queryVector = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f),
                limit = 3,
            )

        assertEquals(3, hits.size)
        assertEquals(listOf(2L, 0L, 1L), hits.map { it.chunkId })
        val scores = hits.map { it.score }
        assertTrue(scores[0] >= scores[1]) { "score[0] >= score[1], got $scores" }
        assertTrue(scores[1] >= scores[2]) { "score[1] >= score[2], got $scores" }
        assertTrue(hits[1].score > hits[2].score) { "aligned > orthogonal, got ${hits.map { it.score }}" }
    }

    @Test
    fun `searchByVector propagates a query vector dimension mismatch`() {
        val baseId = 2002L
        adapter.replaceAllChunks(
            baseId,
            "g0",
            listOf(sampleChunk(baseId, 0L, embedding = floatArrayOf(1f, 2f, 3f, 4f))),
        )

        assertThrows(IllegalStateException::class.java) {
            adapter.searchByVector(
                baseId,
                queryVector = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f),
                limit = 5,
            )
        }
    }

    @Test
    fun `searchByVector skips chunks without an embedding`() {
        val baseId = 2003L
        adapter.replaceAllChunks(
            baseId,
            "g0",
            listOf(
                sampleChunk(
                    baseId = baseId,
                    chunkId = 0L,
                    content = "alpha bravo charlie",
                    embedding = null,
                ),
                sampleChunk(
                    baseId = baseId,
                    chunkId = 1L,
                    content = "delta echo foxtrot",
                    embedding = floatArrayOf(0.7f, 0.7f, 0.0f, 0.0f),
                ),
            ),
        )

        val hits =
            adapter.searchByVector(
                baseId,
                queryVector = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f),
                limit = 5,
            )

        assertEquals(1, hits.size)
        assertEquals(1L, hits.first().chunkId, "chunk without embedding must be skipped")
    }

    @Test
    fun `searchByVector returns empty when the base has no active index`() {
        val hits =
            adapter.searchByVector(
                knowledgeBaseId = 9999L,
                queryVector = floatArrayOf(1.0f, 0.0f),
                limit = 5,
            )
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `searchByVector returns empty for empty or non-positive inputs`() {
        val baseId = 2004L
        adapter.replaceAllChunks(
            baseId,
            "g0",
            listOf(sampleChunk(baseId, 0L, embedding = floatArrayOf(1f, 0f))),
        )
        assertTrue(adapter.searchByVector(baseId, FloatArray(0), limit = 5).isEmpty())
        assertTrue(adapter.searchByVector(baseId, floatArrayOf(1f, 0f), limit = 0).isEmpty())
        assertTrue(adapter.searchByVector(baseId, floatArrayOf(1f, 0f), limit = -1).isEmpty())
    }

    @Test
    fun `chunks marked inactive via the active flag are not returned`() {
        val baseId = 2005L
        adapter.replaceAllChunks(
            baseId,
            "g0",
            listOf(sampleChunk(baseId, 0L, embedding = floatArrayOf(1.0f, 0.0f))),
        )
        adapter.replaceAllChunks(baseId, "g0", emptyList())

        val hits =
            adapter.searchByVector(
                baseId,
                queryVector = floatArrayOf(1.0f, 0.0f),
                limit = 5,
            )
        assertTrue(hits.isEmpty(), "empty index must yield no hits")
    }

    @Test
    fun `appendChunks preserves previously indexed chunks`() {
        val baseId = 2006L
        adapter.appendChunks(
            baseId,
            "g0",
            listOf(
                sampleChunk(baseId, 0L, embedding = floatArrayOf(1.0f, 0.0f)),
            ),
        )
        adapter.appendChunks(
            baseId,
            "g0",
            listOf(
                sampleChunk(baseId, 1L, embedding = floatArrayOf(0.0f, 1.0f)),
            ),
        )

        val hits =
            adapter.searchByVector(
                baseId,
                queryVector = floatArrayOf(1.0f, 0.0f),
                limit = 5,
            )
        assertEquals(2, hits.size, "second appendChunks must NOT erase the first")
        assertEquals(setOf(0L, 1L), hits.map { it.chunkId }.toSet())
    }

    @Test
    fun `appendChunks on an empty index creates the index`() {
        val baseId = 2007L
        adapter.appendChunks(
            baseId,
            "g0",
            listOf(sampleChunk(baseId, 0L, embedding = floatArrayOf(1.0f, 0.0f))),
        )
        val hits =
            adapter.searchByVector(
                baseId,
                queryVector = floatArrayOf(1.0f, 0.0f),
                limit = 5,
            )
        assertEquals(1, hits.size)
        assertEquals(0L, hits.first().chunkId)
    }

    private fun sampleChunk(
        baseId: Long,
        chunkId: Long,
        content: String = "sample content for chunk $chunkId",
        embedding: FloatArray? = null,
    ): IndexedKnowledgeChunk =
        IndexedKnowledgeChunk(
            chunkId = chunkId,
            documentId = chunkId,
            knowledgeBaseId = baseId,
            title = "Title $chunkId",
            sectionTitle = null,
            content = content,
            sourceType = "MANUAL",
            sourceUri = null,
            embedding = embedding,
            score = 0.0,
            citation = "KB:document-$chunkId#chunk-0",
        )
}
