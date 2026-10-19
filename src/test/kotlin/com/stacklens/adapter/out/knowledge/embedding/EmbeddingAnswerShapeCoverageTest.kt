package com.stacklens.adapter.out.knowledge.embedding

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.knowledge.EmbeddingListingOverrides
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.shared.http.ReactiveHttpClientFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class EmbeddingAnswerShapeCoverageTest {
    private val objectMapper = ObjectMapper()
    private val jsonFactory: ReactiveHttpClientFactory = mock()
    private val builder: WebClient.Builder = mock()
    private val client: WebClient = mock()
    private val uriSpec: WebClient.RequestBodyUriSpec = mock()
    private val bodySpec: WebClient.RequestBodySpec = mock()
    private val headersSpec: WebClient.RequestHeadersSpec<*> = mock()
    private val responseSpec: WebClient.ResponseSpec = mock()

    private fun wireChain(root: JsonNode?) {
        whenever(jsonFactory.builder()).thenReturn(builder)
        whenever(builder.baseUrl(any())).thenReturn(builder)
        whenever(builder.defaultHeader(any(), any())).thenReturn(builder)
        whenever(builder.build()).thenReturn(client)
        whenever(client.post()).thenReturn(uriSpec)
        whenever(client.get()).thenReturn(uriSpec)
        whenever(uriSpec.uri(any<String>())).thenReturn(bodySpec)
        whenever(bodySpec.bodyValue(any())).thenReturn(headersSpec)
        whenever(headersSpec.retrieve()).thenReturn(responseSpec)
        val mono = mock<Mono<JsonNode>>()
        whenever(mono.onErrorResume(any())).thenReturn(mono)
        whenever(mono.block(anyOrNull())).thenReturn(root)
        whenever(responseSpec.bodyToMono(any<org.springframework.core.ParameterizedTypeReference<JsonNode>>())).thenReturn(mono)
    }

    private fun openAiCfg() = KnowledgeEmbeddingConfig(provider = KnowledgeEmbeddingProvider.OPENAI, openAiApiKey = "key", model = "m")

    private fun openRouterCfg() =
        KnowledgeEmbeddingConfig(
            provider = KnowledgeEmbeddingProvider.OPENROUTER,
            openRouterApiKey = "key",
            model = "m",
        )

    @Test
    fun `an embedding entry without an index is rejected`() {
        wireChain(objectMapper.readTree("""{"data":[{"embedding":[1.0]}]}"""))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                OpenAiEmbeddingAdapter(jsonFactory).embed(openAiCfg(), listOf("a"))
            }

        assertTrue(failure.message!!.contains("out-of-range index: -1"), failure.message)
    }

    @Test
    fun `an index used twice leaves a hole that is reported`() {
        wireChain(objectMapper.readTree("""{"data":[{"index":0,"embedding":[1.0]},{"index":0,"embedding":[2.0]}]}"""))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                OpenAiEmbeddingAdapter(jsonFactory).embed(openAiCfg(), listOf("a", "b"))
            }

        assertTrue(failure.message!!.contains("missing embedding"), failure.message)
    }

    @Test
    fun `a model listing that breaks while loading is swallowed`() {
        whenever(jsonFactory.builder()).thenThrow(IllegalStateException("no http for you"))

        assertEquals(
            emptyList<String>(),
            OpenRouterEmbeddingAdapter(jsonFactory, "https://openrouter.example").availableModels(openRouterCfg(), EmbeddingListingOverrides(null, null)),
        )
    }
}
