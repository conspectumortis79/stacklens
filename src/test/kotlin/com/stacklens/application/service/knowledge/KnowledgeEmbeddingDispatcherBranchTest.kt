package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.EmbeddingListingOverrides
import com.stacklens.application.port.out.knowledge.EmbeddingPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KnowledgeEmbeddingDispatcherBranchTest {
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val adapter: EmbeddingPort = mock()
    private val dispatcher = KnowledgeEmbeddingDispatcher(configPort, listOf(adapter))

    private val config = KnowledgeEmbeddingConfig(provider = KnowledgeEmbeddingProvider.OLLAMA, model = "m", chunkSize = 100, chunkOverlap = 10)

    @Test
    fun `embed returns empty result for empty texts`() {
        assertEquals(EmbeddingResult(emptyList(), 0, "", ""), dispatcher.embed(emptyList()))
    }

    @Test
    fun `embed delegates to adapter and builds profile`() {
        whenever(configPort.get()).thenReturn(config)
        whenever(adapter.provider()).thenReturn(KnowledgeEmbeddingProvider.OLLAMA)
        whenever(adapter.embed(config, listOf("a"))).thenReturn(listOf(floatArrayOf(0.1f, 0.2f)))
        whenever(adapter.resolvedBaseUrl(config)).thenReturn(" http://localhost:11434 ")
        whenever(configPort.get()).thenReturn(config)

        val result = dispatcher.embed(listOf("a"))

        assertEquals(2, result.dimension)
        assertEquals("m", result.model)
        assertEquals("ollama|http://localhost:11434|m|2|chunker-v1|100|10", result.profile)
    }

    @Test
    fun `embed wraps adapter exceptions`() {
        whenever(configPort.get()).thenReturn(config)
        whenever(adapter.provider()).thenReturn(KnowledgeEmbeddingProvider.OLLAMA)
        whenever(adapter.embed(any(), any())).thenThrow(RuntimeException("boom"))

        val e = assertThrows(IllegalStateException::class.java) { dispatcher.embed(listOf("a")) }
        assertEquals(true, e.message!!.contains("Embedding call failed"))
    }

    @Test
    fun `embed rejects wrong vector count`() {
        whenever(configPort.get()).thenReturn(config)
        whenever(adapter.provider()).thenReturn(KnowledgeEmbeddingProvider.OLLAMA)
        whenever(adapter.embed(any(), any())).thenReturn(listOf(floatArrayOf(1f), floatArrayOf(2f)))
        assertThrows(IllegalArgumentException::class.java) { dispatcher.embed(listOf("a")) }
    }

    @Test
    fun `embed rejects zero dimensional vectors`() {
        whenever(configPort.get()).thenReturn(config)
        whenever(adapter.provider()).thenReturn(KnowledgeEmbeddingProvider.OLLAMA)
        whenever(adapter.embed(any(), any())).thenReturn(listOf(floatArrayOf()))
        assertThrows(IllegalArgumentException::class.java) { dispatcher.embed(listOf("a")) }
    }

    @Test
    fun `embed rejects ragged vector dimensions`() {
        whenever(configPort.get()).thenReturn(config)
        whenever(adapter.provider()).thenReturn(KnowledgeEmbeddingProvider.OLLAMA)
        whenever(adapter.embed(any(), any())).thenReturn(listOf(floatArrayOf(1f, 2f), floatArrayOf(1f)))
        val e = assertThrows(IllegalArgumentException::class.java) { dispatcher.embed(listOf("a", "b")) }
        assertEquals(true, e.message!!.contains("expected 2"))
    }

    @Test
    fun `embed rejects non finite values`() {
        whenever(configPort.get()).thenReturn(config)
        whenever(adapter.provider()).thenReturn(KnowledgeEmbeddingProvider.OLLAMA)
        whenever(adapter.embed(any(), any())).thenReturn(listOf(floatArrayOf(Float.NaN)))
        assertThrows(IllegalArgumentException::class.java) { dispatcher.embed(listOf("a")) }
        whenever(adapter.embed(any(), any())).thenReturn(listOf(floatArrayOf(Float.POSITIVE_INFINITY)))
        assertThrows(IllegalArgumentException::class.java) { dispatcher.embed(listOf("a")) }
    }

    @Test
    fun `availableModels returns empty when provider not registered`() {
        whenever(configPort.get()).thenReturn(config)
        whenever(adapter.provider()).thenReturn(KnowledgeEmbeddingProvider.OLLAMA)
        assertEquals(emptyList<String>(), dispatcher.availableModels(provider = KnowledgeEmbeddingProvider.OPENROUTER))
    }

    @Test
    fun `availableModels builds effective config from overrides`() {
        whenever(configPort.get()).thenReturn(config)
        whenever(adapter.provider()).thenReturn(KnowledgeEmbeddingProvider.OLLAMA)
        whenever(adapter.availableModels(any(), any())).thenReturn(listOf("model-a"))

        val result = dispatcher.availableModels(provider = KnowledgeEmbeddingProvider.OLLAMA, ollamaUrl = "http://x", openRouterApiKey = "or")

        assertEquals(listOf("model-a"), result)
    }

    @Test
    fun `probe returns embedding dimension`() {
        whenever(configPort.get()).thenReturn(config)
        whenever(adapter.provider()).thenReturn(KnowledgeEmbeddingProvider.OLLAMA)
        whenever(adapter.resolvedBaseUrl(any())).thenReturn("http://localhost:11434")
        whenever(adapter.embed(any(), any())).thenReturn(listOf(floatArrayOf(1f, 2f, 3f)))
        assertEquals(3, dispatcher.probe())
    }

    @Test
    fun `resolveAdapter errors on unknown provider`() {
        val only = mock<EmbeddingPort>()
        whenever(only.provider()).thenReturn(KnowledgeEmbeddingProvider.OLLAMA)
        val single = KnowledgeEmbeddingDispatcher(configPort, listOf(only))
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(provider = KnowledgeEmbeddingProvider.OPENROUTER))
        val e =
            assertThrows(IllegalStateException::class.java) {
                single.embed(listOf("a"))
            }
        assertEquals(true, e.message!!.contains("No EmbeddingPort registered"))
    }

    @Test
    fun `availableModels without overrides forwards empty listing overrides`() {
        whenever(configPort.get()).thenReturn(KnowledgeEmbeddingConfig(provider = KnowledgeEmbeddingProvider.OLLAMA, model = "base", ollamaUrl = "u1", openRouterApiKey = "b"))
        whenever(adapter.provider()).thenReturn(KnowledgeEmbeddingProvider.OLLAMA)
        whenever(adapter.availableModels(any(), any())).thenReturn(listOf("x"))
        dispatcher.availableModels()
        org.mockito.kotlin
            .verify(adapter)
            .availableModels(org.mockito.kotlin.any(), eq(EmbeddingListingOverrides(null, null)))
    }
}
