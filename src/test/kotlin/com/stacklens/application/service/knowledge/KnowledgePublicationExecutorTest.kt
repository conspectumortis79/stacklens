package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeHashingPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.service.knowledge.KnowledgePublicationCandidate
import com.stacklens.application.service.knowledge.KnowledgePublicationReference
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgePublicationExecutorTest {
    private lateinit var basePort: KnowledgeBasePort
    private lateinit var sourcePort: KnowledgeSourcePort
    private lateinit var documentPort: KnowledgeDocumentPort
    private lateinit var chunkPort: KnowledgeChunkPort
    private lateinit var indexPort: KnowledgeIndexPort
    private lateinit var chunker: KnowledgeChunker
    private lateinit var dispatcher: KnowledgeEmbeddingDispatcher
    private lateinit var configPort: KnowledgeEmbeddingConfigPort
    private lateinit var hashing: KnowledgeHashingPort
    private lateinit var executor: KnowledgePublicationExecutor

    @BeforeEach
    fun setUp() {
        basePort = mock()
        sourcePort = mock()
        documentPort = mock()
        chunkPort = mock()
        indexPort = mock()
        chunker = mock()
        dispatcher = mock()
        configPort = mock()
        hashing = mock()
        executor =
            KnowledgePublicationExecutor(
                basePort = basePort,
                sourcePort = sourcePort,
                documentPort = documentPort,
                chunkPort = chunkPort,
                indexPort = indexPort,
                chunker = chunker,
                dispatcher = dispatcher,
                configPort = configPort,
                hashing = hashing,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
    }

    private val candidate =
        KnowledgePublicationCandidate(
            sourceType = KnowledgeSourceType.REVIEW_ANALYSIS,
            externalId = "ext-1",
            title = "Review",
            sourceUri = "https://example.com/review",
            content = "some content",
            metadata = mapOf("a" to "b"),
            knowledgeBaseId = 3L,
        )

    @Test
    fun `publish returns early when auto index disabled`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(autoIndexReview = false))
        executor.publish(candidate)
        verify(chunkPort, never()).save(any())
        verify(sourcePort, never()).save(any<KnowledgeSource>())
    }

    @Test
    fun `publish returns early when content blank`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig())
        executor.publish(candidate.copy(content = "   "))
        verify(chunkPort, never()).save(any())
    }

    @Test
    fun `publish re-uses existing source and replaces documents`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig())
        val existingDoc = mock<KnowledgeDocument>()
        val existingSource = mock<KnowledgeSource>()
        whenever(sourcePort.findFirstByTypeAndExternalReference(KnowledgeSourceType.REVIEW_ANALYSIS, "ext-1")).thenReturn(existingSource)
        whenever(documentPort.findBySourceIdOrderByIdAsc(0L)).thenReturn(listOf(existingDoc))
        whenever(hashing.sha256Hex(any())).thenReturn("hash")
        whenever(chunker.chunk(any(), anyOrNull(), any(), any())).thenReturn(emptyList())
        whenever(documentPort.save(any<KnowledgeDocument>())).thenReturn(mock())

        executor.publish(candidate)

        verify(chunkPort).deleteByDocumentId(existingDoc.id)
        verify(indexPort).removeDocument(existingDoc.knowledgeBaseId, existingDoc.id)
        verify(documentPort).deleteBySourceId(existingSource.id)
        verify(sourcePort, atLeastOnce()).save(any<KnowledgeSource>())
    }

    @Test
    fun `publish creates source and base when none exist`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig())
        whenever(sourcePort.findFirstByTypeAndExternalReference(any(), any())).thenReturn(null)
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenReturn(KnowledgeBase(id = 9L, name = "n", systemManaged = true))
        whenever(sourcePort.save(any<KnowledgeSource>())).thenReturn(KnowledgeSource(knowledgeBaseId = 9L, name = "Review", type = KnowledgeSourceType.REVIEW_ANALYSIS))
        whenever(documentPort.findBySourceIdOrderByIdAsc(any())).thenReturn(emptyList())
        whenever(hashing.sha256Hex(any())).thenReturn("hash")
        whenever(chunker.chunk(any(), anyOrNull(), any(), any())).thenReturn(emptyList())
        whenever(documentPort.save(any<KnowledgeDocument>())).thenReturn(mock())

        executor.publish(candidate.copy(knowledgeBaseId = null))

        verify(basePort).save(any<KnowledgeBase>())
        verify(sourcePort, atLeastOnce()).save(any<KnowledgeSource>())
    }

    @Test
    fun `publish picks first system managed base`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig())
        val managed = KnowledgeBase(id = 1L, name = "m", systemManaged = true)
        val other = KnowledgeBase(id = 2L, name = "o", systemManaged = false)
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(other, managed))
        whenever(sourcePort.findFirstByTypeAndExternalReference(any(), any())).thenReturn(null)
        whenever(sourcePort.save(any<KnowledgeSource>())).thenReturn(mock())
        whenever(documentPort.findBySourceIdOrderByIdAsc(any())).thenReturn(emptyList())
        whenever(hashing.sha256Hex(any())).thenReturn("hash")
        whenever(chunker.chunk(any(), anyOrNull(), any(), any())).thenReturn(emptyList())
        whenever(documentPort.save(any<KnowledgeDocument>())).thenReturn(mock())

        executor.publish(candidate.copy(knowledgeBaseId = null))

        verify(basePort, never()).save(any<KnowledgeBase>())
    }

    @Test
    fun `publish marks document ready when no chunks`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig())
        val source = mock<KnowledgeSource>()
        whenever(sourcePort.findFirstByTypeAndExternalReference(any(), any())).thenReturn(source)
        whenever(documentPort.findBySourceIdOrderByIdAsc(0L)).thenReturn(emptyList())
        whenever(hashing.sha256Hex(any())).thenReturn("hash")
        whenever(chunker.chunk(any(), anyOrNull(), any(), any())).thenReturn(emptyList())
        val doc = mock<KnowledgeDocument>()
        whenever(documentPort.save(any<KnowledgeDocument>())).thenReturn(doc)

        executor.publish(candidate)

        verify(documentPort).save(doc)
        verify(sourcePort, atLeastOnce()).save(any<KnowledgeSource>())
    }

    private fun seededDocument() =
        com.stacklens.domain.model.knowledge.KnowledgeDocument(
            id = 11L,
            sourceId = 0L,
            knowledgeBaseId = 3L,
            externalId = "ext-1",
            title = "Review",
            sourceUri = "https://example.com/review",
            sourceType = KnowledgeSourceType.REVIEW_ANALYSIS,
            plainText = "some content",
            contentHash = "hash",
        )

    @Test
    fun `publish embeds chunks and appends to index`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig())
        whenever(sourcePort.findFirstByTypeAndExternalReference(any(), any())).thenReturn(mock())
        whenever(documentPort.findBySourceIdOrderByIdAsc(0L)).thenReturn(emptyList())
        whenever(hashing.sha256Hex(any())).thenReturn("hash")
        whenever(hashing.norm(any())).thenReturn(1.0)
        whenever(hashing.floatArrayToBytes(any())).thenReturn(byteArrayOf(1))
        whenever(hashing.bytesToFloatArray(any())).thenReturn(floatArrayOf(1f))
        whenever(chunker.chunk(any(), anyOrNull(), any(), any())).thenReturn(listOf(RawChunk(0, "content one", "sec")))
        whenever(dispatcher.embed(listOf("content one"))).thenReturn(EmbeddingResult(listOf(floatArrayOf(0.1f, 0.2f)), 2, "profile", "model"))
        whenever(documentPort.save(any<KnowledgeDocument>())).thenReturn(seededDocument())

        executor.publish(candidate)

        org.mockito.kotlin
            .verify(chunker)
            .chunk(any(), anyOrNull(), any(), any())
        org.mockito.kotlin
            .verify(dispatcher)
            .embed(org.mockito.kotlin.eq(listOf("content one")))
        verify(chunkPort).save(any())
        verify(indexPort).appendChunks(org.mockito.kotlin.eq(3L), org.mockito.kotlin.eq("g0"), any())
    }

    @Test
    fun `publish swallows index append failures`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig())
        whenever(sourcePort.findFirstByTypeAndExternalReference(any(), any())).thenReturn(mock())
        whenever(documentPort.findBySourceIdOrderByIdAsc(0L)).thenReturn(emptyList())
        whenever(hashing.sha256Hex(any())).thenReturn("hash")
        whenever(hashing.norm(any())).thenReturn(1.0)
        whenever(hashing.floatArrayToBytes(any())).thenReturn(byteArrayOf(1))
        whenever(chunker.chunk(any(), anyOrNull(), any(), any())).thenReturn(listOf(RawChunk(0, "content one", null)))
        whenever(dispatcher.embed(listOf("content one"))).thenReturn(EmbeddingResult(listOf(floatArrayOf(0.1f)), 1, "profile", "model"))
        whenever(documentPort.save(any<KnowledgeDocument>())).thenReturn(seededDocument())
        whenever(indexPort.appendChunks(any(), any(), any())).thenThrow(RuntimeException("index exploded"))

        executor.publish(candidate)

        verify(indexPort).appendChunks(any(), any(), any())
    }

    @Test
    fun `remove returns early when source missing`() {
        whenever(sourcePort.findFirstByTypeAndExternalReference(any(), any())).thenReturn(null)
        executor.remove(KnowledgePublicationReference(KnowledgeSourceType.REVIEW_ANALYSIS, "ext-1"))
        verify(documentPort, never()).deleteBySourceId(any())
    }

    @Test
    fun `remove deletes documents chunks and source`() {
        val source = mock<KnowledgeSource>()
        whenever(sourcePort.findFirstByTypeAndExternalReference(any(), any())).thenReturn(source)
        val doc = mock<KnowledgeDocument>()
        whenever(documentPort.findBySourceIdOrderByIdAsc(0L)).thenReturn(listOf(doc))

        executor.remove(KnowledgePublicationReference(KnowledgeSourceType.REVIEW_ANALYSIS, "ext-1"))

        verify(chunkPort).deleteByDocumentId(doc.id)
        verify(indexPort).removeDocument(doc.knowledgeBaseId, doc.id)
        verify(documentPort).deleteBySourceId(source.id)
        verify(sourcePort).deleteById(source.id)
    }
}
