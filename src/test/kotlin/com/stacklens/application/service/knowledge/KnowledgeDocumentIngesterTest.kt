package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.domain.model.knowledge.KnowledgeChunk
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeDocumentIngesterTest {
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val chunker: KnowledgeChunker = KnowledgeChunker()
    private val dispatcher: KnowledgeEmbeddingDispatcher = mock()
    private val indexPort: KnowledgeIndexPort = mock()
    private val sut =
        KnowledgeDocumentIngester(
            documentPort = documentPort,
            chunkPort = chunkPort,
            configPort = configPort,
            chunker = chunker,
            dispatcher = dispatcher,
            indexPort = indexPort,
            hashing =
                com.stacklens.adapter.out.knowledge
                    .DefaultKnowledgeHashing(),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private val source =
        KnowledgeSource(
            id = 1L,
            knowledgeBaseId = 10L,
            name = "src",
            type = KnowledgeSourceType.MANUAL,
            status = KnowledgeSourceStatus.PENDING,
        )

    @Test
    fun `empty chunk list yields READY document without calling the embedding dispatcher`() {
        whenever(documentPort.save(any())).thenAnswer { it.arguments[0] }
        whenever(configPort.get()).thenReturn(
            KnowledgeEmbeddingConfig(chunkSize = 3200, chunkOverlap = 0),
        )

        val status =
            sut.ingestDocument(
                source = source,
                title = "x",
                plainText = "",
                sourceUri = null,
                sourceType = KnowledgeSourceType.MANUAL,
                mimeType = "text/plain",
                metadata = emptyMap(),
            )

        assertThat(status).isEqualTo(KnowledgeDocumentStatus.READY)
        verify(dispatcher, never()).embed(any())
    }

    @Test
    fun `embedding dispatcher failure marks the document FAILED and short-circuits the pipeline`() {
        whenever(documentPort.save(any())).thenAnswer { it.arguments[0] }
        whenever(configPort.get()).thenReturn(
            KnowledgeEmbeddingConfig(chunkSize = 400, chunkOverlap = 50),
        )
        whenever(dispatcher.embed(any())).thenThrow(
            IllegalStateException("Embedding call failed for provider=OLLAMA: Connection refused"),
        )

        val status =
            sut.ingestDocument(
                source = source,
                title = "x",
                plainText = "alpha ".repeat(60),
                sourceUri = null,
                sourceType = KnowledgeSourceType.MANUAL,
                mimeType = "text/plain",
                metadata = emptyMap(),
            )

        assertThat(status).isEqualTo(KnowledgeDocumentStatus.FAILED)
        verify(dispatcher).embed(any())
        verify(chunkPort, never()).save(any())
    }

    @Test
    fun `save dispatches chunks through the index port after embedding`() {
        whenever(documentPort.save(any())).thenAnswer { it.arguments[0] }
        whenever(configPort.get()).thenReturn(
            KnowledgeEmbeddingConfig(chunkSize = 800, chunkOverlap = 0),
        )
        whenever(dispatcher.embed(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val texts = invocation.arguments[0] as List<String>
            EmbeddingResult(
                vectors = texts.map { FloatArray(4) { 0.1f } },
                dimension = 4,
                profile = "test-profile",
                model = "test-model",
            )
        }

        val status =
            sut.ingestDocument(
                source = source,
                title = "title",
                plainText = "lorem ipsum ".repeat(50),
                sourceUri = "https://example.com/x",
                sourceType = KnowledgeSourceType.MANUAL,
                mimeType = "text/plain",
                metadata = mapOf("origin" to "manual"),
            )

        assertThat(status).isEqualTo(KnowledgeDocumentStatus.READY)
        verify(chunkPort).deleteByDocumentId(eq(0L))
        verify(chunkPort, org.mockito.kotlin.atLeastOnce()).save(any<KnowledgeChunk>())
        val indexedCaptor = argumentCaptor<List<IndexedKnowledgeChunk>>()
        verify(indexPort).appendChunks(eq(10L), eq("g0"), indexedCaptor.capture())
        verify(dispatcher).embed(any())
    }

    @Test
    fun `applyOutcome marks source READY when no document failed`() {
        val now = java.time.LocalDateTime.now()
        val s = clone(source)
        sut.applyOutcome(s, now, anyDocFailed = false)
        assertThat(s.status).isEqualTo(KnowledgeSourceStatus.READY)
        assertThat(s.errorMessage).isNull()
        assertThat(s.lastSuccessfulSyncAt).isEqualTo(now)
    }

    @Test
    fun `applyOutcome marks source FAILED and preserves an existing error message`() {
        val now = java.time.LocalDateTime.now()
        val s = clone(source).also { it.errorMessage = "previous root cause" }
        sut.applyOutcome(s, now, anyDocFailed = true)
        assertThat(s.status).isEqualTo(KnowledgeSourceStatus.FAILED)
        assertThat(s.errorMessage).isEqualTo("previous root cause")
    }

    @Test
    fun `applyOutcome fills a default error message when one is missing`() {
        val now = java.time.LocalDateTime.now()
        val s = clone(source)
        sut.applyOutcome(s, now, anyDocFailed = true)
        assertThat(s.status).isEqualTo(KnowledgeSourceStatus.FAILED)
        assertThat(s.errorMessage).isNotNull()
        assertThat(s.errorMessage).contains("Embedding")
    }

    private fun clone(s: KnowledgeSource): KnowledgeSource {
        val copy =
            KnowledgeSource(
                id = s.id,
                knowledgeBaseId = s.knowledgeBaseId,
                name = s.name,
                type = s.type,
                status = s.status,
                externalReference = s.externalReference,
                rootUrl = s.rootUrl,
            )
        copy.errorMessage = s.errorMessage
        return copy
    }
}
