package com.stacklens.application.service.knowledge

import com.stacklens.adapter.out.knowledge.KnowledgeResetAdapter
import com.stacklens.application.port.out.knowledge.EmbeddingPort
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexGenerationPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeChunkerTest {
    private val chunker = KnowledgeChunker()

    @Test
    fun `chunk returns empty list for blank input`() {
        assert(chunker.chunk("").isEmpty())
        assert(chunker.chunk("   \n\n  ").isEmpty())
    }

    @Test
    fun `chunk splits paragraphs preserving order`() {
        val p1 = "P1: " + (1..30).joinToString(" ") { "alpha$it" }
        val p2 = "P2: " + (1..30).joinToString(" ") { "beta$it" }
        val p3 = "P3: " + (1..30).joinToString(" ") { "gamma$it" }
        val chunks =
            chunker.chunk(
                "$p1\n\n$p2\n\n$p3",
                chunkSize = 200,
                chunkOverlap = 0,
            )
        assert(chunks.size == 3) { "expected 3 chunks, got ${chunks.size}" }
        assert(chunks[0].ordinal == 0)
        assert(chunks[2].ordinal == 2)
        assert(chunks[0].content.startsWith("P1:"))
        assert(chunks[2].content.startsWith("P3:"))
    }

    @Test
    fun `chunk enforces maximum size and creates multiple chunks when exceeded`() {
        val long = (1..50).joinToString(" ") { "Sentence$it." }
        val chunks = chunker.chunk(long, chunkSize = 80, chunkOverlap = 0)
        assert(chunks.size > 1) { "long text should produce multiple chunks" }
    }

    @Test
    fun `chunk overlap prepends tail of previous chunk to subsequent chunks`() {
        val text =
            (1..20).joinToString("\n\n") {
                "Sentence number $it with some filler text to take up space."
            }
        val withOverlap = chunker.chunk(text, chunkSize = 120, chunkOverlap = 30)
        val withoutOverlap = chunker.chunk(text, chunkSize = 120, chunkOverlap = 0)
        assert(withOverlap.size == withoutOverlap.size)
        assert(withOverlap[1].content.length > withoutOverlap[1].content.length) {
            "overlap should make the second chunk longer"
        }
    }
}

class KnowledgeRetrievalServiceTest {
    private val basePort: KnowledgeBasePort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val embeddingDispatcher: KnowledgeEmbeddingDispatcher = mock()
    private val indexPort: com.stacklens.application.port.out.knowledge.KnowledgeIndexPort = mock()
    private val rerankPort: com.stacklens.application.port.out.ai.KnowledgeRerankPort = mock()

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
    fun `retrieveForChat returns empty list when no active bases exist`() {
        whenever(basePort.findAllByActiveTrue()).thenReturn(emptyList())
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig())
        val result = service.retrieveForChat("anything", limit = 8)
        assert(result.isEmpty())
        verify(embeddingDispatcher, never()).embed(any())
    }

    @Test
    fun `retrieveForChat returns empty list when query is blank`() {
        assert(service.retrieveForChat("").isEmpty())
        assert(service.retrieveForChat("   ").isEmpty())
        verify(embeddingDispatcher, never()).embed(any())
    }

    @Test
    fun `retrieveForChat never depends on a raw EmbeddingPort`() {
        val constructors = KnowledgeRetrievalService::class.java.declaredConstructors
        assert(constructors.size == 1) { "expected exactly one constructor" }
        val params = constructors[0].parameterTypes.map { it.simpleName }.toSet()
        assert("EmbeddingPort" !in params) {
            "remote EmbeddingPort dependency leaked into the retrieval service: $params"
        }
        assert("KnowledgeEmbeddingDispatcher" in params) {
            "retrieval service must depend on the local KnowledgeEmbeddingDispatcher: $params"
        }
    }
}

class KnowledgeEmbeddingDispatcherTest {
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val openAi: EmbeddingPort = mock()
    private val openRouter: EmbeddingPort = mock()
    private val ollama: EmbeddingPort = mock()

    private val dispatcher = KnowledgeEmbeddingDispatcher(configPort, listOf(openAi, openRouter, ollama))

    init {
        whenever(openAi.provider()).thenReturn(KnowledgeEmbeddingProvider.OPENAI)
        whenever(openRouter.provider()).thenReturn(KnowledgeEmbeddingProvider.OPENROUTER)
        whenever(ollama.provider()).thenReturn(KnowledgeEmbeddingProvider.OLLAMA)
    }

