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
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgePublicationFallbackCoverageTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val indexPort: KnowledgeIndexPort = mock()
    private val chunker = KnowledgeChunker()
    private val dispatcher: KnowledgeEmbeddingDispatcher = mock()
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val hashing: KnowledgeHashingPort = mock()

    private val executor =
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

    private val candidate =
        KnowledgePublicationCandidate(
            sourceType = KnowledgeSourceType.REVIEW_ANALYSIS,
            externalId = "ext-fallback",
            title = "Review",
            sourceUri = "https://example.com/review",
            content = "content",
            metadata = emptyMap(),
            knowledgeBaseId = null,
        )

    private fun stubPublicationPath() {
        whenever(hashing.sha256Hex(any())).thenReturn("hash")
        whenever(hashing.floatArrayToBytes(any())).thenReturn(ByteArray(4))
        whenever(hashing.norm(any())).thenReturn(0.0)
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(autoIndexReview = true))
        whenever(dispatcher.embed(any())).thenReturn(EmbeddingResult(listOf(floatArrayOf(1f, 2f)), 2, "profile", "model"))
        whenever(sourcePort.findFirstByTypeAndExternalReference(any(), any())).thenReturn(null)
        whenever(sourcePort.save(any())).thenAnswer { it.arguments[0] }
        whenever(documentPort.save(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `an unmanaged base is used when no system managed base exists`() {
        stubPublicationPath()
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(
            listOf(KnowledgeBase(id = 31L, name = "Importierte Ordner", systemManaged = false)),
        )

        executor.publish(candidate)

        verify(documentPort, atLeastOnce()).save(argThat { knowledgeBaseId == 31L })
    }

    @Test
    fun `a new system base is created when the installation has none`() {
        stubPublicationPath()
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any())).thenReturn(
            KnowledgeBase(id = 32L, name = "StackLens-Analyseergebnisse", systemManaged = true),
        )

        executor.publish(candidate)

        verify(documentPort, atLeastOnce()).save(argThat { knowledgeBaseId == 32L })
    }

    @Test
    fun `a failing lucene append still marks the document ready`() {
        whenever(hashing.sha256Hex(any())).thenReturn("hash")
        whenever(hashing.floatArrayToBytes(any())).thenReturn(ByteArray(4))
        whenever(hashing.norm(any())).thenReturn(0.0)
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(chunkSize = 100, chunkOverlap = 0))
        whenever(dispatcher.embed(anyList())).thenReturn(EmbeddingResult(listOf(floatArrayOf(1f, 2f)), 2, "profile", "model"))
        whenever(documentPort.save(any())).thenAnswer { it.arguments[0] }
        doThrow(RuntimeException()).whenever(indexPort).appendChunks(any(), any(), anyList())

        val status =
            KnowledgeDocumentIngester(
                documentPort = documentPort,
                chunkPort = chunkPort,
                configPort = configPort,
                chunker = chunker,
                dispatcher = dispatcher,
                indexPort = indexPort,
                hashing = hashing,
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            ).ingestDocument(
                source = KnowledgeSource(id = 1L, knowledgeBaseId = 2L, name = "src", type = KnowledgeSourceType.MANUAL),
                title = "title",
                plainText = "some content that is long enough to produce a single chunk of text",
                sourceUri = null,
                sourceType = KnowledgeSourceType.MANUAL,
                mimeType = "text/plain",
                metadata = emptyMap(),
            )

        assertEquals(KnowledgeDocumentStatus.READY, status)
        verify(indexPort).appendChunks(eq(2L), eq("g0"), anyList())
    }
}
