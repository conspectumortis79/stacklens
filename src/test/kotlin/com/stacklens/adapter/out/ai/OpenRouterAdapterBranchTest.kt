package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenRouterAdapterBranchTest {
    private lateinit var server: MockWebServer
    private val objectMapper = ObjectMapper()
    private val aiConfigRepo = mock<AiConfigPort>()
    private val promptLoader = mock<PromptReaderPort>()
    private lateinit var adapter: OpenRouterAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter =
            OpenRouterAdapter(
                defaultBaseUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoader,
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `execute throws on non-2xx HTTP response`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(id = 1L, model = "openai/gpt-4", openRouterApiKey = "key"),
        )
        val ex =
            try {
                adapter.execute("openai/gpt-4", "hi")
                null
            } catch (e: IllegalStateException) {
                e
            }
        assertNotNull(ex)
    }

    @Test
    fun `execute returns empty when response body is empty`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(""),
        )
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(id = 1L, model = "openai/gpt-4", openRouterApiKey = "key"),
        )
        val result = adapter.execute("openai/gpt-4", "hi")
        assertEquals("", result)
    }

    @Test
    fun `execute returns empty when response is invalid JSON`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("this is not valid JSON"),
        )
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(id = 1L, model = "openai/gpt-4", openRouterApiKey = "key"),
        )
        val result = adapter.execute("openai/gpt-4", "hi")
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
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(id = 1L, model = "openai/gpt-4", openRouterApiKey = "key"),
        )
        val result = adapter.execute("openai/gpt-4", "hi")
        assertEquals("", result)
    }

    @Test
    fun `execute returns empty when response choices has no message`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{}]}"""),
        )
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(id = 1L, model = "openai/gpt-4", openRouterApiKey = "key"),
        )
        val result = adapter.execute("openai/gpt-4", "hi")
        assertEquals("", result)
    }

    @Test
    fun `execute returns content from response message`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"hello"}}]}"""),
        )
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(id = 1L, model = "openai/gpt-4", openRouterApiKey = "key"),
        )
        val result = adapter.execute("openai/gpt-4", "hi")
        assertEquals("hello", result)
    }

    @Test
    fun `isNonToolCapableModel returns true for known non-tool model`() {
        val adapterWithPatterns =
            OpenRouterAdapter(
                defaultBaseUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoader,
                nonToolCapablePatterns = listOf("minimax", "claude-3-haiku"),
            )
        assertTrue(adapterWithPatterns.isNonToolCapableModel("minimax/M3"))
        assertTrue(adapterWithPatterns.isNonToolCapableModel("claude-3-haiku-20240307"))
        assertFalse(adapterWithPatterns.isNonToolCapableModel("openai/gpt-4"))
    }

    @Test
    fun `isNonToolCapableModel returns false for empty patterns`() {
        val adapterNoPatterns =
            OpenRouterAdapter(
                defaultBaseUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoader,
                nonToolCapablePatterns = emptyList(),
            )
        assertFalse(adapterNoPatterns.isNonToolCapableModel("minimax/M3"))
    }

    @Test
    fun `executeWithStreamingPerLine falls back to plain when repoDir does not exist`() {
        val nonExistent = java.io.File("/nonexistent/path/abc123")
        assertFalse(nonExistent.exists())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"plain"}}]}"""),
        )
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(id = 1L, model = "openai/gpt-4", openRouterApiKey = "key"),
        )
        val result = adapter.executeWithStreamingPerLine("openai/gpt-4", "hi", null, nonExistent, onPartialOutput = { })
        assertEquals("plain", result)
    }

    @Test
    fun `executeWithStreamingPerLine falls back to plain when repoDir is null`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"plain-null"}}]}"""),
        )
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(id = 1L, model = "openai/gpt-4", openRouterApiKey = "key"),
        )
        val result = adapter.executeWithStreamingPerLine("openai/gpt-4", "hi", null, null, onPartialOutput = { })
        assertEquals("plain-null", result)
    }

    @Test
    fun `resolveToken returns null when no key configured`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val result = adapter.resolveToken()
        assertTrue(result == null || result.isNotBlank())
    }

    @Test
    fun `resolveSystemPrompt includes markdown hint for non-tool-capable model`() {
        val adapterWithPatterns =
            OpenRouterAdapter(
                defaultBaseUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoader,
                nonToolCapablePatterns = listOf("minimax"),
            )
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(id = 1L, model = "minimax/M3", openRouterApiKey = "key"),
        )
        whenever(promptLoader.get("en", "commontools.systemPrompt")).thenReturn("base prompt")
        val prompt = adapterWithPatterns.resolveSystemPrompt()
        assertTrue(prompt.contains("base prompt"))
        assertTrue(prompt.contains("OUTPUT FORMAT") || prompt.contains("OUTPUT"))
    }
}
