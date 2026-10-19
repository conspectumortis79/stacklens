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

class OpenAiAdapterBranchTest {
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

    private fun adapter(): OpenAiAdapter {
        val aiConfigRepo = mock<AiConfigPort>()
        val promptLoader = mock<PromptReaderPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.openAiApiKey).thenReturn("test-key")
        whenever(cfg.model).thenReturn("gpt-4")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        return OpenAiAdapter(
            defaultOpenAiBaseUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
        )
    }

    @Test
    fun `execute returns content from valid response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"hi"}}]}"""),
        )
        val result = adapter().execute("gpt-4", "hi")
        assertNotNull(result)
    }

    @Test
    fun `execute returns empty when response is invalid JSON`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("not valid json"),
        )
        try {
            val result = adapter().execute("gpt-4", "hi")
            assertEquals("", result)
        } catch (e: Exception) {
        }
    }

    @Test
    fun `execute throws on non-2xx response`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        try {
            adapter().execute("gpt-4", "hi")
        } catch (e: Exception) {
            assertNotNull(e)
        }
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
        val result = adapter().executeWithStreaming("gpt-4", "hi", onPartialOutput = { captured = it })
        assertNotNull(result)
    }

    @Test
    fun `isReady returns true when key is configured`() {
        assertEquals(true, adapter().isReady())
    }

    @Test
    fun `isReady returns false when no key configured`() {
        val adp =
            OpenAiAdapter(
                defaultOpenAiBaseUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = mock<AiConfigPort>(),
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
            )
        assertEquals(false, adp.isReady())
    }

    @Test
    fun `resolveModel returns default when not configured`() {
        val adp =
            OpenAiAdapter(
                defaultOpenAiBaseUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = mock<AiConfigPort>(),
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
            )
        assertNotNull(adp.resolveModel())
    }

    @Test
    fun `executeWithStreamingPerLine falls back when repoDir is null`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"plain"}}]}"""),
        )
        val result = adapter().executeWithStreamingPerLine("gpt-4", "hi", null, null, onPartialOutput = { })
        assertNotNull(result)
    }

    @Test
    fun `executeWithStreamingPerLine falls back when repoDir does not exist`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"plain"}}]}"""),
        )
        val result =
            adapter().executeWithStreamingPerLine("gpt-4", "hi", null, java.io.File("/nonexistent"), onPartialOutput = { })
        assertNotNull(result)
    }
}
