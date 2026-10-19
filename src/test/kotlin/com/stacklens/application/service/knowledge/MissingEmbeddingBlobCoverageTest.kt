package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeHashingPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.service.knowledge.KnowledgePublicationCandidate
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class MissingEmbeddingBlobCoverageTest {
    private val hashing = mock<KnowledgeHashingPort>()

    init {
        whenever(hashing.sha256Hex(any())).thenReturn("hash")
        whenever(hashing.norm(any())).thenReturn(0.0)
        Mockito.doReturn(null).`when`(hashing).floatArrayToBytes(any())
    }

    private fun document(id: Long) =
        KnowledgeDocument(
            id = id,
            stableId = "doc-$id",
            knowledgeBaseId = 3L,
            sourceId = 4L,
            sourceType = KnowledgeSourceType.MANUAL,
            title = "T",
            sourceUri = null,
            externalId = null,
            plainText = "content",
            contentHash = "hash",
            metadataJson = null,
            status = KnowledgeDocumentStatus.PENDING,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            indexedAt = null,
        )

    @Test
    fun `the ingester indexes a chunk without an embedding blob`() {
        val documentPort = mock<KnowledgeDocumentPort>()
        val chunkPort = mock<KnowledgeChunkPort>()
        val configPort = mock<KnowledgeEmbeddingConfigPort>()
        val indexPort = mock<KnowledgeIndexPort>()
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(chunkSize = 4000, chunkOverlap = 0))
        whenever(documentPort.save(any())).thenAnswer { it.arguments[0] }
        whenever(chunkPort.save(any())).thenAnswer { it.arguments[0] }

        val ingester =
            KnowledgeDocumentIngester(
                documentPort = documentPort,
                chunkPort = chunkPort,
                configPort = configPort,
                chunker = KnowledgeChunker(),
                dispatcher =
                    mock<KnowledgeEmbeddingDispatcher>().apply {
                        whenever(embed(any())).thenReturn(EmbeddingResult(listOf(floatArrayOf(1f)), 1, "p", "m"))
                    },
                indexPort = indexPort,
                hashing = hashing,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )

        ingester.ingestDocument(
            source = KnowledgeSource(id = 4L, knowledgeBaseId = 3L, name = "s", type = KnowledgeSourceType.MANUAL),
            title = "T",
            plainText = "content",
            sourceUri = null,
            sourceType = KnowledgeSourceType.MANUAL,
            mimeType = null,
            metadata = emptyMap(),
        )
    }

    @Test
    fun `the publication executor indexes a chunk without an embedding blob`() {
        val basePort = mock<KnowledgeBasePort>()
        val sourcePort = mock<KnowledgeSourcePort>()
        val documentPort = mock<KnowledgeDocumentPort>()
        val chunkPort = mock<KnowledgeChunkPort>()
        val indexPort = mock<KnowledgeIndexPort>()
        val configPort = mock<KnowledgeEmbeddingConfigPort>()
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(autoIndexReview = true))
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(KnowledgeBase(id = 3L, name = "KB")))
        whenever(sourcePort.save(any())).thenAnswer { it.arguments[0] }
        whenever(documentPort.save(any())).thenAnswer { it.arguments[0] }
        whenever(chunkPort.save(any())).thenAnswer { it.arguments[0] }

        KnowledgePublicationExecutor(
            basePort = basePort,
            sourcePort = sourcePort,
            documentPort = documentPort,
            chunkPort = chunkPort,
            indexPort = indexPort,
            chunker = KnowledgeChunker(),
            dispatcher =
                mock<KnowledgeEmbeddingDispatcher>().apply {
                    whenever(embed(any())).thenReturn(EmbeddingResult(listOf(floatArrayOf(1f)), 1, "p", "m"))
                },
            configPort = configPort,
            hashing = hashing,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        ).publish(
            KnowledgePublicationCandidate(
                sourceType = KnowledgeSourceType.REVIEW_ANALYSIS,
                externalId = "ext",
                title = "T",
                sourceUri = null,
                content = "content",
                metadata = emptyMap(),
                knowledgeBaseId = 3L,
            ),
        )
    }
}
