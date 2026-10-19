package com.stacklens.adapter.out.knowledge.embedding

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.testsupport.FakeContainerRuntimeProbePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class EmbeddingAdaptersHttpBranchTest {
    private val objectMapper = ObjectMapper()
    private val jsonFactory: ReactiveHttpClientFactory = mock()
    private val builder: WebClient.Builder = mock()
    private val client: WebClient = mock()
    private val uriSpec: WebClient.RequestBodyUriSpec = mock()
    private val bodySpec: WebClient.RequestBodySpec = mock()
    private val headersSpec: WebClient.RequestHeadersSpec<*> = mock()
    private val responseSpec: WebClient.ResponseSpec = mock()
    private val mono: Mono<JsonNode> = mock()

    private fun wireChain(root: JsonNode?) {
        whenever(jsonFactory.builder()).thenReturn(builder)
        whenever(builder.baseUrl(any())).thenReturn(builder)
        whenever(builder.defaultHeader(any(), any())).thenReturn(builder)
        whenever(builder.build()).thenReturn(client)
        whenever(client.post()).thenReturn(uriSpec)
        whenever(uriSpec.uri(any<String>())).thenReturn(bodySpec)
        whenever(bodySpec.bodyValue(any())).thenReturn(headersSpec)
        whenever(headersSpec.retrieve()).thenReturn(responseSpec)
        whenever(responseSpec.bodyToMono(any<org.springframework.core.ParameterizedTypeReference<JsonNode>>())).thenReturn(mono)
        whenever(mono.block(any())).thenReturn(root)
    }

    private fun openAiCfg() = KnowledgeEmbeddingConfig(provider = KnowledgeEmbeddingProvider.OPENAI, openAiApiKey = "key", model = "m")

    private fun openRouterCfg() = KnowledgeEmbeddingConfig(provider = KnowledgeEmbeddingProvider.OPENROUTER, openRouterApiKey = "key", model = "m")

    private fun ollamaCfg() = KnowledgeEmbeddingConfig(provider = KnowledgeEmbeddingProvider.OLLAMA, model = "m", ollamaUrl = "http://localhost:11434")

    @Test
    fun `openAi embed parses vectors by index`() {
        wireChain(objectMapper.readTree("""{"data":[{"index":0,"embedding":[1.0,2.0]},{"index":1,"embedding":[3.0]}]}"""))
        val result = OpenAiEmbeddingAdapter(jsonFactory).embed(openAiCfg(), listOf("a", "b"))
        assertEquals(2, result.size)
        assertEquals(2, result[0].size)
        assertEquals(3.0f, result[1][0])
    }

    @Test
    fun `openAi embed errors on null response`() {
        wireChain(null)
        val e = assertThrows(IllegalStateException::class.java) { OpenAiEmbeddingAdapter(jsonFactory).embed(openAiCfg(), listOf("a")) }
        assertTrue(e.message!!.contains("returned no response"))
    }

    @Test
    fun `openAi embed errors when data missing`() {
        wireChain(objectMapper.readTree("""{"foo":1}"""))
        val e = assertThrows(IllegalStateException::class.java) { OpenAiEmbeddingAdapter(jsonFactory).embed(openAiCfg(), listOf("a")) }
        assertTrue(e.message!!.contains("missing 'data' array"))
    }

    @Test
    fun `openAi embed errors on out of range index`() {
        wireChain(objectMapper.readTree("""{"data":[{"index":7,"embedding":[1.0]}]}"""))
        val e = assertThrows(IllegalStateException::class.java) { OpenAiEmbeddingAdapter(jsonFactory).embed(openAiCfg(), listOf("a")) }
        assertTrue(e.message!!.contains("out-of-range index"))
    }

    @Test
    fun `openRouter embed parses vectors by index`() {
        wireChain(objectMapper.readTree("""{"data":[{"index":0,"embedding":[1.0,2.0]}]}"""))
        val result = OpenRouterEmbeddingAdapter(jsonFactory, "https://openrouter.ai").embed(openRouterCfg(), listOf("a"))
        assertEquals(2, result.single().size)
    }

    @Test
    fun `openRouter embed errors on null response`() {
        wireChain(null)
        val e = assertThrows(IllegalStateException::class.java) { OpenRouterEmbeddingAdapter(jsonFactory, "https://openrouter.ai").embed(openRouterCfg(), listOf("a")) }
        assertTrue(e.message!!.contains("returned no response"))
    }

    @Test
    fun `openRouter embed errors when data missing`() {
        wireChain(objectMapper.readTree("""{}"""))
        val e = assertThrows(IllegalStateException::class.java) { OpenRouterEmbeddingAdapter(jsonFactory, "https://openrouter.ai").embed(openRouterCfg(), listOf("a")) }
        assertTrue(e.message!!.contains("missing 'data' array"))
    }

    @Test
    fun `openRouter embed errors on out of range index`() {
        wireChain(objectMapper.readTree("""{"data":[{"index":-1,"embedding":[1.0]}]}"""))
        val e = assertThrows(IllegalStateException::class.java) { OpenRouterEmbeddingAdapter(jsonFactory, "https://openrouter.ai").embed(openRouterCfg(), listOf("a")) }
        assertTrue(e.message!!.contains("out-of-range index"))
    }

    @Test
    fun `ollama embed parses embeddings`() {
        wireChain(objectMapper.readTree("""{"embeddings":[[1.0,2.0],[3.0]]}"""))
        val result = OllamaEmbeddingAdapter(jsonFactory, "", FakeContainerRuntimeProbePort()).embed(ollamaCfg(), listOf("a", "b"))
        assertEquals(2, result.size)
        assertEquals(2, result[0].size)
    }

    @Test
    fun `ollama embed errors on null response`() {
        wireChain(null)
        val adapter = OllamaEmbeddingAdapter(jsonFactory, "", FakeContainerRuntimeProbePort())
        val e = assertThrows(IllegalStateException::class.java) { adapter.embed(ollamaCfg(), listOf("a")) }
        assertTrue(e.message!!.contains("returned no response"))
    }

    @Test
    fun `ollama embed errors when embeddings missing`() {
        wireChain(objectMapper.readTree("""{}"""))
        val adapter = OllamaEmbeddingAdapter(jsonFactory, "", FakeContainerRuntimeProbePort())
        val e = assertThrows(IllegalStateException::class.java) { adapter.embed(ollamaCfg(), listOf("a")) }
        assertTrue(e.message!!.contains("'embeddings' array"))
    }

    @Test
    fun `ollama resolvedBaseUrl honors the env override and the container runtime`() {
        val outside = OllamaEmbeddingAdapter(jsonFactory, "http://env-host:11434", FakeContainerRuntimeProbePort())
        assertEquals(
            "http://env-host:11434",
            outside.resolvedBaseUrl(KnowledgeEmbeddingConfig(provider = KnowledgeEmbeddingProvider.OLLAMA, ollamaUrl = "", model = "m")),
        )
        assertEquals("http://localhost:11434", outside.resolvedBaseUrl(ollamaCfg()))
        val inside =
            OllamaEmbeddingAdapter(
                jsonFactory,
                "",
                FakeContainerRuntimeProbePort(ContainerRuntime.DOCKER),
            )
        assertEquals("http://host.docker.internal:11434", inside.resolvedBaseUrl(ollamaCfg()))
    }

    @Test
    fun `ollama embed answers empty input without touching the endpoint`() {
        val adapter = OllamaEmbeddingAdapter(jsonFactory, "", FakeContainerRuntimeProbePort())
        val cfg = KnowledgeEmbeddingConfig(provider = KnowledgeEmbeddingProvider.OPENAI, openAiApiKey = "key", model = "m")
        assertEquals(emptyList<FloatArray>(), adapter.embed(cfg, emptyList()))
    }
}
