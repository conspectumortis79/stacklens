package com.stacklens.adapter.out.knowledge.embedding

import com.stacklens.application.port.out.knowledge.EmbeddingListingOverrides
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenRouterEmbeddingAdapterModelsTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: OpenRouterEmbeddingAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(
        provider: KnowledgeEmbeddingProvider = KnowledgeEmbeddingProvider.OPENROUTER,
        ollamaUrl: String = "http://localhost:11434",
        openRouterApiKey: String? = null,
    ) = KnowledgeEmbeddingConfig(
        provider = provider,
        model = "openai/text-embedding-3-small",
        ollamaUrl = ollamaUrl,
        openRouterApiKey = openRouterApiKey,
    )

    @Test
    fun `a foreign provider config is answered from the openrouter key it carries`() {
        adapter =
            OpenRouterEmbeddingAdapter(
                httpFactory = ReactiveHttpClientFactory(),
                configuredBaseUrl = "https://openrouter.ai",
            )
        assertTrue(adapter.availableModels(config(provider = KnowledgeEmbeddingProvider.OLLAMA), EmbeddingListingOverrides(null, null)).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `OPENROUTER resolvedBaseUrl returns the canonical public host`() {
        adapter =
            OpenRouterEmbeddingAdapter(
                httpFactory = ReactiveHttpClientFactory(),
                configuredBaseUrl = "https://openrouter.ai",
            )
        assertEquals("https://openrouter.ai", adapter.resolvedBaseUrl(config()))
    }

    @Test
    fun `a listing override replaces the stored api key`() {
        enqueueModelsResponse(
            """
            {
              "data": [
                {"id": "qwen/qwen3-embedding", "architecture": {"input_modalities": ["text"], "output_modalities": ["embeddings"]}}
              ]
            }
            """.trimIndent(),
        )
        adapter = adapterFor(server)

        val result =
            adapter.availableModels(
                config(openRouterApiKey = null),
                EmbeddingListingOverrides(baseUrl = null, apiKey = "sk-or-override"),
            )

        assertEquals(listOf("qwen/qwen3-embedding"), result)
    }

    @Test
    fun `availableModels returns an empty list when no API key is configured`() {
        adapter = adapterFor(server)
        val result = adapter.availableModels(config(openRouterApiKey = null), EmbeddingListingOverrides(null, null))
        assertTrue(result.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `availableModels returns an empty list when API key is blank`() {
        adapter = adapterFor(server)
        val result = adapter.availableModels(config(openRouterApiKey = "   "), EmbeddingListingOverrides(null, null))
        assertTrue(result.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `availableModels returns only models whose architecture includes the embeddings modality`() {
        enqueueModelsResponse(
            """
            {
              "data": [
                {"id": "openai/text-embedding-3-small", "architecture": {"output_modalities": ["embeddings"]}},
                {"id": "openai/text-embedding-3-large", "architecture": {"output_modalities": ["embeddings"]}},
                {"id": "openai/gpt-4.1", "architecture": {"output_modalities": ["text"]}},
                {"id": "no-architecture-at-all"},
                {"id": "cohere/embed-multilingual", "architecture": {"output_modalities": ["embeddings", "text"]}}
              ]
            }
            """.trimIndent(),
        )

        adapter = adapterFor(server)
        val result = adapter.availableModels(config(openRouterApiKey = "sk-or-test"), EmbeddingListingOverrides(null, null))

        assertEquals(
            listOf("cohere/embed-multilingual", "openai/text-embedding-3-large", "openai/text-embedding-3-small"),
            result,
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `availableModels against the real OpenRouter embeddings endpoint shape returns all embedding models`() {
        enqueueModelsResponse(
            """
            {
              "data": [
                {"id": "qwen/qwen3-embedding-8b", "architecture": {"output_modalities": ["embeddings"]}},
                {"id": "qwen/qwen3-embedding-4b", "architecture": {"output_modalities": ["embeddings"]}},
                {"id": "openai/text-embedding-3-small", "architecture": {"output_modalities": ["embeddings"]}},
                {"id": "openai/text-embedding-3-large", "architecture": {"output_modalities": ["embeddings"]}},
                {"id": "openai/text-embedding-ada-002", "architecture": {"output_modalities": ["embeddings"]}},
                {"id": "google/gemini-embedding-001", "architecture": {"output_modalities": ["embeddings"]}},
                {"id": "voyageai/voyage-4-large", "architecture": {"output_modalities": ["embeddings"]}}
              ]
            }
            """.trimIndent(),
        )

        adapter = adapterFor(server)
        val result = adapter.availableModels(config(openRouterApiKey = "sk-or-test"), EmbeddingListingOverrides(null, null))

        assertEquals(7, result.size)
        assertTrue(result.contains("qwen/qwen3-embedding-8b"))
        assertTrue(result.contains("qwen/qwen3-embedding-4b"))
        assertTrue(result.contains("openai/text-embedding-3-small"))
        assertTrue(result.contains("openai/text-embedding-3-large"))
        assertTrue(result.contains("openai/text-embedding-ada-002"))
        assertTrue(result.contains("google/gemini-embedding-001"))
        assertTrue(result.contains("voyageai/voyage-4-large"))
        val recorded = server.takeRequest()
        assertEquals("/api/v1/embeddings/models", recorded.path)
    }

    @Test
    fun `availableModels sends the API key as a Bearer Authorization header`() {
        enqueueModelsResponse("""{"data": []}""")

        adapter = adapterFor(server)
        adapter.availableModels(config(openRouterApiKey = "sk-or-very-secret"), EmbeddingListingOverrides(null, null))

        val recorded = server.takeRequest()
        assertEquals("Bearer sk-or-very-secret", recorded.getHeader("Authorization"))
        assertEquals("/api/v1/embeddings/models", recorded.path)
    }

    @Test
    fun `availableModels returns an empty list when OpenRouter responds with a 401`() {
        server.enqueue(MockResponse().setResponseCode(401))

        adapter = adapterFor(server)
        val result = adapter.availableModels(config(openRouterApiKey = "sk-or-bad"), EmbeddingListingOverrides(null, null))

        assertTrue(result.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `availableModels returns an empty list when OpenRouter responds with a 500`() {
        server.enqueue(MockResponse().setResponseCode(500))

        adapter = adapterFor(server)
        val result = adapter.availableModels(config(openRouterApiKey = "sk-or-test"), EmbeddingListingOverrides(null, null))

        assertTrue(result.isEmpty())
        assertEquals(1, server.requestCount)
    }

    private fun adapterFor(server: MockWebServer): OpenRouterEmbeddingAdapter =
        OpenRouterEmbeddingAdapter(
            httpFactory = ReactiveHttpClientFactory(),
            configuredBaseUrl = server.url("").toString().removeSuffix("/"),
        )

    private fun enqueueModelsResponse(body: String) {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body)
                .setResponseCode(200),
        )
    }
}
