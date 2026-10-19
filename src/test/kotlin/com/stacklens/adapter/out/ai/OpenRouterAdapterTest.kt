package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.ai.OpenRouterProperties
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.http.HttpClient
import java.nio.file.Files

class OpenRouterAdapterTest {
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
            PromptContext(mapOf("commontools.systemPrompt" to "system prompt")),
        )
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
        language: String = "de",
    ) = AiConfig(openRouterApiKey = openRouterApiKey, model = model, language = language)

    @Test
    fun `resolveToken returns key from aiConfigRepo`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openRouterApiKey = "sk-or-from-db"))
        assertEquals("sk-or-from-db", adapter().resolveToken())
    }

    @Test
    fun `resolveToken returns null when config has no key`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openRouterApiKey = ""))
        assertNull(adapter().resolveToken())
    }

    @Test
    fun `resolveToken returns null when no config exists`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertNull(adapter().resolveToken())
    }

    @Test
    fun `isReady returns true when token is configured`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openRouterApiKey = "sk-or-test"))
        assertTrue(adapter().isReady())
    }

    @Test
    fun `isReady returns false when no token available`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertFalse(adapter().isReady())
    }

    @Test
    fun `resolveModel returns config model when set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(model = "anthropic/claude-3-opus"))
        assertEquals("anthropic/claude-3-opus", adapter().resolveModel())
    }

    @Test
    fun `resolveModel returns default openai-gpt-4_1 when missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals("openai/gpt-4.1", adapter().resolveModel())
    }

    @Test
    fun `resolveModel returns default when config model is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(model = ""))
        assertEquals("openai/gpt-4.1", adapter().resolveModel())
    }

    @Test
    fun `resolveLanguage returns config language when set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "en"))
        assertEquals("en", adapter().resolveLanguage())
    }

    @Test
    fun `resolveLanguage returns default de when missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals("de", adapter().resolveLanguage())
    }

    @Test
    fun `execute throws when no token configured`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertThrows(IllegalStateException::class.java) {
            adapter().execute("openai/gpt-4.1", "hello")
        }
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
    fun `execute returns empty string when content missing`() {
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
    fun `execute returns empty string when choices empty`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openRouterApiKey = "sk-or-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[]}"""),
        )
        val result = adapter().execute("openai/gpt-4.1", "hello")
        assertEquals("", result)
    }

    @Test
    fun `execute throws on HTTP 500`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openRouterApiKey = "sk-or-test"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("openai/gpt-4.1", "hello")
            }
        assertTrue(ex.message!!.contains("500"))
    }

    @Test
    fun `executeWithStreaming forwards result to callback`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openRouterApiKey = "sk-or-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"streamed"}}]}"""),
        )
        var captured: String? = null
        val result =
            adapter().executeWithStreaming("openai/gpt-4.1", "hi", onPartialOutput = { partial ->
                captured = partial
            })
        assertEquals("streamed", result)
        assertEquals("streamed", captured)
    }

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
        assertFalse(a.isNonToolCapableModel("anthropic/claude-3"))
    }

    @Test
    fun `resolveOpenAiBaseUrl-equivalent returns the constructor-provided base URL`() {
        assertNotNull(adapter("http://custom.local"))
    }

    @Test
    fun `executeWithStreamingPerLine default-timeout delegates to long form`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openRouterApiKey = "sk-or-test"))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"hi"}}]}"""),
        )
        val a = adapter()
        val captured = StringBuilder()
        val result = a.executeWithStreamingPerLine("model", "prompt", onPartialOutput = { captured.append(it) })
        assertEquals("hi", result)
        assertEquals("hi", captured.toString())
    }

    @Test
    fun `executeWithStreamingPerLine logs markdown-extract info when files are written`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openRouterApiKey = "sk-or-test"))
        val md = "```kotlin\n// ```path: src/main/kotlin/Foo.kt\npackage foo\n```\n"
        val json = """{"choices":[{"message":{"content":${objectMapper.writeValueAsString(md)}}}]}"""
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(json),
        )
        val tmpDir = Files.createTempDirectory("or-test-").toFile()
        try {
            val a = adapter()
            a.executeWithStreamingPerLine("model", "prompt", 10L, tmpDir, onPartialOutput = { })
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `default httpVersion is HTTP_1_1 to avoid HTTP-2 RST_STREAM errors`() {
        assertEquals(HttpClient.Version.HTTP_1_1, OpenRouterProperties.DEFAULT_HTTP_VERSION)
    }

    @Test
    fun `httpClient uses the configured httpVersion HTTP_1_1 by default`() {
        assertSame(HttpClient.Version.HTTP_1_1, adapter().httpClient.version())
    }

    @Test
    fun `httpClient honors a custom httpVersion HTTP_2 override`() {
        val a =
            OpenRouterAdapter(
                defaultBaseUrl = "http://x",
                timeoutSeconds = 10L,
                aiConfigRepo = mock<AiConfigPort>(),
                objectMapper = ObjectMapper(),
                promptLoader = mock<PromptReaderPort>(),
                httpVersion = HttpClient.Version.HTTP_2,
            )
        assertSame(HttpClient.Version.HTTP_2, a.httpClient.version())
    }

    @Test
    fun `processToolLoopIteration returns false on invalid JSON`() {
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = "not-json",
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(r)
    }

    @Test
    fun `processToolLoopIteration returns false when choices missing`() {
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = "{}",
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(r)
    }

    @Test
    fun `processToolLoopIteration returns false when first choice not a map`() {
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices":[42]}""",
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(r)
    }

    @Test
    fun `processToolLoopIteration returns false when message is not a map`() {
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices":[{"message":"x"}]}""",
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(r)
    }

    @Test
    fun `processToolLoopIteration appends content to allText and breaks on stop`() {
        val allText = StringBuilder()
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices":[{"message":{"content":"hello"},"finish_reason":"stop"}]}""",
                messages = mutableListOf(),
                allText = allText,
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(r)
        assertTrue(allText.toString().contains("hello"))
    }

    @Test
    fun `processToolLoopIteration handles non-string content`() {
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices":[{"message":{"content":42},"finish_reason":"stop"}]}""",
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(r)
    }

    @Test
    fun `processToolLoopIteration handles non-list tool_calls as empty`() {
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices":[{"message":{"content":"x","tool_calls":"x"},"finish_reason":"stop"}]}""",
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(r)
    }

    @Test
    fun `processToolLoopIteration continues with tool calls and finish_reason tool_calls`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("or-tc")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":[{"id":"c1","function":{"name":"read_file","arguments":"{\"path\":\"x\"}"}}]},"finish_reason":"tool_calls"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = repo,
                onPartialOutput = {},
            )
            assertTrue(messages.isNotEmpty())
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration handles non-map tool_call element`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("or-bad-tc")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":["x"]},"finish_reason":"tool_calls"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = repo,
                onPartialOutput = {},
            )
            assertTrue(messages.none { it["role"] == "tool" })
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration handles tool_call with non-map function`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("or-bad-fn")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":[{"id":"c1","function":"x"}]},"finish_reason":"tool_calls"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = repo,
                onPartialOutput = {},
            )
            assertTrue(messages.none { it["role"] == "tool" })
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration handles non-string function name`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("or-bad-name")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":[{"id":"c1","function":{"name":42,"arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = repo,
                onPartialOutput = {},
            )
            assertTrue(messages.none { it["role"] == "tool" })
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration handles non-string arguments fallback to empty map`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("or-bad-args")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":[{"id":"c1","function":{"name":"read_file","arguments":42}}]},"finish_reason":"tool_calls"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = repo,
                onPartialOutput = {},
            )
            assertTrue(messages.isNotEmpty())
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration handles invalid args JSON via emptyMap fallback`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("or-invalid-args")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":[{"id":"c1","function":{"name":"read_file","arguments":"not-valid-json"}}]},"finish_reason":"tool_calls"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = repo,
                onPartialOutput = {},
            )
            assertTrue(messages.isNotEmpty())
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration uses default call id when id missing`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("or-call-id")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter().processToolLoopIteration(
                iteration = 9,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":[{"function":{"name":"read_file","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = repo,
                onPartialOutput = {},
            )
            val toolMsg = messages.find { it["role"] == "tool" }
            assertNotNull(toolMsg)
            assertEquals("call_9", toolMsg!!["tool_call_id"])
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `ChatTransport send delegates to sendToolRequest`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"hello"}}]}"""),
        )
        val a = adapter()
        val response = a.toolLoop.transport.send("""{"model":"m","messages":[]}""", 10L)
        assertTrue(response.contains("hello"))
    }

    @Test
    fun `ChatTransport send errors when the OpenRouter API key is missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(openRouterApiKey = null as String?, model = "openai/gpt-4.1", language = "de"),
        )
        val a = adapter()
        val ex =
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
                a.toolLoop.transport.send("""{"model":"m","messages":[]}""", 10L)
            }
        assertTrue(ex.message!!.contains("OpenRouter API key"))
    }
}
