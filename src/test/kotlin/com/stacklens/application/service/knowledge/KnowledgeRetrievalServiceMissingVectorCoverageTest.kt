package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.ai.KnowledgeRerankPort
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeRetrievalServiceMissingVectorCoverageTest {
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

    @Test
    fun `an embedding answer without any vector skips the vector search`() {
        whenever(configPort.get())
            .thenReturn(
                KnowledgeEmbeddingConfig(
                    enabled = true,
                    provider = KnowledgeEmbeddingProvider.OLLAMA,
                    model = "test-model",
                ),
            )
        whenever(basePort.findAllByActiveTrue())
            .thenReturn(listOf(KnowledgeBase(id = 1L, name = "B1", includeInGlobalChat = true, systemManaged = false)))
        whenever(embeddingDispatcher.embed(listOf("hello")))
            .thenReturn(EmbeddingResult(vectors = emptyList(), dimension = 0, profile = "p", model = "m"))

        val results = service.retrieveForChat("hello", limit = 5)

        assertTrue(results.isEmpty())
        verify(indexPort, never()).searchByVector(any(), any(), any())
    }
}
