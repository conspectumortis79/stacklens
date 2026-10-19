package com.stacklens.application.service.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeReindexUseCase
import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeChunk
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class KnowledgeReindexServiceFullTest {
    private val basePort: KnowledgeBasePort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val indexPort: KnowledgeIndexPort = mock()

    private val service =
        KnowledgeReindexService(
            basePort = basePort,
            chunkPort = chunkPort,
            documentPort = documentPort,
            indexPort = indexPort,
            hashing =
                com.stacklens.adapter.out.knowledge
                    .DefaultKnowledgeHashing(),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private val now: LocalDateTime = LocalDateTime.of(2024, 1, 1, 0, 0)

    private fun base(
        id: Long = 1L,
        name: String = "Base",
    ): KnowledgeBase =
        KnowledgeBase(
            id = id,
            stableId = "kb-stable-$id",
            name = name,
            description = "desc",
            createdAt = now,
            updatedAt = now,
        )

    private fun doc(
        id: Long,
        sourceType: KnowledgeSourceType = KnowledgeSourceType.MANUAL,
    ): KnowledgeDocument =
        KnowledgeDocument(
            id = id,
            stableId = "doc-stable-$id",
            sourceId = 11L,
            knowledgeBaseId = 1L,
            externalId = null,
            title = "Title $id",
            sourceUri = null,
            sourceType = sourceType,
            mimeType = "text/plain",
            plainText = "body",
            contentHash = "h",
            metadataJson = null,
            status = com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus.READY,
            createdAt = now,
            updatedAt = now,
            indexedAt = now,
        )

    private fun chunk(
        id: Long,
        documentId: Long,
        ordinal: Int = 0,
        content: String = "hello",
        embeddingBytes: ByteArray? = null,
    ): KnowledgeChunk =
        KnowledgeChunk(
            id = id,
            stableId = "ch-stable-$id",
            documentId = documentId,
            knowledgeBaseId = 1L,
            ordinal = ordinal,
            sectionTitle = null,
            content = content,
            contentHash = "h",
            embeddingProfile = null,
            embeddingDimension = 2,
            embeddingBlob = embeddingBytes,
            tokenEstimate = 1,
        )

    @BeforeEach
    fun resetState() {
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
    }

    @Test
    fun `reindexBase returns null when the base does not exist`() {
        whenever(basePort.findById(99L)).thenReturn(null)
        assertNull(service.reindexBase(99L))
    }

    @Test
    fun `reindexBase emits a zero-count result when the base has no chunks`() {
        whenever(basePort.findById(1L)).thenReturn(base())
        whenever(chunkPort.findByKnowledgeBaseId(1L)).thenReturn(emptyList())
        val result = service.reindexBase(1L)
        assertNotNull(result)
        assertEquals(1L, result!!.knowledgeBaseId)
        assertEquals(0, result.chunkCount)
        verify(indexPort, org.mockito.kotlin.never())
            .replaceAllChunks(any(), any(), any())
    }

    @Test
    fun `reindexBase forwards each chunk with the document title into the index`() {
        whenever(basePort.findById(1L)).thenReturn(base())
        val d1 = doc(21L)
        val d2 = doc(22L)
        whenever(chunkPort.findByKnowledgeBaseId(1L)).thenReturn(
            listOf(
                chunk(id = 31L, documentId = 21L, ordinal = 0, content = "first"),
                chunk(id = 32L, documentId = 21L, ordinal = 1, content = "second"),
                chunk(id = 33L, documentId = 22L, ordinal = 0, content = "third"),
            ),
        )
        whenever(documentPort.findById(21L)).thenReturn(d1)
        whenever(documentPort.findById(22L)).thenReturn(d2)
        val result = service.reindexBase(1L)
        assertEquals(3, result!!.chunkCount)
        verify(indexPort).replaceAllChunks(
            eq(1L),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.argThat<List<IndexedKnowledgeChunk>> { chunks ->
                val byDoc = chunks.groupBy { it.documentId }
                byDoc[21L]?.size == 2 &&
                    byDoc[22L]?.size == 1 &&
                    byDoc[21L]?.first()?.content == "first" &&
                    byDoc[21L]?.last()?.content == "second"
            },
        )
    }

    @Test
    fun `reindexBase skips chunks whose documents can no longer be loaded`() {
        whenever(basePort.findById(1L)).thenReturn(base())
        whenever(chunkPort.findByKnowledgeBaseId(1L)).thenReturn(
            listOf(
                chunk(id = 31L, documentId = 21L, ordinal = 0, content = "kept"),
                chunk(id = 32L, documentId = 999L, ordinal = 0, content = "orphaned"),
                chunk(id = 33L, documentId = 22L, ordinal = 0, content = "kept-2"),
            ),
        )
        whenever(documentPort.findById(21L)).thenReturn(doc(21L))
        whenever(documentPort.findById(22L)).thenReturn(doc(22L))
        whenever(documentPort.findById(999L)).thenReturn(null)

        val result = service.reindexBase(1L)

        assertEquals(2, result!!.chunkCount)
        verify(indexPort).replaceAllChunks(
            eq(1L),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.argThat<List<IndexedKnowledgeChunk>> { chunks ->
                chunks.none { it.chunkId == 32L } &&
                    chunks.any { it.chunkId == 31L } &&
                    chunks.any { it.chunkId == 33L }
            },
        )
    }

    @Test
    fun `reindexBase decodes the embeddingBlob into a float array when present`() {
        whenever(basePort.findById(1L)).thenReturn(base())
        val d = doc(21L)
        whenever(chunkPort.findByKnowledgeBaseId(1L)).thenReturn(
            listOf(
                chunk(id = 31L, documentId = 21L, content = "x", embeddingBytes = byteArrayOf(0.toByte(), 0, -128, 63, 0, 0, 0, 64)),
            ),
        )
        whenever(documentPort.findById(21L)).thenReturn(d)
        service.reindexBase(1L)
        verify(indexPort).replaceAllChunks(
            eq(1L),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.argThat<List<IndexedKnowledgeChunk>> { chunks ->
                val emb = chunks.first().embedding
                emb != null && emb.size == 2 && emb[0] == 1.0f && emb[1] == 2.0f
            },
        )
    }

    @Test
    fun `reindexAll iterates every base and emits a result for each`() {
        val bases = listOf(base(1L, "alpha"), base(2L, "beta"))
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(bases)
        whenever(chunkPort.findByKnowledgeBaseId(1L)).thenReturn(emptyList())
        val d = doc(99L)
        whenever(chunkPort.findByKnowledgeBaseId(2L)).thenReturn(
            listOf(chunk(id = 1L, documentId = 99L, ordinal = 0)),
        )
        whenever(documentPort.findById(99L)).thenReturn(d)
        val results = service.reindexAll()
        assertEquals(2, results.size)
        assertEquals(1L, results[0].knowledgeBaseId)
        assertEquals(0, results[0].chunkCount)
        assertEquals(2L, results[1].knowledgeBaseId)
        assertEquals(1, results[1].chunkCount)
    }

    @Test
    fun `reindexAll returns an empty list when there are no bases at all`() {
        assertEquals(emptyList<KnowledgeReindexUseCase.ReindexResult>(), service.reindexAll())
    }

    @Test
    fun `LocalDateTime-toInstant extension gives the same UTC instant as systemDefault`() {
        val ldt = LocalDateTime.of(2024, 5, 1, 12, 0)
        val expected = ldt.atZone(java.time.ZoneId.systemDefault()).toInstant()
        val actual = ldt.toInstant()
        assertEquals(expected, actual)
    }
}
