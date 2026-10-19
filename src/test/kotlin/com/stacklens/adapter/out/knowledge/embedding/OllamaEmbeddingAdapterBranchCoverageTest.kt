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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient

class OllamaEmbeddingAdapterBranchCoverageTest {
    private val server = MockWebServer()

    private val realFactory = ReactiveHttpClientFactory()

    private val adapter = OllamaEmbeddingAdapter(realFactory, "http://localhost:11434", FakeContainerRuntimeProbePort())

    @BeforeEach
    fun startServer() {
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("").toString().removeSuffix("/")

    private fun config(url: String = baseUrl()): KnowledgeEmbeddingConfig =
        KnowledgeEmbeddingConfig(
            provider = KnowledgeEmbeddingProvider.OLLAMA,
            model = "nomic-embed-text",
            ollamaUrl = url,
        )

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body)
                .setResponseCode(code),
        )
    }

    private fun failingFactory(successfulBuilders: Int): ReactiveHttpClientFactory {
        val mocked = mock(ReactiveHttpClientFactory::class.java)
        var served = 0
        whenever(mocked.builder()).thenAnswer {
            served++
            if (served <= successfulBuilders) realFactory.builder() else throw IllegalStateException("no client")
        }
        return mocked
    }

    @Test
    fun `reports its provider`() {
        assertEquals(KnowledgeEmbeddingProvider.OLLAMA, adapter.provider())
    }

    @Test
    fun `embed answers an empty text list with no vectors`() {
        assertEquals(emptyList<FloatArray>(), adapter.embed(config(), emptyList()))
    }

    @Test
    fun `embed rejects vectors that contain non numeric values`() {
        enqueue("""{"embeddings":[["first",2.0]]}""")
        assertThrows(IllegalStateException::class.java) {
            adapter.embed(config(), listOf("text"))
        }
    }

    @Test
    fun `embed returns one float array per vector`() {
        enqueue("""{"embeddings":[[1.5,2.5],[3.5,4.5]]}""")
        val vectors = adapter.embed(config(), listOf("text"))
        assertEquals(2, vectors.size)
        assertEquals(1.5f, vectors[0][0])
    }

    @Test
    fun `available models keeps unique non blank embedding models only`() {
        enqueue(
            """
            {"models":[{"name":"  "},{"name":"nomic-embed-text"},{"name":"nomic-embed-text"},{"other":1},"plain"]}
            """.trimIndent(),
        )
        enqueue("""{"capabilities":["embedding","   ",null]}""")
        assertEquals(listOf("nomic-embed-text"), adapter.availableModels(config(), EmbeddingListingOverrides(null, null)))
    }

    @Test
    fun `available models yields nothing when the tag listing answers with an error`() {
        enqueue("""{"error":"unavailable"}""", code = 500)
        assertTrue(adapter.availableModels(config(), EmbeddingListingOverrides(null, null)).isEmpty())
    }

    @Test
    fun `available models yields nothing when the client cannot be created`() {
        val adapter = OllamaEmbeddingAdapter(failingFactory(0), "http://localhost:11434", FakeContainerRuntimeProbePort())
        assertTrue(adapter.availableModels(config(), EmbeddingListingOverrides(null, null)).isEmpty())
    }

    @Test
    fun `available models drops models whose capability lookup fails`() {
        enqueue("""{"models":[{"name":"nomic-embed-text"}]}""")
        val adapter = OllamaEmbeddingAdapter(failingFactory(1), "http://localhost:11434", FakeContainerRuntimeProbePort())
        assertTrue(adapter.availableModels(config(), EmbeddingListingOverrides(null, null)).isEmpty())
    }

    @Test
    fun `available models drops models without an embedding capability`() {
        enqueue("""{"models":[{"name":"llama3"}]}""")
        enqueue("""{"capabilities":["completion"]}""")
        assertTrue(adapter.availableModels(config(), EmbeddingListingOverrides(null, null)).isEmpty())
    }

    @Test
    fun `resolved base url keeps the configured endpoint`() {
        assertEquals("http://localhost:11434", adapter.resolvedBaseUrl(config(url = "http://localhost:11434/")))
        assertEquals("http://other:11434", adapter.resolvedBaseUrl(config(url = "http://other:11434")))
    }

    @Test
    fun `resolved base url rewrites localhost only for a dockerised process`() {
        val inside =
            OllamaEmbeddingAdapter(
                realFactory,
                "http://localhost:11434",
                FakeContainerRuntimeProbePort(ContainerRuntime.DOCKER),
            )
        assertEquals("http://host.docker.internal:11434", inside.resolvedBaseUrl(config(url = "http://localhost:11434")))
    }

    @Test
    fun `plain clients expose a builder and the default memory limit`() {
        val builder: WebClient.Builder = realFactory.builder()
        assertTrue(builder.build().toString().isNotEmpty())
        assertEquals(10 * 1024 * 1024, realFactory.defaultMaxInMemorySizeBytes)
    }
}
