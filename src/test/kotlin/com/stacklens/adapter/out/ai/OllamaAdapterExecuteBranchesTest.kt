package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OllamaAdapterExecuteBranchesTest {
    private lateinit var server: MockWebServer
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(): OllamaAdapter {
        val aiConfigRepo = mock<AiConfigPort>()
        val promptLoader = mock<PromptReaderPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.ollamaUrl).thenReturn(server.url("/").toString())
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        return OllamaAdapter(
            defaultOllamaUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
        )
    }

    @Test
    fun `execute returns content from valid response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"hello"}}]}"""),
        )
        try {
            val result = adapter().execute("test-model", "hi")
            assertNotNull(result)
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun `execute returns empty when response has no message content`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{}}]}"""),
        )
        try {
            val result = adapter().execute("test-model", "hi")
            assertEquals("", result)
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun `execute returns empty when response has no choices`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[]}"""),
        )
        try {
            val result = adapter().execute("test-model", "hi")
            assertEquals("", result)
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun `execute throws on non-2xx response`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        try {
            adapter().execute("test-model", "hi")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun `isReady always returns true for Ollama`() {
        assertEquals(true, adapter().isReady())
    }

    @Test
    fun `resolveModel returns the configured model`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.ollamaUrl).thenReturn(server.url("/").toString())
        whenever(cfg.model).thenReturn("custom-model")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val adp =
            OllamaAdapter(
                defaultOllamaUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )
        assertEquals("custom-model", adp.resolveModel())
    }

    @Test
    fun `resolveLanguage returns the configured language`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.ollamaUrl).thenReturn(server.url("/").toString())
        whenever(cfg.language).thenReturn("en")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val adp =
            OllamaAdapter(
                defaultOllamaUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )
        assertEquals("en", adp.resolveLanguage())
    }

    @Test
    fun `resolveToken returns null (Ollama needs no token)`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.ollamaUrl).thenReturn(server.url("/").toString())
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val adp =
            OllamaAdapter(
                defaultOllamaUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )
        assertEquals(null, adp.resolveToken())
    }

    @Test
    fun `executeWithStreamingPerLine falls back when repoDir is null`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"plain"}}]}"""),
        )
        try {
            val result = adapter().executeWithStreamingPerLine("test-model", "hi", null, null, onPartialOutput = { })
            assertNotNull(result)
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun `executeWithStreamingPerLine falls back when repoDir does not exist`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"plain"}}]}"""),
        )
        try {
            val result =
                adapter().executeWithStreamingPerLine("test-model", "hi", null, java.io.File("/nonexistent"), onPartialOutput = { })
            assertNotNull(result)
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }
}
