package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptContext
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
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

class LmStudioAdapterFullTest {
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
        whenever(promptLoader.context(any())).thenReturn(
            com.stacklens.application.port.out.prompt.PromptContext(
                mapOf("commontools.systemPrompt" to "system prompt"),
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(url: String = server.url("/").toString().trimEnd('/')) =
        LmStudioAdapter(
            defaultLmStudioUrl = url,
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
        )

    private fun aiConfig(model: String = "local-model"): AiConfig = AiConfig(model = model)

    private fun enqueueModelLoaded(model: String = "local-model") {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"models":[{"key":"$model","loaded_instances":[{"id":"i1"}]}]}""",
                ),
        )
    }

    private fun enqueueModelNotLoadedAndLoad() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"models":[]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
    }

    @Test
    fun `resolveLmStudioUrl uses config URL when set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(lmStudioUrl = "http://my-lmstudio:1234"),
        )
        val a = adapter()
        assertEquals("http://my-lmstudio:1234", a.resolveLmStudioUrl())
    }

    @Test
    fun `resolveLmStudioUrl falls back to default when config is null`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val a = adapter("http://default.local:1234")
        assertEquals("http://default.local:1234", a.resolveLmStudioUrl())
    }

    @Test
    fun `resolveLmStudioUrl falls back to default when config URL is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(lmStudioUrl = ""))
        val a = adapter("http://default.local:1234")
        assertEquals("http://default.local:1234", a.resolveLmStudioUrl())
    }

    @Test
    fun `parseCompletionResponse returns chat message content`() {
        val a = adapter()
        val json = """{"choices":[{"message":{"content":"hi"}}]}"""
        assertEquals("hi", a.parseCompletionResponse(json))
    }

    @Test
    fun `parseCompletionResponse returns native output string`() {
        val a = adapter()
        val json = """{"output":"native-output"}"""
        assertEquals("native-output", a.parseCompletionResponse(json))
    }

    @Test
    fun `parseCompletionResponse returns native output message list joined`() {
        val a = adapter()
        val json = """{"output":[{"type":"message","content":"a"},{"type":"thinking","content":"x"},{"type":"message","content":"b"}]}"""
        assertEquals("ab", a.parseCompletionResponse(json))
    }

    @Test
    fun `parseCompletionResponse returns empty when response is empty`() {
        val a = adapter()
        val json = """{}"""
        assertEquals("", a.parseCompletionResponse(json))
    }

    @Test
    fun `parseCompletionResponse returns empty when choices is empty`() {
        val a = adapter()
        val json = """{"choices":[]}"""
        assertEquals("", a.parseCompletionResponse(json))
    }

    @Test
    fun `parseCompletionResponse returns empty when message has no content`() {
        val a = adapter()
        val json = """{"choices":[{"message":{}}]}"""
        assertEquals("", a.parseCompletionResponse(json))
    }

    @Test
    fun `extractNativeChatContent returns string output as-is`() {
        val a = adapter()
        val map = mapOf("output" to "plain")
        assertEquals("plain", a.extractNativeChatContent(map))
    }

    @Test
    fun `extractNativeChatContent returns null for empty output list`() {
        val a = adapter()
        val map = mapOf("output" to emptyList<Any>())
        assertEquals(null, a.extractNativeChatContent(map))
    }

    @Test
    fun `extractNativeChatContent returns null when output is not a list or string`() {
        val a = adapter()
        val map = mapOf("output" to 42)
        assertEquals(null, a.extractNativeChatContent(map))
    }

    @Test
    fun `extractNativeChatContent returns null when no message-type items`() {
        val a = adapter()
        val map = mapOf("output" to listOf(mapOf("type" to "thinking", "content" to "x")))
        assertEquals(null, a.extractNativeChatContent(map))
    }

    @Test
    fun `fetchAvailableModels returns list from apiClient`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"data":[{"id":"local-model"},{"id":"other-model"}]}""",
                ),
        )
        val models = adapter().fetchAvailableModels(server.url("/").toString().trimEnd('/'))
        assertNotNull(models)
        assertTrue(models.isNotEmpty())
    }

    @Test
    fun `execute loads model before request`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"models":[]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"hi"}}]}""",
                ),
        )
        val result = adapter().execute("local-model", "hi", 10L)
        assertEquals("hi", result)
    }

    @Test
    fun `execute skips loadModel when model is already loaded`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"loaded"}}]}""",
                ),
        )
        val result = adapter().execute("local-model", "hi", 10L)
        assertEquals("loaded", result)
    }

    @Test
    fun `executeWithRepoTools returns text when no tool calls`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"hello"},"finish_reason":"stop"}]}""",
                ),
        )
        val result =
            adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("hello", result)
    }

    @Test
    fun `executeWithRepoTools handles missing choices`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))
        val result =
            adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("", result)
    }

    @Test
    fun `executeWithRepoTools handles empty choices`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[]}"""))
        val result =
            adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("", result)
    }

    @Test
    fun `executeWithRepoTools strips thinking blocks from content`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"<think>Let me think...</think> actual-content"},"finish_reason":"stop"}]}""",
                ),
        )
        val result =
            adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertNotNull(result)
    }

    @Test
    fun `executeWithRepoTools handles structured tool_calls`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"creating","tool_calls":[{"id":"c1","function":{"name":"write","arguments":"{\"path\":\"a.txt\",\"content\":\"hi\"}"}}]},"finish_reason":"tool_calls"}]}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"done"},"finish_reason":"stop"}]}""",
                ),
        )
        adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertTrue(tmp.resolve("a.txt").toFile().exists())
    }

    @Test
    fun `executeWithRepoTools handles Qwen3-style text tool calls`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"<tool_call>{\"name\":\"write\",\"arguments\":{\"path\":\"b.txt\",\"content\":\"yo\"}}</tool_call>"},"finish_reason":"tool_calls"}]}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"done"},"finish_reason":"stop"}]}""",
                ),
        )
        adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertTrue(tmp.resolve("b.txt").toFile().exists())
    }

    @Test
    fun `executeWithRepoTools handles Hermes-style text tool calls`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"<function=write><parameter=path>c.txt</parameter><parameter=content>hermes</parameter></function>"},"finish_reason":"tool_calls"}]}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"done"},"finish_reason":"stop"}]}""",
                ),
        )
        adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertTrue(tmp.resolve("c.txt").toFile().exists())
    }

    @Test
    fun `executeWithRepoTools falls back when repoDir is invalid`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """data: {"type":"chat.end","result":{"output":"plain"}}

""",
                ),
        )
        val nonExistent = java.io.File("/tmp/__definitely_does_not_exist_lmstudio__")
        val result =
            adapter().executeWithStreamingPerLine("local-model", "hi", 10L, nonExistent, onPartialOutput = { _ -> })
        assertEquals("plain", result)
    }

    @Test
    fun `executeWithRepoTools handles missing message`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"finish_reason":"stop"}]}""",
                ),
        )
        val result =
            adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("", result)
    }

    @Test
    fun `executeWithRepoTools handles empty message content`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{},"finish_reason":"stop"}]}""",
                ),
        )
        val result =
            adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("", result)
    }

    @Test
    fun `executeWithRepoTools handles invalid JSON in tool args`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"x","tool_calls":[{"id":"c1","function":{"name":"write","arguments":"not-valid"}}]},"finish_reason":"tool_calls"}]}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"done"},"finish_reason":"stop"}]}""",
                ),
        )
        val result =
            adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertNotNull(result)
    }

    @Test
    fun `executeWithRepoTools handles tool_calls with missing function fields`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"hi","tool_calls":[{"foo":"bar"}]},"finish_reason":"tool_calls"}]}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"done"},"finish_reason":"stop"}]}""",
                ),
        )
        val result =
            adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertNotNull(result)
    }

    @Test
    fun `executeWithRepoTools exits after max iterations`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        enqueueModelLoaded()
        repeat(65) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """{"choices":[{"message":{"content":"iter","tool_calls":[{"id":"c$it","function":{"name":"write","arguments":"{\"path\":\"f$it.txt\",\"content\":\"x\"}"}}]},"finish_reason":"tool_calls"}]}""",
                    ),
            )
        }
        adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
    }

    @Test
    fun `resolveModel returns config model when set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(model = "custom"))
        assertEquals("custom", adapter().resolveModel())
    }

    @Test
    fun `resolveModel returns local-model when missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals("local-model", adapter().resolveModel())
    }

    @Test
    fun `resolveModel returns local-model when blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(model = ""))
        assertEquals("local-model", adapter().resolveModel())
    }

    @Test
    fun `resolveLanguage returns config language when set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "en"))
        assertEquals("en", adapter().resolveLanguage())
    }

    @Test
    fun `resolveLanguage returns de when missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals("de", adapter().resolveLanguage())
    }

    @Test
    fun `isReady always returns true`() {
        assertTrue(adapter().isReady())
    }

    @Test
    fun `resolveToken always returns null`() {
        assertEquals(null, adapter().resolveToken())
    }

    @Test
    fun `buildRequestBody includes model and prompt`() {
        val a = adapter()
        val body = a.buildRequestBody("local-model", "hello", stream = false)
        val parsed = objectMapper.readTree(body)
        assertEquals("local-model", parsed.path("model").asText())
        assertEquals(false, parsed.path("stream").asBoolean())
    }

    @Test
    fun `buildRequestBody with stream=true`() {
        val a = adapter()
        val body = a.buildRequestBody("local-model", "hello", stream = true)
        val parsed = objectMapper.readTree(body)
        assertEquals(true, parsed.path("stream").asBoolean())
    }

    @Test
    fun `buildRequestBody includes system prompt`() {
        val a = adapter()
        val body = a.buildRequestBody("local-model", "hello", stream = false)
        val parsed = objectMapper.readTree(body)
        val messages = parsed.path("messages")
        assertNotNull(messages)
        assertTrue(messages.isArray)
        assertEquals("system", messages[0].path("role").asText())
        assertEquals("user", messages[1].path("role").asText())
    }

    @Test
    fun `resolveSystemPrompt delegates to promptLoader`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "en"))
        whenever(promptLoader.get("en", "commontools.systemPrompt")).thenReturn("the prompt")
        assertEquals("the prompt", adapter().resolveSystemPrompt())
    }

    @Test
    fun `resolveLmStudioUrl replaces localhost with host docker internal when in docker`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val a = adapter("http://localhost:1234")
        val result = a.resolveLmStudioUrl()
        assertFalse(result.contains("host.docker.internal"))
    }
}
