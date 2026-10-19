package com.stacklens.adapter.out.knowledge.embedding

import com.stacklens.application.port.out.knowledge.EmbeddingListingOverrides
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterEmbeddingAdapterBranchTest {
    private val adapter =
        OpenRouterEmbeddingAdapter(
            httpFactory =
                com.stacklens.shared.http
                    .ReactiveHttpClientFactory(),
            configuredBaseUrl = "https://openrouter.ai",
        )

    private fun cfg(
        provider: KnowledgeEmbeddingProvider = KnowledgeEmbeddingProvider.OPENROUTER,
        apiKey: String? = "openrouter-key",
    ): KnowledgeEmbeddingConfig =
        KnowledgeEmbeddingConfig(
            provider = provider,
            openAiApiKey = null,
            openRouterApiKey = apiKey,
            ollamaUrl = "http://localhost:11434",
            model = "text-embedding-3-small",
        )

    @Test
    fun `provider returns OPENROUTER`() {
        assertEquals(KnowledgeEmbeddingProvider.OPENROUTER, adapter.provider())
    }

    @Test
    fun `resolvedBaseUrl returns the canonical OpenRouter public host`() {
        assertEquals("https://openrouter.ai", adapter.resolvedBaseUrl(cfg()))
    }

    @Test
    fun `embed returns an empty list when texts is empty`() {
        assertEquals(emptyList<FloatArray>(), adapter.embed(cfg(), emptyList()))
    }

    @Test
    fun `embed rejects a missing OpenRouter API key`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter.embed(cfg(apiKey = null), listOf("hello"))
            }
        assertTrue(ex.message!!.contains("API key"))
    }

    @Test
    fun `embed rejects a blank OpenRouter API key`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter.embed(cfg(apiKey = "   "), listOf("hello"))
            }
        assertTrue(ex.message!!.contains("API key"))
    }

    @Test
    fun `availableModels reads the openrouter key of a config that names another provider`() {
        assertTrue(
            adapter
                .availableModels(
                    cfg(provider = KnowledgeEmbeddingProvider.OLLAMA, apiKey = null),
                    EmbeddingListingOverrides(null, null),
                ).isEmpty(),
        )
    }
}
