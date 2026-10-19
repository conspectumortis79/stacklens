package com.stacklens.adapter.out.knowledge.embedding

import com.stacklens.application.port.out.knowledge.EmbeddingListingOverrides
import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.testsupport.FakeContainerRuntimeProbePort
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OllamaEmbeddingAdapterModelsTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: OllamaEmbeddingAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter =
            OllamaEmbeddingAdapter(
                httpFactory = ReactiveHttpClientFactory(),
                envOllamaUrl = "http://localhost:11434",
                containerRuntimeProbe = FakeContainerRuntimeProbePort(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(
        provider: KnowledgeEmbeddingProvider = KnowledgeEmbeddingProvider.OLLAMA,
        ollamaUrl: String = "http://localhost:11434",
        openAiApiKey: String? = null,
        openRouterApiKey: String? = null,
    ) = KnowledgeEmbeddingConfig(
        provider = provider,
        model = "nomic-embed-text",
        ollamaUrl = ollamaUrl,
        openAiApiKey = openAiApiKey,
        openRouterApiKey = openRouterApiKey,
    )

    private fun mockOllama(
        tags: String,
        showResponses: Map<String, String> = emptyMap(),
    ): String {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(tags)
                .setResponseCode(200),
        )
        for ((_, body) in showResponses) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(body)
                    .setResponseCode(200),
            )
        }
        return server.url("").toString().removeSuffix("/")
    }

    @Test
    fun `a config naming another provider is served from the overridden ollama url`() {
        val base =
            mockOllama(
                tags = """{"models":[{"name":"nomic-embed-text:latest"}]}""",
                showResponses = mapOf("nomic-embed-text:latest" to """{"capabilities":["embedding"]}"""),
            )

        val result =
            adapter.availableModels(
                config(provider = KnowledgeEmbeddingProvider.OPENAI, ollamaUrl = "http://unused:11434"),
                EmbeddingListingOverrides(baseUrl = base, apiKey = null),
            )

        assertEquals(listOf("nomic-embed-text:latest"), result)
    }

    @Test
    fun `OLLAMA returns only models whose capabilities include embedding`() {
        val base =
            mockOllama(
                tags =
                    """
                    {
                      "models": [
                        {"name": "nomic-embed-text:latest"},
                        {"name": "mxbai-embed-large:latest"},
                        {"name": "qwen3.6:35b-mlx"},
                        {"name": "llama3.1:8b"}
                      ]
                    }
                    """.trimIndent(),
                showResponses =
                    mapOf(
                        "nomic-embed-text:latest" to """{"capabilities":["embedding"]}""",
                        "mxbai-embed-large:latest" to """{"capabilities":["embedding"]}""",
                        "qwen3.6:35b-mlx" to """{"capabilities":["completion"]}""",
                        "llama3.1:8b" to """{"capabilities":["completion"]}""",
                    ),
            )

        val result = adapter.availableModels(config(ollamaUrl = base), EmbeddingListingOverrides(null, null))

        assertEquals(listOf("nomic-embed-text:latest", "mxbai-embed-large:latest"), result)
    }

    @Test
    fun `capabilities cache returns the same models on a second call without re-fetching show`() {
        val base =
            mockOllama(
                tags =
                    """
                    {"models": [{"name": "nomic-embed-text:latest"}]}
                    """.trimIndent(),
                showResponses =
                    mapOf("nomic-embed-text:latest" to """{"capabilities":["embedding"]}"""),
            )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"models": [{"name": "nomic-embed-text:latest"}]}""")
                .setResponseCode(200),
        )

        val cfg = config(ollamaUrl = base)
        val first = adapter.availableModels(cfg, EmbeddingListingOverrides(null, null))
        val second = adapter.availableModels(cfg, EmbeddingListingOverrides(null, null))

        assertEquals(listOf("nomic-embed-text:latest"), first)
        assertEquals(listOf("nomic-embed-text:latest"), second)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `Ollama unavailable returns empty list and logs no error`() {
        server.shutdown()
        val result = adapter.availableModels(config(ollamaUrl = "http://localhost:1"), EmbeddingListingOverrides(null, null))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `resolvedBaseUrl rewrites localhost to host-docker-internal when running in a container`() {
        val inside = adapterWith(FakeContainerRuntimeProbePort(ContainerRuntime.DOCKER))

        assertEquals(
            "http://host.docker.internal:11434",
            inside.resolvedBaseUrl(config(ollamaUrl = "http://localhost:11434")),
        )
    }

    @Test
    fun `resolvedBaseUrl leaves non-localhost URLs untouched even inside a container`() {
        val inside = adapterWith(FakeContainerRuntimeProbePort(ContainerRuntime.DOCKER))

        assertEquals(
            "http://my-ollama:11434",
            inside.resolvedBaseUrl(config(ollamaUrl = "http://my-ollama:11434")),
        )
    }

    private fun adapterWith(probe: com.stacklens.application.port.out.platform.ContainerRuntimeProbePort) =
        OllamaEmbeddingAdapter(
            httpFactory = ReactiveHttpClientFactory(),
            envOllamaUrl = "http://localhost:11434",
            containerRuntimeProbe = probe,
        )
}
