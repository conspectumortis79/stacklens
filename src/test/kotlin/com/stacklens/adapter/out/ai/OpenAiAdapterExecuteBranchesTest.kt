package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class OpenAiAdapterExecuteBranchesTest {
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
        whenever(cfg.openAiApiKey).thenReturn("test-key-12345")
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
                .setBody("""{"output_text":"hello"}"""),
        )
        val result = adapter().execute("test-model", "hi")
        assertEquals("hello", result)
    }

    @Test
    fun `execute returns empty when response has no content`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[]}"""),
        )
        val result = adapter().execute("test-model", "hi")
        assertEquals("", result)
    }

    @Test
    fun `execute throws on non-2xx response`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        assertThrows(IllegalStateException::class.java) {
            adapter().execute("test-model", "hi")
        }
    }

    @Test
    fun `isReady returns true when key is configured`() {
        assertEquals(true, adapter().isReady())
    }

    @Test
    fun `isReady returns false when no key configured`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.openAiApiKey).thenReturn(null)
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val adp =
            OpenAiAdapter(
                defaultOpenAiBaseUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
            )
        assertEquals(false, adp.isReady())
    }

    @Test
    fun `resolveModel returns the configured model`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.openAiApiKey).thenReturn("test-key-12345")
        whenever(cfg.model).thenReturn("custom-model")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val adp =
            OpenAiAdapter(
                defaultOpenAiBaseUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
            )
        assertEquals("custom-model", adp.resolveModel())
    }

    @Test
    fun `resolveLanguage returns the configured language`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.openAiApiKey).thenReturn("test-key-12345")
        whenever(cfg.language).thenReturn("en")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val adp =
            OpenAiAdapter(
                defaultOpenAiBaseUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
            )
        assertEquals("en", adp.resolveLanguage())
    }

    @Test
    fun `resolveToken returns the configured key`() {
        assertEquals("test-key-12345", adapter().resolveToken())
    }

    @Test
    fun `executeWithStreamingPerLine falls back when repoDir is null`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"output_text":"plain-null"}"""),
        )
        val result = adapter().executeWithStreamingPerLine("test-model", "hi", null, null, onPartialOutput = { })
        assertEquals("plain-null", result)
    }

    @Test
    fun `executeWithStreamingPerLine falls back when repoDir does not exist`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"output_text":"plain-missing"}"""),
        )
        val result =
            adapter().executeWithStreamingPerLine("test-model", "hi", null, java.io.File("/nonexistent"), onPartialOutput = { })
        assertEquals("plain-missing", result)
    }
}
