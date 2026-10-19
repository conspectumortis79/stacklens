package com.stacklens.adapter.out.knowledge.embedding

import com.stacklens.application.port.out.knowledge.EmbeddingListingOverrides
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.shared.http.ReactiveHttpClientFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenAiEmbeddingAdapterModelsTest {
    private val adapter = OpenAiEmbeddingAdapter(httpFactory = ReactiveHttpClientFactory())

    private fun config(
        provider: KnowledgeEmbeddingProvider = KnowledgeEmbeddingProvider.OPENAI,
        openAiApiKey: String? = "sk-test",
    ) = KnowledgeEmbeddingConfig(
        provider = provider,
        model = "text-embedding-3-small",
        openAiApiKey = openAiApiKey,
    )

    @Test
    fun `a config naming another provider still answers the openai lineup`() {
        val result = adapter.availableModels(config(provider = KnowledgeEmbeddingProvider.OLLAMA), EmbeddingListingOverrides(null, null))

        assertEquals(3, result.size)
    }

    @Test
    fun `OPENAI returns the three canonical embedding models`() {
        val result = adapter.availableModels(config(), EmbeddingListingOverrides(null, null))
        assertEquals(
            setOf("text-embedding-3-small", "text-embedding-3-large", "text-embedding-ada-002"),
            result.toSet(),
        )
        assertEquals(3, result.size)
    }
}
