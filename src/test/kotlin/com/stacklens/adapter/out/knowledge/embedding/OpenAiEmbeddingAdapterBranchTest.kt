package com.stacklens.adapter.out.knowledge.embedding

import com.stacklens.application.port.out.knowledge.EmbeddingListingOverrides
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.shared.http.ReactiveHttpClientFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiEmbeddingAdapterBranchTest {
    private val adapter =
        OpenAiEmbeddingAdapter(
            httpFactory = ReactiveHttpClientFactory(),
        )

    private fun cfg(
        provider: KnowledgeEmbeddingProvider = KnowledgeEmbeddingProvider.OPENAI,
        apiKey: String? = "openai-key",
    ): KnowledgeEmbeddingConfig =
        KnowledgeEmbeddingConfig(
            provider = provider,
            openAiApiKey = apiKey,
            ollamaUrl = "http://localhost:11434",
            model = "text-embedding-3-small",
        )

    @Test
    fun `provider returns OPENAI`() {
        assertEquals(KnowledgeEmbeddingProvider.OPENAI, adapter.provider())
    }

    @Test
    fun `resolvedBaseUrl returns the OpenAI public API host`() {
        assertEquals("https://api.openai.com", adapter.resolvedBaseUrl(cfg()))
    }

    @Test
    fun `resolvedBaseUrl answers the canonical openai host`() {
        assertEquals(
            "https://api.openai.com",
            adapter.resolvedBaseUrl(cfg()),
        )
    }

    @Test
    fun `embed returns empty list for empty input texts`() {
        assertEquals(emptyList<FloatArray>(), adapter.embed(cfg(), emptyList()))
    }

    @Test
    fun `embed reports the missing api key for a config naming another provider`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter.embed(
                    cfg(provider = KnowledgeEmbeddingProvider.OLLAMA, apiKey = null),
                    listOf("hi"),
                )
            }
        assertTrue(ex.message!!.contains("OpenAI API key"))
    }

    @Test
    fun `embed rejects a missing OpenAI API key`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter.embed(cfg(apiKey = null), listOf("hi"))
            }
        assertTrue(ex.message!!.contains("OpenAI API key"))
    }

    @Test
    fun `embed rejects a blank OpenAI API key`() {
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter.embed(cfg(apiKey = "   "), listOf("hi"))
            }
        assertTrue(ex.message!!.contains("OpenAI API key"))
    }

    @Test
    fun `availableModels returns the canonical OpenAI embedding lineup`() {
        val models = adapter.availableModels(cfg(), EmbeddingListingOverrides(null, null))
        assertEquals(
            listOf(
                "text-embedding-3-small",
                "text-embedding-3-large",
                "text-embedding-ada-002",
            ),
            models,
        )
    }

    @Test
    fun `availableModels answers the lineup for a config that names another provider`() {
        assertEquals(3, adapter.availableModels(cfg(provider = KnowledgeEmbeddingProvider.OLLAMA), EmbeddingListingOverrides(null, null)).size)
    }
}
