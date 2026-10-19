package com.stacklens.adapter.out.knowledge.embedding

import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.testsupport.FakeContainerRuntimeProbePort
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class OllamaEmbeddingAdapterCapabilitiesTest {
    private val server = MockWebServer()
    private val adapter =
        OllamaEmbeddingAdapter(
            httpFactory = ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 5_000),
            envOllamaUrl = "",
            containerRuntimeProbe = FakeContainerRuntimeProbePort(),
        )

    @BeforeEach
    fun start() {
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun fetchCapabilities(base: String): Set<String> {
        val method =
            OllamaEmbeddingAdapter::class.java.getDeclaredMethod("fetchCapabilities", String::class.java, String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(adapter, base, "nomic-embed-text") as Set<String>
    }

    @Test
    fun `a missing show answer yields no capabilities`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("unavailable"))

        assertEquals(emptySet<String>(), fetchCapabilities(server.url("/").toString().trimEnd('/')))
    }

    @Test
    fun `the capability list is filtered and cached`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"capabilities":["embedding"," tools ","",null]}""")
                .setBodyDelay(10, TimeUnit.MILLISECONDS),
        )

        assertEquals(setOf("embedding", " tools "), fetchCapabilities(server.url("/").toString().trimEnd('/')))
    }

    @Test
    fun `a broken url is reported as no capabilities`() {
        assertEquals(emptySet<String>(), fetchCapabilities("http://127.0.0.1:1"))
    }

    @Test
    fun `the base url is rewritten inside a container`() {
        val withoutMarker = adapter.resolvedBaseUrl(config("http://localhost:11434"))
        val withMarker =
            OllamaEmbeddingAdapter(
                httpFactory = ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 5_000),
                envOllamaUrl = "",
                containerRuntimeProbe = FakeContainerRuntimeProbePort(ContainerRuntime.DOCKER),
            ).resolvedBaseUrl(config("http://localhost:11434"))

        assertEquals("http://localhost:11434", withoutMarker)
        assertEquals("http://host.docker.internal:11434", withMarker)
    }

    private fun config(url: String) = KnowledgeEmbeddingConfig(ollamaUrl = url)
}
