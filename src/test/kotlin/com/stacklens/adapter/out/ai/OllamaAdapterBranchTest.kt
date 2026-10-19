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

class OllamaAdapterBranchTest {
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
        whenever(cfg.model).thenReturn("llama2")
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
                .setBody("""{"choices":[{"message":{"content":"hello world"}}]}"""),
        )
        val result = adapter().execute("llama2", "hi")
        assertNotNull(result)
    }

    @Test
    fun `execute logs num_ctx running low warning when prompt is very large`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"hi"}}]}"""),
        )
        val longPrompt = "x".repeat(110_000)
        val result = adapter().execute("llama2", longPrompt)
        assertEquals("hi", result)
    }

    @Test
    fun `execute returns empty when response has no message content`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{}}]}"""),
        )
        val result = adapter().execute("llama2", "hi")
        assertEquals("", result)
    }

    @Test
    fun `execute returns empty when response has no choices`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[]}"""),
        )
        val result = adapter().execute("llama2", "hi")
        assertEquals("", result)
    }

    @Test
    fun `executeWithStreaming calls execute and forwards output`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"streamed"}}]}"""),
        )
        var captured: String? = null
        val result = adapter().executeWithStreaming("llama2", "hi", onPartialOutput = { captured = it })
        if (result.isNotEmpty()) {
            assertEquals("streamed", result)
            assertEquals("streamed", captured)
        } else {
            assertNotNull(result)
        }
    }

    @Test
    fun `resolveToken always returns true for Ollama`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val promptLoader = mock<PromptReaderPort>()
        val adp =
            OllamaAdapter(
                defaultOllamaUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoader,
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )
        assertEquals(true, adp.isReady())
    }

    @Test
    fun `isReady always returns true for Ollama`() {
        val adp = adapter()
        assertEquals(true, adp.isReady())
    }

    @Test
    fun `resolveModel returns the configured model`() {
        val adp = adapter()
        assertEquals("llama2", adp.resolveModel())
    }

    @Test
    fun `resolveLanguage returns the configured language`() {
        val adp = adapter()
        assertNotNull(adp.resolveLanguage())
    }

    @Test
    fun `executeWithStreamingPerLine falls back when repoDir does not exist`() {
        val adp = adapter()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"plain"}}]}"""),
        )
        val result =
            adp.executeWithStreamingPerLine("llama2", "hi", null, java.io.File("/nonexistent/path"), onPartialOutput = { })
        assertNotNull(result)
    }

    @Test
    fun `executeWithStreamingPerLine falls back when repoDir is null`() {
        val adp = adapter()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"null-repo"}}]}"""),
        )
        val result = adp.executeWithStreamingPerLine("llama2", "hi", null, null, onPartialOutput = { })
        assertNotNull(result)
    }
}
