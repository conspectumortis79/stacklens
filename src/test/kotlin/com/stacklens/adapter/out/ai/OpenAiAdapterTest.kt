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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenAiAdapterTest {
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
        whenever(promptLoader.get(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn("system prompt")
        whenever(promptLoader.context(org.mockito.kotlin.any())).thenReturn(
            com.stacklens.application.port.out.prompt
                .PromptContext(mapOf("commontools.systemPrompt" to "system prompt")),
        )
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
        openAiApiKey: String = "sk-test",
        model: String = "gpt-5.2",
        language: String = "de",
        openAiReasoningEffort: String = "medium",
    ): AiConfig {
        val cfg = AiConfig(openAiApiKey = openAiApiKey, model = model, language = language)
        cfg.openAiReasoningEffort = openAiReasoningEffort
        return cfg
    }

    @Test
    fun `resolveToken returns key from aiConfigRepo`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiApiKey = "sk-from-db"))
        assertEquals("sk-from-db", adapter().resolveToken())
    }

    @Test
    fun `resolveToken returns null when config has no key and no env var`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiApiKey = ""))
        assertNull(adapter().resolveToken())
    }

    @Test
    fun `resolveToken returns null when no config exists`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertNull(adapter().resolveToken())
    }

    @Test
    fun `resolveToken skips blank key in config`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiApiKey = "   "))
        assertNull(adapter().resolveToken())
    }

    @Test
    fun `isReady returns true when token is configured`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiApiKey = "sk-test"))
        assertTrue(adapter().isReady())
    }

    @Test
    fun `isReady returns false when no token available`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertFalse(adapter().isReady())
    }

    @Test
    fun `resolveModel returns config model when set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(model = "gpt-4o"))
        assertEquals("gpt-4o", adapter().resolveModel())
    }

    @Test
    fun `resolveModel returns default gpt-5_2 when config is missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals("gpt-5.2", adapter().resolveModel())
    }

    @Test
    fun `resolveModel returns default when config model is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(model = ""))
        assertEquals("gpt-5.2", adapter().resolveModel())
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
            adapter().execute("gpt-5.2", "hello")
        }
    }

    @Test
    fun `execute returns response text on success`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiApiKey = "sk-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"output_text":"the answer","output":[{"content":[{"text":"the answer"}]}]}""",
                ),
        )
        val result = adapter().execute("gpt-5.2", "hello")
        assertEquals("the answer", result)
    }

    @Test
    fun `execute returns fallback text when output_text is missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiApiKey = "sk-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"output":[{"content":[{"text":"fallback"}]}]}"""),
        )
        val result = adapter().execute("gpt-5.2", "hello")
        assertEquals("fallback", result)
    }

    @Test
    fun `execute throws on HTTP 500`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiApiKey = "sk-test"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("gpt-5.2", "hello")
            }
        assertTrue(ex.message!!.contains("HTTP 500"))
    }

    @Test
    fun `executeWithStreaming forwards result to callback`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiApiKey = "sk-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"output_text":"streamed","output":[{"content":[{"text":"streamed"}]}]}""",
                ),
        )
        var captured: String? = null
        val result =
            adapter().executeWithStreaming("gpt-5.2", "hi", onPartialOutput = { partial ->
                captured = partial
            })
        assertEquals("streamed", result)
        assertEquals("streamed", captured)
    }

    @Test
    fun `executeWithStreamingPerLine falls back when repoDir does not exist`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiApiKey = "sk-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"output_text":"plain","output":[{"content":[{"text":"plain"}]}]}"""),
        )
        val nonExistentDir = java.io.File("/tmp/__nonexistent_dir_for_test__")
        var captured: String? = null
        val result =
            adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, nonExistentDir, onPartialOutput = { partial ->
                captured = partial
            })
        assertEquals("plain", result)
        assertEquals("plain", captured)
    }

    @Test
    fun `resolveOpenAiBaseUrl returns the constructor-provided base URL`() {
        assertNotNull(adapter("http://custom.local"))
    }

    @Test
    fun `executeWithStreaming delegates to executeWithStreamingPerLine when no repoDir`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(openAiApiKey = "sk-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"output_text":"x","output":[{"content":[{"text":"x"}]}]}"""),
        )
        val r = adapter().executeWithStreaming("gpt-5.2", "hi", onPartialOutput = { })
        assertEquals("x", r)
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
            )
        assertFalse(r)
    }

    @Test
    fun `processToolLoopIteration returns false when first choice is not a map`() {
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices":[42]}""",
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = java.io.File("."),
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
            )
        assertFalse(r)
    }

    @Test
    fun `processToolLoopIteration appends content to allText when no tool calls`() {
        val allText = StringBuilder()
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices":[{"message":{"content":"hello"},"finish_reason":"stop"}]}""",
                messages = mutableListOf(),
                allText = allText,
                repoDir = java.io.File("."),
            )
        assertFalse(r)
        assertTrue(allText.toString().contains("hello"))
    }

    @Test
    fun `processToolLoopIteration calls onPartialOutput with accumulated text`() {
        val allText = StringBuilder()
        var captured: String? = null
        adapter().processToolLoopIteration(
            iteration = 1,
            respJson = """{"choices":[{"message":{"content":"hello"},"finish_reason":"stop"}]}""",
            messages = mutableListOf(),
            allText = allText,
            repoDir = java.io.File("."),
            onPartialOutput = { captured = it },
        )
        assertEquals("hello", captured)
    }

    @Test
    fun `processToolLoopIteration handles content as non-string`() {
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices":[{"message":{"content":42},"finish_reason":"stop"}]}""",
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = java.io.File("."),
            )
        assertFalse(r)
    }

    @Test
    fun `processToolLoopIteration handles tool_calls as non-list`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("openai-nonlist")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":"not-a-list"},"finish_reason":"tool_calls"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = repo,
            )
            assertTrue(messages.isEmpty())
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration returns true when tool calls present and not stop`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("openai-tc")
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
            )
            assertTrue(messages.any { it["role"] == "assistant" })
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration handles finish_reason stop with tool calls present`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("openai-stop")
                .toFile()
        try {
            val r =
                adapter().processToolLoopIteration(
                    iteration = 1,
                    respJson =
                        """{"choices":[{"message":{"content":"final","tool_calls":[{"id":"c1","function":{"name":"read_file","arguments":"{}"}}]},"finish_reason":"stop"}]}""",
                    messages = mutableListOf(),
                    allText = StringBuilder(),
                    repoDir = repo,
                )
            assertFalse(r)
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration handles non-map tool_call element`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("openai-bad-tc")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":["not-a-map"]},"finish_reason":"tool_calls"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = repo,
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
                .createTempDirectory("openai-bad-fn")
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
                .createTempDirectory("openai-bad-name")
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
                .createTempDirectory("openai-bad-args")
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
                .createTempDirectory("openai-invalid-args")
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
            )
            assertTrue(messages.isNotEmpty())
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration uses default call id when tool_call id missing`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("openai-call-id")
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
            )
            val toolMsg = messages.find { it["role"] == "tool" }
            assertNotNull(toolMsg)
            assertEquals("call_9", toolMsg!!["tool_call_id"])
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration parses text-form tool calls when structured field is empty`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("openai-text-tool")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            val r =
                adapter().processToolLoopIteration(
                    iteration = 1,
                    respJson =
                        """{"choices":[{"message":{"content":"<tool>read_file</tool>{}"},"finish_reason":"tool_calls"}]}""",
                    messages = messages,
                    allText = StringBuilder(),
                    repoDir = repo,
                )
            assertNotNull(r)
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
}
