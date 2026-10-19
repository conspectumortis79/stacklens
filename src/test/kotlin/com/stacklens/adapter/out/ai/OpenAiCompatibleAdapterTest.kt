package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

class OpenAiCompatibleAdapterTest {
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

    private val serverBaseUrl: String
        get() = server.url("/").toString().trimEnd('/')

    private fun adapter(
        fallbackBaseUrl: String = serverBaseUrl,
        patterns: List<String> = emptyList(),
        baseUrlOf: (AiConfig?) -> String? = { config -> config?.compatibleBaseUrl },
    ) = OpenAiCompatibleAdapter(
        endpoint =
            OpenAiCompatibleEndpoint(
                label = "OpenAI-compatible",
                fallbackBaseUrl = fallbackBaseUrl,
                defaultModel = "example/model-one",
                nonToolCapablePatterns = patterns,
                apiKeyOf = { config -> config?.compatibleApiKey },
                baseUrlOf = baseUrlOf,
            ),
        timeoutSeconds = 10L,
        aiConfigRepo = aiConfigRepo,
        objectMapper = objectMapper,
        promptLoader = promptLoader,
    )

    private fun config(
        key: String? = "sk-compatible",
        model: String? = null,
        baseUrl: String? = null,
    ) = AiConfig(compatibleApiKey = key, model = model, compatibleBaseUrl = baseUrl, language = "de")

    @Test
    fun `execute posts chat completions to the endpoint and returns the message content`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(model = "qwen3.8-flash"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"hello back"},"finish_reason":"stop"}]}"""),
        )

        assertEquals("hello back", adapter().execute("qwen3.8-flash", "hi"))

        val recorded: RecordedRequest = server.takeRequest()
        assertEquals("/chat/completions", recorded.path)
        assertEquals("Bearer sk-compatible", recorded.getHeader("Authorization"))
        assertEquals("POST", recorded.method)
        assertTrue(recorded.body.readUtf8().contains("\"model\":\"qwen3.8-flash\""))
    }

    @Test
    fun `execute prefers the stored base url over the configured fallback`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(baseUrl = serverBaseUrl, model = "m"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))

        assertEquals("ok", adapter(fallbackBaseUrl = "https://unused.invalid/v1").execute("m", "hi"))
        assertEquals("/chat/completions", server.takeRequest().path)
    }

    @Test
    fun `execute does not send openrouter specific headers`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(model = "m"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))

        adapter().execute("m", "hi")

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("HTTP-Referer"))
        assertNull(recorded.getHeader("X-Title"))
    }

    @Test
    fun `execute throws when no api key is configured`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = null))

        val error = assertThrows(IllegalStateException::class.java) { adapter().execute("m", "hi") }
        assertTrue(error.message!!.contains("OpenAI-compatible API key"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `execute reports an endpoint error including the http status`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(model = "m"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("upstream exploded"))

        val error = assertThrows(IllegalStateException::class.java) { adapter().execute("m", "hi") }
        assertTrue(error.message!!.contains("OpenAI-compatible error (HTTP 500)"))
        assertTrue(error.message!!.contains("upstream exploded"))
    }

    @Test
    fun `resolveToken reads the dedicated compatible key only`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(compatibleApiKey = "sk-compat", openRouterApiKey = "sk-or", model = "m"),
        )
        assertEquals("sk-compat", adapter().resolveToken())
        assertTrue(adapter().isReady())
    }

    @Test
    fun `resolveToken is null when the stored key is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(key = "   "))
        assertNull(adapter().resolveToken())
        assertFalse(adapter().isReady())
    }

    @Test
    fun `resolveModel falls back to the endpoint default model`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(model = "  "))
        assertEquals("example/model-one", adapter().resolveModel())
    }

    @Test
    fun `resolveModel returns the stored model when present`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(model = "deepseek-v4-flash"))
        assertEquals("deepseek-v4-flash", adapter().resolveModel())
    }

    @Test
    fun `resolveSystemPrompt appends the markdown hint for endpoint listed non tool models`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(model = "some-gemma-4"))

        val prompt = adapter(patterns = listOf("gemma")).resolveSystemPrompt()

        assertTrue(prompt.startsWith("system prompt"))
        assertTrue(prompt.contains("no tool-calling"))
    }

    @Test
    fun `isNonToolCapableModel is false when the endpoint declares no patterns`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(model = "some-gemma-4"))
        assertFalse(adapter().isNonToolCapableModel("some-gemma-4"))
    }

    @Test
    fun `executeWithStreamingPerLine sends tool definitions when a repo directory is given`(
        @TempDir repo: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(model = "m"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"done"},"finish_reason":"stop"}]}"""),
        )

        val result = adapter().executeWithStreamingPerLine("m", "write it", 10L, repo.toFile(), onPartialOutput = {})

        assertEquals("done", result)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"tools\""))
        assertTrue(body.contains("commontools.systemPrompt") || body.contains("system prompt"))
    }

    @Test
    fun `executeWithStreamingPerLine falls back to a plain call without a repo directory`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config(model = "m"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"content":"plain"}}]}"""))

        var partial = ""
        val result = adapter().executeWithStreamingPerLine("m", "hi", 10L, null, onPartialOutput = { partial = it })

        assertEquals("plain", result)
        assertEquals("plain", partial)
        assertFalse(
            server
                .takeRequest()
                .body
                .readUtf8()
                .contains("\"tools\""),
        )
    }
}
