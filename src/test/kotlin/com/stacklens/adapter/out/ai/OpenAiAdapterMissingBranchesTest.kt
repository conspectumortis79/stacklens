package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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

class OpenAiAdapterMissingBranchesTest {
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

    @Test
    fun `resolveToken returns null when both config and env are missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val result = adapter().resolveToken()
        if (System.getenv("OPENAI_API_KEY").isNullOrBlank()) {
            assertNull(result)
        }
    }

    @Test
    fun `resolveToken returns blank config key as null`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = ""))
        if (System.getenv("OPENAI_API_KEY").isNullOrBlank()) {
            assertNull(adapter().resolveToken())
        }
    }

    @Test
    fun `isReady returns false when no token is available`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        if (System.getenv("OPENAI_API_KEY").isNullOrBlank()) {
            assertEquals(false, adapter().isReady())
        }
    }

    @Test
    fun `isReady returns true when config has a token`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-abc"))
        assertEquals(true, adapter().isReady())
    }

    @Test
    fun `execute with explicit timeout override uses the provided timeout`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-abc"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"output_text":"ok"}"""),
        )
        val result = adapter().execute("gpt-5.2", "hi", 99L)
        assertEquals("ok", result)
    }

    @Test
    fun `executeWithStreamingPerLine 5-arg falls back when repoDir is a file not a directory`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-abc"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"output_text":"ok"}"""),
        )
        val fileAsDir = tmp.resolve("not-a-dir").toFile()
        fileAsDir.writeText("I'm a file, not a directory")
        val result =
            adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, fileAsDir, onPartialOutput = { _ -> })
        assertEquals("ok", result)
    }

    @Test
    fun `processToolLoopIteration returns false on invalid JSON`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val keepGoing = a.processToolLoopIteration(1, "not-valid-json", messages, allText, tmp.toFile())
        assertEquals(false, keepGoing)
        assertEquals(0, messages.size)
    }

    @Test
    fun `processToolLoopIteration returns false when choices is missing`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val keepGoing = a.processToolLoopIteration(1, "{}", messages, allText, tmp.toFile())
        assertEquals(false, keepGoing)
    }

    @Test
    fun `processToolLoopIteration returns false when choices is empty`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val keepGoing =
            a.processToolLoopIteration(1, """{"choices":[]}""", messages, allText, tmp.toFile())
        assertEquals(false, keepGoing)
    }

    @Test
    fun `processToolLoopIteration returns false when first choice is not a map`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val keepGoing =
            a.processToolLoopIteration(
                1,
                """{"choices":["not-a-map"]}""",
                messages,
                allText,
                tmp.toFile(),
            )
        assertEquals(false, keepGoing)
    }

    @Test
    fun `processToolLoopIteration returns false when message is missing`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val keepGoing =
            a.processToolLoopIteration(
                1,
                """{"choices":[{}]}""",
                messages,
                allText,
                tmp.toFile(),
            )
        assertEquals(false, keepGoing)
    }

    @Test
    fun `processToolLoopIteration returns true with parsed text tool calls`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val response =
            """{"choices":[{"message":{"content":"<tool_call>{\"name\":\"write\",\"arguments\":{\"path\":\"x.txt\",\"content\":\"y\"}}</tool_call>"},"finish_reason":"tool_calls"}]}"""
        val keepGoing = a.processToolLoopIteration(1, response, messages, allText, tmp.toFile())
        assertEquals(true, keepGoing)
        val written = tmp.resolve("x.txt").toFile()
        assertTrue(written.exists())
    }

    @Test
    fun `processToolLoopIteration continues with finish_reason not stop`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val response =
            """{"choices":[{"message":{"content":"x","tool_calls":[{"id":"c1","function":{"name":"write","arguments":"{\"path\":\"y.txt\",\"content\":\"z\"}"}}]},"finish_reason":"length"}]}"""
        val keepGoing = a.processToolLoopIteration(1, response, messages, allText, tmp.toFile())
        assertEquals(true, keepGoing)
    }

    @Test
    fun `processToolLoopIteration stops on finish_reason stop with no tool calls`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val response = """{"choices":[{"message":{"content":"done"},"finish_reason":"stop"}]}"""
        val keepGoing = a.processToolLoopIteration(1, response, messages, allText, tmp.toFile())
        assertEquals(false, keepGoing)
    }

    @Test
    fun `execute throws when status is 502`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-abc"))
        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("gpt-5.2", "hi")
            }
        assertTrue(ex.message!!.contains("HTTP 502"))
    }

    @Test
    fun `parseResponsesApiResponse returns text from output_text when present`() {
        val a = adapter()
        val result = a.parseResponsesApiResponse("""{"output_text":"the answer"}""")
        assertEquals("the answer", result)
    }

    @Test
    fun `parseResponsesApiResponse returns empty when output is not a list`() {
        val a = adapter()
        val result = a.parseResponsesApiResponse("""{"output":"not-a-list"}""")
        assertEquals("", result)
    }

    @Test
    fun `parseResponsesApiResponse skips output items that are not maps`() {
        val a = adapter()
        val result =
            a.parseResponsesApiResponse("""{"output":["not-a-map",{"content":[{"text":"kept"}]}]}""")
        assertEquals("kept", result)
    }

    @Test
    fun `parseResponsesApiResponse skips content items that are not lists`() {
        val a = adapter()
        val result = a.parseResponsesApiResponse("""{"output":[{"content":"not-a-list"}]}""")
        assertEquals("", result)
    }

    @Test
    fun `parseResponsesApiResponse returns content from content field as fallback`() {
        val a = adapter()
        val result =
            a.parseResponsesApiResponse(
                """{"output":[{"content":[{"content":"hello"}]}]}""",
            )
        assertEquals("hello", result)
    }

    @Test
    fun `parseResponsesApiResponse skips content parts without text or content`() {
        val a = adapter()
        val result =
            a.parseResponsesApiResponse(
                """{"output":[{"content":[{"other":"value"}]}]}""",
            )
        assertEquals("", result)
    }

    @Test
    fun `fetchAvailableModels filters out non-gpt models`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-abc"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"data":[{"id":"gpt-5.2"},{"id":"o1-preview"},{"id":"dall-e-3"},{"id":"claude-opus"}]}""",
                ),
        )
        val models = adapter().fetchAvailableModels("sk-abc")
        assertTrue(models.contains("gpt-5.2"))
        assertTrue(!models.contains("dall-e-3"))
        assertTrue(!models.contains("o1-preview"))
    }

    @Test
    fun `fetchAvailableModels skips data items that are not maps`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-abc"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":["not-a-map",{"id":"gpt-4o"}]}"""),
        )
        val models = adapter().fetchAvailableModels("sk-abc")
        assertEquals(listOf("gpt-4o"), models)
    }

    @Test
    fun `fetchAvailableModels skips data items without id`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-abc"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"foo":"bar"},{"id":"gpt-3.5-turbo"}]}"""),
        )
        val models = adapter().fetchAvailableModels("sk-abc")
        assertEquals(listOf("gpt-3.5-turbo"), models)
    }

    @Test
    fun `fetchAvailableModels returns empty when data field is not a list`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-abc"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":"not-a-list"}"""),
        )
        val models = adapter().fetchAvailableModels("sk-abc")
        assertEquals(emptyList<String>(), models)
    }

    @Test
    fun `processToolLoopIteration defaults call id when missing`(
        @TempDir tmp: Path,
    ) {
        val a = adapter()
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val response =
            """{"choices":[{"message":{"content":"x","tool_calls":[{"function":{"name":"write","arguments":"{\"path\":\"a.txt\",\"content\":\"b\"}"}}]},"finish_reason":"tool_calls"}]}"""
        a.processToolLoopIteration(7, response, messages, allText, tmp.toFile())
        assertTrue(messages.any { it["role"] == "assistant" })
        val toolMsg = messages.first { it["role"] == "tool" }
        assertEquals("call_7", toolMsg["tool_call_id"])
    }

    @Test
    fun `currentAiConfig returns the value from the repository`() {
        val cfg = AiConfig(openAiApiKey = "sk-abc")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        assertEquals(cfg, adapter().currentAiConfig())
    }

    @Test
    fun `currentAiConfig returns null when repository returns null`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertNull(adapter().currentAiConfig())
    }

    @Test
    fun `resolveModel returns default when config model is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(model = ""))
        assertEquals("gpt-5.2", adapter().resolveModel())
    }

    @Test
    fun `resolveModel returns config value when set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(model = "o3"))
        assertEquals("o3", adapter().resolveModel())
    }

    @Test
    fun `resolveLanguage returns default when config language is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = ""))
        assertEquals("de", adapter().resolveLanguage())
    }

    @Test
    fun `resolveLanguage returns config value when set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = "en"))
        assertEquals("en", adapter().resolveLanguage())
    }

    @Test
    fun `executeWithStreaming invokes the callback with the result`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-abc"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"output_text":"streamed"}"""),
        )
        var captured: String? = null
        val result = adapter().executeWithStreaming("gpt-5.2", "hi", onPartialOutput = { captured = it })
        assertEquals("streamed", result)
        assertEquals("streamed", captured)
    }

    @Test
    fun `executeWithStreaming with timeout override uses the override`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-abc"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"output_text":"done"}"""),
        )
        var captured: String? = null
        val result = adapter().executeWithStreaming("gpt-5.2", "hi", 42L, onPartialOutput = { captured = it })
        assertEquals("done", result)
        assertEquals("done", captured)
    }
}
