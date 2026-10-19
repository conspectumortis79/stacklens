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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenAiAdapterHelpersTest {
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
        OpenAiAdapter(
            defaultOpenAiBaseUrl = baseUrl,
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
        )

    private fun aiConfig(
        openAiReasoningEffort: String = "medium",
        language: String = "de",
    ): AiConfig {
        val cfg = AiConfig(language = language)
        cfg.openAiReasoningEffort = openAiReasoningEffort
        return cfg
    }

    @Test
    fun `resolveOpenAiBaseUrl returns the constructor-provided base URL`() {
        val customAdapter = adapter("http://custom.local")
        assertEquals("http://custom.local", customAdapter.resolveOpenAiBaseUrl())
    }

    @Test
    fun `resolveOpenAiBaseUrl returns default when no override`() {
        val a =
            OpenAiAdapter(
                defaultOpenAiBaseUrl = "https://default.example.com",
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoader,
            )
        assertEquals("https://default.example.com", a.resolveOpenAiBaseUrl())
    }

    @Test
    fun `supportsReasoningEffort accepts o-series models`() {
        val a = adapter()
        assertTrue(a.supportsReasoningEffort("o1"))
        assertTrue(a.supportsReasoningEffort("o3"))
        assertTrue(a.supportsReasoningEffort("o4-mini"))
    }

    @Test
    fun `supportsReasoningEffort accepts uppercase o-series`() {
        val a = adapter()
        assertTrue(a.supportsReasoningEffort("O1-PREVIEW"))
        assertTrue(a.supportsReasoningEffort("O3-MINI"))
    }

    @Test
    fun `supportsReasoningEffort rejects non-o-series models`() {
        val a = adapter()
        assertFalse(a.supportsReasoningEffort("gpt-5.2"))
        assertFalse(a.supportsReasoningEffort("gpt-4o"))
        assertFalse(a.supportsReasoningEffort("claude-opus"))
    }

    @Test
    fun `supportsReasoningEffort is case-insensitive`() {
        val a = adapter()
        assertTrue(a.supportsReasoningEffort("O1"))
        assertFalse(a.supportsReasoningEffort("GPT-4O"))
    }

    @Test
    fun `resolveReasoningEffort returns medium as default`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals("medium", adapter().resolveReasoningEffort())
    }

    @Test
    fun `resolveReasoningEffort returns config value when valid`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiReasoningEffort = "low"))
        assertEquals("low", adapter().resolveReasoningEffort())
    }

    @Test
    fun `resolveReasoningEffort normalizes to lowercase`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiReasoningEffort = "HIGH"))
        assertEquals("high", adapter().resolveReasoningEffort())
    }

    @Test
    fun `resolveReasoningEffort rejects unsupported values`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiReasoningEffort = "extreme"))
        assertEquals("medium", adapter().resolveReasoningEffort())
    }

    @Test
    fun `resolveReasoningEffort handles blank value`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiReasoningEffort = ""))
        assertEquals("medium", adapter().resolveReasoningEffort())
    }

    @Test
    fun `resolveReasoningEffort trims whitespace`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiReasoningEffort = "  high  "))
        assertEquals("high", adapter().resolveReasoningEffort())
    }

    @Test
    fun `resolveSystemPrompt delegates to promptLoader`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "en"))
        whenever(promptLoader.get("en", "commontools.systemPrompt")).thenReturn("the prompt")
        assertEquals("the prompt", adapter().resolveSystemPrompt())
    }

    @Test
    fun `resolveSystemPrompt uses language from config`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "fr"))
        whenever(promptLoader.get("fr", "commontools.systemPrompt")).thenReturn("le prompt")
        assertEquals("le prompt", adapter().resolveSystemPrompt())
    }

    @Test
    fun `buildRequestBody includes model and prompt`() {
        val a = adapter()
        val body = a.buildRequestBody("gpt-5.2", "hello")
        val parsed = objectMapper.readTree(body)
        assertEquals("gpt-5.2", parsed.path("model").asText())
        assertEquals("hello", parsed.path("input").asText())
    }

    @Test
    fun `buildRequestBody sets store to false`() {
        val a = adapter()
        val body = a.buildRequestBody("gpt-5.2", "hello")
        val parsed = objectMapper.readTree(body)
        assertFalse(parsed.path("store").asBoolean())
    }

    @Test
    fun `buildRequestBody includes reasoning when model supports it`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiReasoningEffort = "low"))
        val a = adapter()
        val body = a.buildRequestBody("o1", "hello")
        val parsed = objectMapper.readTree(body)
        assertEquals("low", parsed.path("reasoning").path("effort").asText())
    }

    @Test
    fun `buildRequestBody omits reasoning when model does not support it`() {
        val a = adapter()
        val body = a.buildRequestBody("gpt-5.2", "hello")
        val parsed = objectMapper.readTree(body)
        assertTrue(parsed.path("reasoning").isMissingNode || parsed.path("reasoning").isNull)
    }

    @Test
    fun `buildRequestBody includes system prompt as instructions`() {
        val a = adapter()
        val body = a.buildRequestBody("gpt-5.2", "hello")
        val parsed = objectMapper.readTree(body)
        assertNotNull(parsed.path("instructions"))
    }

    @Test
    fun `execute returns fallback text when output_text is missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            com.stacklens.domain.model
                .AiConfig(openAiApiKey = "sk-test"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"output":[{"content":[{"text":"fallback"}]}]}""",
                ),
        )
        val result = adapter().execute("gpt-5.2", "hello")
        assertEquals("fallback", result)
    }

    @Test
    fun `executeWithStreamingPerLine 4-arg fallback when repoDir is null`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            com.stacklens.domain.model
                .AiConfig(openAiApiKey = "sk-test"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"output_text":"via-4-arg","output":[{"content":[{"text":"via-4-arg"}]}]}""",
                ),
        )
        var captured: String? = null
        val result =
            adapter().executeWithStreamingPerLine("gpt-5.2", "hi", onPartialOutput = { partial ->
                captured = partial
            })
        assertEquals("via-4-arg", result)
        assertEquals("via-4-arg", captured)
    }
}