    @Test
    fun `empty input yields empty result without consulting any adapter`() {
        val result = dispatcher.embed(emptyList())
        assert(result.vectors.isEmpty())
        assert(result.dimension == 0)
    }

    @Test
    fun `dispatcher rejects vectors with NaN values`() {
        whenever(configPort.get()).thenReturn(
            KnowledgeEmbeddingConfig(
                provider = KnowledgeEmbeddingProvider.OPENAI,
                model = "text-embedding-3-small",
                openAiApiKey = "sk-test",
            ),
        )
        whenever(
            openAi.embed(any(), any()),
        ).thenReturn(
            listOf(floatArrayOf(1.0f, Float.NaN, 3.0f)),
        )
        val ex = runCatching { dispatcher.embed(listOf("test")) }.exceptionOrNull()
        assert(ex is IllegalArgumentException) { "expected IllegalArgumentException, got $ex" }
    }

    @Test
    fun `dispatcher rejects dimension mismatches`() {
        whenever(configPort.get()).thenReturn(
            KnowledgeEmbeddingConfig(
                provider = KnowledgeEmbeddingProvider.OPENAI,
                model = "text-embedding-3-small",
                openAiApiKey = "sk-test",
            ),
        )
        whenever(
            openAi.embed(any(), any()),
        ).thenReturn(
            listOf(
                floatArrayOf(1.0f, 2.0f, 3.0f),
                floatArrayOf(4.0f, 5.0f),
            ),
        )
        val ex = runCatching { dispatcher.embed(listOf("a", "b")) }.exceptionOrNull()
        assert(ex is IllegalArgumentException) { "expected IllegalArgumentException, got $ex" }
    }
}

class KnowledgeResetAdapterTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val jobPort: KnowledgeIngestionJobPort = mock()
    private val indexGenerationPort: KnowledgeIndexGenerationPort = mock()
    private val vectorIndexPort: KnowledgeIndexPort = mock()
    private val configPort: KnowledgeEmbeddingConfigPort = mock()

    @Test
    fun `resetAllKnowledgeData wipes every port in correct order and preserves the embedding config`() {
        KnowledgeResetAdapter(
            basePort = basePort,
            sourcePort = sourcePort,
            documentPort = documentPort,
            chunkPort = chunkPort,
            jobPort = jobPort,
            indexGenerationPort = indexGenerationPort,
            vectorIndexPort = vectorIndexPort,
            indexDir = "/tmp/does-not-matter-${System.nanoTime()}",
        ).resetAllKnowledgeData()

        val inOrder =
            inOrder(
                chunkPort,
                documentPort,
                sourcePort,
                jobPort,
                indexGenerationPort,
                basePort,
            )
        inOrder.verify(chunkPort).deleteAll()
        inOrder.verify(documentPort).deleteAll()
        inOrder.verify(sourcePort).deleteAll()
        inOrder.verify(jobPort).deleteAll()
        inOrder.verify(indexGenerationPort).deleteAll()
        inOrder.verify(basePort).deleteAll()
        verify(configPort, never()).delete()
    }

    @Test
    fun `clearKnowledgeBase wipes both DB and Lucene index and leaves config and base row intact`() {
        val base = KnowledgeBase(id = 42L, name = "test")

        KnowledgeResetAdapter(
            basePort = basePort,
            sourcePort = sourcePort,
            documentPort = documentPort,
            chunkPort = chunkPort,
            jobPort = jobPort,
            indexGenerationPort = indexGenerationPort,
            vectorIndexPort = vectorIndexPort,
            indexDir = "/tmp/does-not-matter-${System.nanoTime()}",
        ).clearKnowledgeBase(base)

        verify(chunkPort, times(1)).deleteByKnowledgeBaseId(42)
        verify(documentPort, times(1)).deleteByKnowledgeBaseId(42)
        verify(sourcePort, times(1)).deleteByKnowledgeBaseId(42)
        verify(indexGenerationPort, times(1)).deleteByKnowledgeBaseId(42)
        verify(vectorIndexPort, times(1)).clearKnowledgeBase(42)
        verify(configPort, never()).delete()
        verify(basePort, never()).deleteById(42)
    }
}
