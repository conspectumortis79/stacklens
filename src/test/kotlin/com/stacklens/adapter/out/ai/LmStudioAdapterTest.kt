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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LmStudioAdapterTest {
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

    private fun adapter(url: String = server.url("/").toString().trimEnd('/')) =
        LmStudioAdapter(
            defaultLmStudioUrl = url,
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
        )

    private fun aiConfig(
        lmStudioUrl: String = "",
        model: String = "local-model",
        language: String = "de",
    ) = AiConfig(lmStudioUrl = lmStudioUrl, model = model, language = language)

    @Test
    fun `resolveToken returns null since LM Studio has no auth`() {
        assertNull(adapter().resolveToken())
    }

    @Test
    fun `isReady returns true even when no config exists`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertTrue(adapter().isReady())
    }

    @Test
    fun `resolveModel returns config model when set`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(model = "qwen2.5-coder"))
        assertEquals("qwen2.5-coder", adapter().resolveModel())
    }

    @Test
    fun `resolveModel returns default local-model when missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals("local-model", adapter().resolveModel())
    }

    @Test
    fun `resolveModel returns default when config model is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(model = ""))
        assertEquals("local-model", adapter().resolveModel())
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
    fun `resolveLanguage returns default de when language is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = ""))
        assertEquals("de", adapter().resolveLanguage())
    }

    @Test
    fun `resolveLanguage returns default de when language is whitespace`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "   "))
        assertEquals("de", adapter().resolveLanguage())
    }

    @Test
    fun `resolveLmStudioUrl uses config URL when set and not blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(lmStudioUrl = "http://custom:1234"))
        assertEquals("http://custom:1234", adapter().resolveLmStudioUrl())
    }

    @Test
    fun `resolveLmStudioUrl falls back to default when config URL is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(lmStudioUrl = ""))
        val default = server.url("/").toString().trimEnd('/')
        assertEquals(default, adapter().resolveLmStudioUrl())
    }

    @Test
    fun `resolveLmStudioUrl falls back to default when no config exists`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val default = server.url("/").toString().trimEnd('/')
        assertEquals(default, adapter().resolveLmStudioUrl())
    }

    @Test
    fun `execute returns completion response on success`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"the answer"}}]}""",
                ),
        )
        val result = adapter().execute("local-model", "hello")
        assertEquals("the answer", result)
    }

    @Test
    fun `execute returns empty string when content missing`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{}}]}"""),
        )
        val result = adapter().execute("local-model", "hello")
        assertEquals("", result)
    }

    @Test
    fun `executeWithStreamingPerLine 4-arg overload delegates to streaming`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"via-4-arg"}}]}"""),
        )
        val r =
            adapter().executeWithStreamingPerLine("local-model", "hi", onPartialOutput = { _ ->
            })
        assertNotNull(r)
    }

    @Test
    fun `executeWithStreaming invokes api client and returns the result`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"via-streaming"}}]}"""),
        )
        val r =
            adapter().executeWithStreaming("local-model", "hi", onPartialOutput = { _ ->
            })
        assertNotNull(r)
    }

    @Test
    fun `executeWithStreamingPerLine 5-arg falls back when repoDir does not exist`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"via-fallback"}}]}"""),
        )
        val nonExistentDir = java.io.File("/tmp/__nonexistent_dir_for_test__")
        val r =
            adapter().executeWithStreamingPerLine("local-model", "hi", 10L, nonExistentDir, onPartialOutput = { _ ->
            })
        assertNotNull(r)
    }

    @Test
    fun `executeWithStreamingPerLine 5-arg falls back when repoDir is not a directory`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"via-fallback"}}]}"""),
        )
        val tempFile = java.io.File.createTempFile("not-a-dir-", ".txt")
        tempFile.deleteOnExit()
        val r =
            adapter().executeWithStreamingPerLine("local-model", "hi", 10L, tempFile, onPartialOutput = { _ ->
            })
        assertNotNull(r)
    }

    @Test
    fun `constructor accepts custom base URL`() {
        assertNotNull(adapter("http://custom.local:1234"))
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
    fun `processToolLoopIteration returns false when choices is not a list`() {
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices":"x"}""",
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
    fun `processToolLoopIteration appends content and breaks on stop`() {
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
    fun `processToolLoopIteration handles non-string content as empty`() {
        val allText = StringBuilder()
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices":[{"message":{"content":42},"finish_reason":"stop"}]}""",
                messages = mutableListOf(),
                allText = allText,
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(r)
    }

    @Test
    fun `processToolLoopIteration handles non-list tool_calls as empty`() {
        val messages = mutableListOf<Map<String, Any>>()
        val r =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices":[{"message":{"content":"x","tool_calls":"x"},"finish_reason":"stop"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(r)
    }

    @Test
    fun `processToolLoopIteration continues with tool calls present and finish_reason tool_calls`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("lmstudio-tc")
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
    fun `processToolLoopIteration stops on finish_reason stop even with text-form tool calls`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("lmstudio-stop")
                .toFile()
        try {
            val r =
                adapter().processToolLoopIteration(
                    iteration = 1,
                    respJson =
                        """{"choices":[{"message":{"content":"<tool>read_file</tool>","tool_calls":[]},"finish_reason":"stop"}]}""",
                    messages = mutableListOf(),
                    allText = StringBuilder(),
                    repoDir = repo,
                    onPartialOutput = {},
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
                .createTempDirectory("lmstudio-bad-tc")
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
                .createTempDirectory("lmstudio-bad-fn")
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
                .createTempDirectory("lmstudio-bad-name")
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
                .createTempDirectory("lmstudio-bad-args")
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
    fun `processToolLoopIteration uses default call id when id missing`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("lmstudio-call-id")
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
    fun `ChatTransport send delegates to sendRequest and is exercised directly`() {
        val a = adapter()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"models":[{"key":"local-model","loaded_instances":[{"id":"i1"}]}]}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"hello"}}]}"""),
        )
        val response = a.toolLoop.transport.send("""{"model":"local-model","messages":[]}""", 10L)
        assertTrue(response.contains("hello"))
    }

    @Test
    fun `ChatTransport send loads the model when it is not yet loaded`() {
        val a = adapter()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"models":[]}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"loaded"}}]}"""),
        )
        val response = a.toolLoop.transport.send("""{"model":"local-model","messages":[]}""", 10L)
        assertTrue(response.contains("loaded"))
    }
}
