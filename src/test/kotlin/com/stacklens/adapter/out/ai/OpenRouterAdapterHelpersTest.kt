package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.ai.OpenRouterProperties
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenRouterAdapterHelpersTest {
    private lateinit var server: MockWebServer
    private lateinit var aiConfigRepo: AiConfigPort
    private lateinit var promptLoader: PromptReaderPort
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        aiConfigRepo = mock()
        promptLoader = mock()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system prompt")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(baseUrl: String = server.url("/").toString().trimEnd('/')) =
        OpenRouterAdapter(
            defaultBaseUrl = baseUrl,
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
        )

    private fun aiConfig(
        openRouterApiKey: String = "sk-or-test",
        model: String = "openai/gpt-4.1",
        language: String = "en",
    ) = AiConfig(openRouterApiKey = openRouterApiKey, model = model, language = language)

    @Test
    fun `isNonToolCapableModel returns false when patterns list is empty`() {
        val a =
            OpenRouterAdapter(
                defaultBaseUrl = "http://x",
                timeoutSeconds = 10L,
                aiConfigRepo = mock<AiConfigPort>(),
                objectMapper = ObjectMapper(),
                promptLoader = mock<PromptReaderPort>(),
                nonToolCapablePatterns = emptyList(),
            )
        assertFalse(a.isNonToolCapableModel("anthropic/claude-3"))
    }

    @Test
    fun `isNonToolCapableModel matches known pattern case-insensitively`() {
        val a =
            OpenRouterAdapter(
                defaultBaseUrl = "http://x",
                timeoutSeconds = 10L,
                aiConfigRepo = mock<AiConfigPort>(),
                objectMapper = ObjectMapper(),
                promptLoader = mock<PromptReaderPort>(),
                nonToolCapablePatterns = listOf("minimax"),
            )
        assertTrue(a.isNonToolCapableModel("minimax/M3"))
        assertTrue(a.isNonToolCapableModel("MiniMax-M3"))
        assertTrue(a.isNonToolCapableModel("MINIMAX-M3"))
        assertFalse(a.isNonToolCapableModel("anthropic/claude-3"))
    }

    @Test
    fun `isNonToolCapableModel returns false for non-matching model`() {
        val a =
            OpenRouterAdapter(
                defaultBaseUrl = "http://x",
                timeoutSeconds = 10L,
                aiConfigRepo = mock<AiConfigPort>(),
                objectMapper = ObjectMapper(),
                promptLoader = mock<PromptReaderPort>(),
                nonToolCapablePatterns = OpenRouterProperties.DEFAULT_NON_TOOL_CAPABLE_PATTERNS,
            )
        assertFalse(a.isNonToolCapableModel("openai/gpt-4.1"))
        assertFalse(a.isNonToolCapableModel("anthropic/claude-3"))
    }

    @Test
    fun `DEFAULT_NON_TOOL_CAPABLE_PATTERNS is not empty by default`() {
        assertTrue(OpenRouterProperties.DEFAULT_NON_TOOL_CAPABLE_PATTERNS.isNotEmpty())
    }

    @Test
    fun `buildChatRequestBody includes model and messages`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val body = adapter().buildChatRequestBody("openai/gpt-4.1", "hello")
        val parsed = objectMapper.readTree(body)
        assertEquals("openai/gpt-4.1", parsed.path("model").asText())
        assertTrue(parsed.path("messages").isArray)
    }

    @Test
    fun `buildChatRequestBody includes user prompt in messages`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val body = adapter().buildChatRequestBody("openai/gpt-4.1", "hello")
        val parsed = objectMapper.readTree(body)
        val messages = parsed.path("messages")
        assertEquals("user", messages[messages.size() - 1].path("role").asText())
        assertEquals("hello", messages[messages.size() - 1].path("content").asText())
    }

    @Test
    fun `buildChatRequestBody includes system prompt`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "en"))
        whenever(promptLoader.get("en", "commontools.systemPrompt")).thenReturn("MY_SYSTEM")
        val body = adapter().buildChatRequestBody("openai/gpt-4.1", "hello")
        val parsed = objectMapper.readTree(body)
        val messages = parsed.path("messages")
        assertEquals("system", messages[0].path("role").asText())
        assertEquals("MY_SYSTEM", messages[0].path("content").asText())
    }

    @Test
    fun `parseChatResponse returns text from choices array`() {
        val json = """{"choices":[{"message":{"content":"the answer"}}]}"""
        assertEquals("the answer", adapter().parseChatResponse(json))
    }

    @Test
    fun `parseChatResponse returns empty string when content missing`() {
        val json = """{"choices":[{"message":{}}]}"""
        assertEquals("", adapter().parseChatResponse(json))
    }

    @Test
    fun `parseChatResponse returns empty string when choices empty`() {
        val json = """{"choices":[]}"""
        assertEquals("", adapter().parseChatResponse(json))
    }

    @Test
    fun `parseChatResponse returns empty string when choices missing`() {
        val json = """{"other":"value"}"""
        assertEquals("", adapter().parseChatResponse(json))
    }

    @Test
    fun `resolveSystemPrompt delegates to promptLoader`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "en"))
        whenever(promptLoader.get("en", "commontools.systemPrompt")).thenReturn("the prompt")
        assertEquals("the prompt", adapter().resolveSystemPrompt())
    }

    @Test
    fun `resolveSystemPrompt uses default language when config missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        whenever(promptLoader.get("de", "commontools.systemPrompt")).thenReturn("der Prompt")
        assertEquals("der Prompt", adapter().resolveSystemPrompt())
    }

    @Test
    fun `execute returns response text on success`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openRouterApiKey = "sk-or-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"the answer"}}]}""",
                ),
        )
        val result = adapter().execute("openai/gpt-4.1", "hello")
        assertEquals("the answer", result)
    }

    @Test
    fun `execute returns empty string when content missing in response`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openRouterApiKey = "sk-or-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{}}]}"""),
        )
        val result = adapter().execute("openai/gpt-4.1", "hello")
        assertEquals("", result)
    }

    @Test
    fun `execute throws on HTTP 500`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openRouterApiKey = "sk-or-test"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        try {
            adapter().execute("openai/gpt-4.1", "hello")
            org.junit.jupiter.api.Assertions
                .fail<Unit>("Expected an exception but none was thrown")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("500") || e.message!!.contains("error"))
        }
    }
}
