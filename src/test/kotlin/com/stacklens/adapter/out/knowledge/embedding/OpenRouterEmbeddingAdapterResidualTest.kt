package com.stacklens.adapter.out.knowledge.embedding

import com.stacklens.application.port.out.knowledge.EmbeddingListingOverrides
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenRouterEmbeddingAdapterResidualTest {
    private lateinit var server: MockWebServer

    private lateinit var adapter: OpenRouterEmbeddingAdapter

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
        adapter =
            OpenRouterEmbeddingAdapter(
                httpFactory = ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 10_000),
                configuredBaseUrl = server.url("/").toString().trimEnd('/'),
            )
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun config(key: String? = "sk-or-test") =
        KnowledgeEmbeddingConfig(
            provider = KnowledgeEmbeddingProvider.OPENROUTER,
            model = "openai/text-embedding-3-small",
            ollamaUrl = "http://localhost:11434",
            openRouterApiKey = key,
        )

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `an index outside the answer length is rejected`() {
        server.enqueue(json("""{"data":[{"index":7,"embedding":[1.0,2.0]}]}"""))

        val failure = assertThrows(IllegalStateException::class.java) { adapter.embed(config(), listOf("text")) }

        assertTrue(failure.message!!.contains("out-of-range index"), failure.message)
    }

    @Test
    fun `a negative index is rejected as well`() {
        server.enqueue(json("""{"data":[{"index":-3,"embedding":[1.0]}]}"""))

        assertThrows(IllegalStateException::class.java) { adapter.embed(config(), listOf("text")) }
    }

    @Test
    fun `an answer that leaves a slot empty reports the missing embedding`() {
        server.enqueue(json("""{"data":[{"index":0,"embedding":[0.5]},{"index":0,"embedding":[0.25]}]}"""))

        val failure = assertThrows(IllegalStateException::class.java) { adapter.embed(config(), listOf("text")) }

        assertTrue(failure.message!!.contains("missing embedding"), failure.message)
    }

    @Test
    fun `a complete answer yields one vector per text`() {
        server.enqueue(json("""{"data":[{"index":0,"embedding":[0.5,1.5]},{"index":1,"embedding":[2.5,3.5]}]}"""))

        val vectors = adapter.embed(config(), listOf("erster", "zweiter"))

        assertEquals(2, vectors.size)
        assertEquals(0.5f, vectors[0][0])
        assertEquals(3.5f, vectors[1][1])
    }

    @Test
    fun `the model list skips entries without a usable identifier`() {
        server.enqueue(
            json(
                """{"data":[
                   {"architecture":{"output_modalities":["embeddings"]}},
                   {"id":"","architecture":{"output_modalities":["embeddings"]}},
                   {"id":"b","architecture":{"output_modalities":["chat"]}},
                   {"id":"a","architecture":{"output_modalities":["embeddings"]}},
                   {"id":"a","architecture":{"output_modalities":["embeddings"]}}
                ]}""",
            ),
        )

        assertEquals(listOf("a"), adapter.availableModels(config(), EmbeddingListingOverrides(null, null)))
    }

    @Test
    fun `a model list without a payload stays empty`() {
        server.enqueue(json("null"))

        assertTrue(adapter.availableModels(config(), EmbeddingListingOverrides(null, null)).isEmpty())
    }

    @Test
    fun `a failing model list is logged and answered with an empty list`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("interner fehler"))

        assertTrue(adapter.availableModels(config(), EmbeddingListingOverrides(null, null)).isEmpty())
    }

    @Test
    fun `a model list with broken json is logged and answered with an empty list`() {
        server.enqueue(json("kein json{"))

        assertTrue(adapter.availableModels(config(), EmbeddingListingOverrides(null, null)).isEmpty())
    }
}
