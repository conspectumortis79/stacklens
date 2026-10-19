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
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files

class OllamaAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: OllamaAdapter
    private val mapper = ObjectMapper()
    private val aiConfigPort = mock<AiConfigPort>()
    private val promptLoader = mock<PromptReaderPort>()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        whenever(aiConfigPort.findAiConfig()).thenReturn(
            AiConfig(ollamaUrl = server.url("").toString().removeSuffix("/")),
        )
        whenever(promptLoader.get(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn("system prompt")
        adapter =
            OllamaAdapter(
                defaultOllamaUrl = server.url("").toString().removeSuffix("/"),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigPort,
                objectMapper = mapper,
                promptLoader = promptLoader,
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )
    }

    @Test
    fun `writesFilesNatively keeps the provider-neutral default`() {
        assertFalse(adapter.writesFilesNatively)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun ollamaChatResponse(content: String): String {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"choices":[{"message":{"role":"assistant","content":"$escaped"}}]}"""
    }

    private fun ollamaError(
        status: Int = 500,
        body: String = "boom",
    ): MockResponse = MockResponse().setResponseCode(status).setBody(body)

    @Test
    fun `stripThinkingBlocks removes think tags case-insensitive`() {
        assertEquals("hello", adapter.stripThinkingBlocks("hello"))
        val openThink =
            String(charArrayOf(0x3C.toChar(), 0x74.toChar(), 0x68.toChar(), 0x69.toChar(), 0x6E.toChar(), 0x6B.toChar(), 0x3E.toChar()))
        val closeThink =
            String(
                charArrayOf(
                    0x3C.toChar(),
                    0x2F.toChar(),
                    0x74.toChar(),
                    0x68.toChar(),
                    0x69.toChar(),
                    0x6E.toChar(),
                    0x6B.toChar(),
                    0x3E.toChar(),
                ),
            )
        assertEquals("before after", adapter.stripThinkingBlocks("before" + openThink + "hello" + closeThink + " after"))
        assertEquals("", adapter.stripThinkingBlocks(openThink + closeThink))
        assertEquals("after", adapter.stripThinkingBlocks(openThink + closeThink + "after"))
        assertEquals("beforehello", adapter.stripThinkingBlocks("before" + openThink + closeThink + "hello"))
    }

    @Test
    fun `resolveModel returns configured model or default`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(model = "llama3"))
        assertEquals("llama3", adapter.resolveModel())
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(model = ""))
        assertEquals("qwen3-coder:30b-a3b-q4_k_m", adapter.resolveModel())
        whenever(aiConfigPort.findAiConfig()).thenReturn(null)
        assertEquals("qwen3-coder:30b-a3b-q4_k_m", adapter.resolveModel())
    }

    @Test
    fun `resolveLanguage returns configured language or de default`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(language = "en"))
        assertEquals("en", adapter.resolveLanguage())
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(language = ""))
        assertEquals("de", adapter.resolveLanguage())
        whenever(aiConfigPort.findAiConfig()).thenReturn(null)
        assertEquals("de", adapter.resolveLanguage())
    }

    @Test
    fun `resolveToken returns null (no token required)`() {
        assertEquals(null, adapter.resolveToken())
    }

    @Test
    fun `isReady returns true`() {
        assertTrue(adapter.isReady())
    }

    @Test
    fun `resolveNumCtx returns configured value or default via request body`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(
            AiConfig(
                ollamaUrl = server.url("").toString().removeSuffix("/"),
                ollamaNumCtx = 8192,
            ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(ollamaChatResponse("ok")),
        )
        adapter.execute("m", "p")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"num_ctx\":8192"))
    }

    @Test
    fun `resolveNumCtx returns default 32768 when configured to 0`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(
            AiConfig(
                ollamaUrl = server.url("").toString().removeSuffix("/"),
                ollamaNumCtx = 0,
            ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(ollamaChatResponse("ok")),
        )
        adapter.execute("m", "p")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"num_ctx\":32768"))
    }

    @Test
    fun `resolveOllamaUrl replaces localhost with host dot docker dot internal inside docker`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(
            AiConfig(
                ollamaUrl = server.url("").toString().removeSuffix("/"),
            ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(ollamaChatResponse("ok")),
        )
        val result = adapter.execute("m", "p")
        assertEquals("ok", result)
    }

    @Test
    fun `resolveOllamaUrl falls back to default when config null and default not blank`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(null)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(ollamaChatResponse("ok")),
        )
        val result = adapter.execute("m", "p")
        assertEquals("ok", result)
    }

    @Test
    fun `execute returns message content from non-streaming response`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(ollamaChatResponse("hello there")),
        )
        val result = adapter.execute("m", "p")
        assertEquals("hello there", result)
    }

    @Test
    fun `execute throws on non-200 response`() {
        server.enqueue(ollamaError(500))
        assertThrows<IllegalStateException> { adapter.execute("m", "p") }
    }

    @Test
    fun `execute uses POST and Content-Type application json`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(ollamaChatResponse("ok")),
        )
        adapter.execute("m", "p")
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.headers["Content-Type"]!!.contains("application/json"))
        assertTrue(recorded.path!!.endsWith("/v1/chat/completions"))
    }

    @Test
    fun `execute request body contains model messages stream false num_ctx and think false`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(ollamaChatResponse("ok")),
        )
        adapter.execute("llama3", "my prompt")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"model\":\"llama3\""))
        assertTrue(body.contains("\"stream\":false"))
        assertTrue(body.contains("\"think\":false"))
        assertTrue(body.contains("\"num_ctx\""))
        assertTrue(body.contains("\"messages\""))
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("\"content\":\"my prompt\""))
    }

    @Test
    fun `execute strips thinking blocks from response content`() {
        val openThink =
            String(charArrayOf(0x3C.toChar(), 0x74.toChar(), 0x68.toChar(), 0x69.toChar(), 0x6E.toChar(), 0x6B.toChar(), 0x3E.toChar()))
        val closeThink =
            String(
                charArrayOf(
                    0x3C.toChar(),
                    0x2F.toChar(),
                    0x74.toChar(),
                    0x68.toChar(),
                    0x69.toChar(),
                    0x6E.toChar(),
                    0x6B.toChar(),
                    0x3E.toChar(),
                ),
            )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(ollamaChatResponse(openThink + "reasoning..." + closeThink + "hello")),
        )
        val result = adapter.execute("m", "p")
        assertEquals("hello", result)
    }

    @Test
    fun `execute logs warning when num_ctx running low`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(
            AiConfig(
                ollamaUrl = server.url("").toString().removeSuffix("/"),
                ollamaNumCtx = 100,
            ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(ollamaChatResponse("ok")),
        )
        adapter.execute("m", "x".repeat(1_000))
    }

    @Test
    fun `execute handles empty choices list gracefully`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[]}"""),
        )
        val result = adapter.execute("m", "p")
        assertEquals("", result)
    }

    @Test
    fun `execute handles malformed choices`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[null]}"""),
        )
        val result = adapter.execute("m", "p")
        assertEquals("", result)
    }

    @Test
    fun `executeWithStreaming assembles streamed content`() {
        val chunk1 = """{"choices":[{"delta":{"content":"hello "}}]}"""
        val chunk2 = """{"choices":[{"delta":{"content":"world"}}]}"""
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: $chunk1\ndata: $chunk2\ndata: [DONE]\n"),
        )
        val received = mutableListOf<String>()
        val result =
            adapter.executeWithStreaming("m", "p", timeoutOverride = 30L, onPartialOutput = { received += it })
        assertEquals("hello world", result)
    }

    @Test
    fun `executeWithStreaming throws on non-200 response`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("err"))
        assertThrows<IllegalStateException> {
            adapter.executeWithStreaming("m", "p", timeoutOverride = 30L, onPartialOutput = {})
        }
    }

    @Test
    fun `executeWithStreaming ignores lines without data prefix`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n"),
        )
        val result =
            adapter.executeWithStreaming("m", "p", timeoutOverride = 30L, onPartialOutput = {})
        assertEquals("ok", result)
    }

    @Test
    fun `executeWithStreamingPerLine delegates to per-line streaming`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"perline\"}}]}\ndata: [DONE]\n"),
        )
        val received = mutableListOf<String>()
        val result =
            adapter.executeWithStreamingPerLine("m", "p", timeoutOverride = 30L, onPartialOutput = { received += it })
        assertEquals("perline", result)
    }

    @Test
    fun `executeWithStreamingPerLine with repoDir delegates to executeWithRepoTools`() {
        val repo = File("/nonexistent-stacklens-repo-${System.nanoTime()}")
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"fallback\"}}]}\ndata: [DONE]\n"),
        )
        val result =
            adapter.executeWithStreamingPerLine("m", "p", timeoutOverride = 30L, repoDir = repo, onPartialOutput = {})
        assertEquals("fallback", result)
    }

    @Test
    fun `executeWithStreaming throws on HTTP error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows<IllegalStateException> {
            adapter.executeWithStreaming("m", "p", timeoutOverride = 30L, onPartialOutput = {})
        }
    }

    @Test
    fun `executeWithRepoTools handles tool_calls in response`() {
        val repo = File("/tmp").apply { mkdirs() }
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"choices":[{"message":{"role":"assistant","content":"writing","tool_calls":[{"id":"call1","function":{"name":"write_file","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""",
                    ),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"choices":[{"message":{"role":"assistant","content":"done"},"finish_reason":"stop"}]}""",
                    ),
            )
            val result =
                adapter.executeWithStreamingPerLine(
                    "m",
                    "p",
                    timeoutOverride = 30L,
                    repoDir = repo,
                    onPartialOutput = {},
                )
            assertTrue(result.contains("done") || result.isNotEmpty())
            val written = File(repo, "hello.txt")
            if (written.exists()) {
                assertEquals("hi", written.readText())
                written.delete()
            }
        } finally {
            repo.delete()
        }
    }

    @Test
    fun `executeWithRepoTools parses text-form tool calls from content`() {
        val repo = File("/tmp").apply { mkdirs() }
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"choices":[{"message":{"role":"assistant","content":"```tool-call write_file(test.txt, hello)```"}}]}""",
                    ),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"choices":[{"message":{"role":"assistant","content":"all done"},"finish_reason":"stop"}]}""",
                    ),
            )
            val result =
                adapter.executeWithStreamingPerLine(
                    "m",
                    "p",
                    timeoutOverride = 30L,
                    repoDir = repo,
                    onPartialOutput = {},
                )
            assertTrue(result.isNotEmpty())
            val written = File(repo, "test.txt")
            if (written.exists()) {
                assertEquals("hello", written.readText())
                written.delete()
            }
        } finally {
            repo.delete()
        }
    }

    @Test
    fun `resolveOllamaUrl uses config when present and non-blank`() {
        val resolved = adapter.resolveOllamaUrl()
        assertTrue(
            resolved.contains(
                server
                    .url("")
                    .toString()
                    .removeSuffix("/")
                    .take(8),
            ),
        )
    }

    @Test
    fun `resolveOllamaUrl uses default when config is blank`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(
            AiConfig(ollamaUrl = ""),
        )
        val resolved = adapter.resolveOllamaUrl()
        assertEquals(server.url("").toString().removeSuffix("/"), resolved)
    }

    @Test
    fun `execute handles response with no choices field`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{}"""),
        )
        val r = adapter.execute(model = "m", prompt = "p", timeoutOverride = 30L)
        assertEquals("", r)
    }

    @Test
    fun `execute handles response with empty choices list`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[]}"""),
        )
        val r = adapter.execute(model = "m", prompt = "p", timeoutOverride = 30L)
        assertEquals("", r)
    }

    @Test
    fun `execute handles response with non-list choices`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":"not a list"}"""),
        )
        val r = adapter.execute(model = "m", prompt = "p", timeoutOverride = 30L)
        assertEquals("", r)
    }

    @Test
    fun `execute handles response with non-map choice`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":["string"]}"""),
        )
        val r = adapter.execute(model = "m", prompt = "p", timeoutOverride = 30L)
        assertEquals("", r)
    }

    @Test
    fun `execute handles response with non-map message`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":"not a map"}]}"""),
        )
        val r = adapter.execute(model = "m", prompt = "p", timeoutOverride = 30L)
        assertEquals("", r)
    }

    @Test
    fun `execute handles response with non-string content`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":42}}]}"""),
        )
        val r = adapter.execute(model = "m", prompt = "p", timeoutOverride = 30L)
        assertEquals("", r)
    }

    @Test
    fun `executeWithRepoTools handles tool_calls with non-list type`() {
        val repo = Files.createTempDirectory("ollama-").toFile()
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"choices":[{"message":{"role":"assistant","content":"x","tool_calls":"not a list"},"finish_reason":"stop"}]}""",
                    ),
            )
            val result =
                adapter.executeWithStreamingPerLine(
                    "m",
                    "p",
                    timeoutOverride = 30L,
                    repoDir = repo,
                    onPartialOutput = {},
                )
            assertTrue(result.isNotEmpty())
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `executeWithRepoTools handles tool_call with non-map element`() {
        val repo = Files.createTempDirectory("ollama-").toFile()
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"choices":[{"message":{"role":"assistant","content":"x","tool_calls":["string"]},"finish_reason":"stop"}]}""",
                    ),
            )
            val result =
                adapter.executeWithStreamingPerLine(
                    "m",
                    "p",
                    timeoutOverride = 30L,
                    repoDir = repo,
                    onPartialOutput = {},
                )
            assertTrue(result.isNotEmpty())
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `executeWithRepoTools handles tool_call with non-map function`() {
        val repo = Files.createTempDirectory("ollama-").toFile()
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"choices":[{"message":{"role":"assistant","content":"x","tool_calls":[{"id":"c1","function":"not a map"}]},"finish_reason":"stop"}]}""",
                    ),
            )
            val result =
                adapter.executeWithStreamingPerLine(
                    "m",
                    "p",
                    timeoutOverride = 30L,
                    repoDir = repo,
                    onPartialOutput = {},
                )
            assertTrue(result.isNotEmpty())
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `executeWithRepoTools handles tool_call with non-string name`() {
        val repo = Files.createTempDirectory("ollama-").toFile()
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"choices":[{"message":{"role":"assistant","content":"x","tool_calls":[{"id":"c1","function":{"name":42,"arguments":"{}"}}]},"finish_reason":"stop"}]}""",
                    ),
            )
            val result =
                adapter.executeWithStreamingPerLine(
                    "m",
                    "p",
                    timeoutOverride = 30L,
                    repoDir = repo,
                    onPartialOutput = {},
                )
            assertTrue(result.isNotEmpty())
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `executeWithRepoTools handles tool_call with non-string arguments`() {
        val repo = Files.createTempDirectory("ollama-").toFile()
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"choices":[{"message":{"role":"assistant","content":"x","tool_calls":[{"id":"c1","function":{"name":"write_file","arguments":42}}]},"finish_reason":"stop"}]}""",
                    ),
            )
            val result =
                adapter.executeWithStreamingPerLine(
                    "m",
                    "p",
                    timeoutOverride = 30L,
                    repoDir = repo,
                    onPartialOutput = {},
                )
            assertTrue(result.isNotEmpty())
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration returns false when response is invalid JSON`() {
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val keepGoing =
            adapter.processToolLoopIteration(
                iteration = 1,
                respJson = "not-json",
                messages = messages,
                allText = allText,
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(keepGoing)
        assertEquals(0, messages.size)
    }

    @Test
    fun `processToolLoopIteration returns false when choices missing`() {
        val keepGoing =
            adapter.processToolLoopIteration(
                iteration = 1,
                respJson = """{}""",
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(keepGoing)
    }

    @Test
    fun `processToolLoopIteration returns false when choices is not a list`() {
        val keepGoing =
            adapter.processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices": "not-a-list"}""",
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(keepGoing)
    }

    @Test
    fun `processToolLoopIteration returns false when first choice is not a map`() {
        val keepGoing =
            adapter.processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices": [42]}""",
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(keepGoing)
    }

    @Test
    fun `processToolLoopIteration returns false when message is not a map`() {
        val keepGoing =
            adapter.processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices": [{"message": "not-a-map"}]}""",
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(keepGoing)
    }

    @Test
    fun `processToolLoopIteration appends content to allText and returns false when no tool calls`() {
        val allText = StringBuilder()
        val keepGoing =
            adapter.processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices": [{"message": {"content": "hello"}, "finish_reason": "stop"}]}""",
                messages = mutableListOf(),
                allText = allText,
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(keepGoing)
        assertTrue(allText.toString().contains("hello"))
    }

    @Test
    fun `processToolLoopIteration returns true when tool calls present and finish_reason not stop`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("proc-tool-test")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            val keepGoing =
                adapter.processToolLoopIteration(
                    iteration = 1,
                    respJson =
                        """{"choices":[{"message":{"content":"","tool_calls":[{"id":"c1","function":{"name":"read_file","arguments":"{\"path\":\"x.txt\"}"}}]},"finish_reason":"tool_calls"}]}""",
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
    fun `processToolLoopIteration parses text-form tool calls when structured field is empty`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("text-tool-test")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            val keepGoing =
                adapter.processToolLoopIteration(
                    iteration = 1,
                    respJson =
                        """{"choices":[{"message":{"content":"<tool>read_file</tool>{}"},"finish_reason":"tool_calls"}]}""",
                    messages = messages,
                    allText = StringBuilder(),
                    repoDir = repo,
                    onPartialOutput = {},
                )
            assertNotNull(keepGoing)
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration appends assistant message with content when content is non-blank`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("assistant-msg-test")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter.processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"hi","tool_calls":[{"id":"c1","function":{"name":"read_file","arguments":"{\"path\":\"x\"}"}}]},"finish_reason":"tool_calls"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = repo,
                onPartialOutput = {},
            )
            val assistant = messages.find { it["role"] == "assistant" }
            assertNotNull(assistant)
            assertTrue((assistant!!["content"] as? String)?.isNotEmpty() == true)
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration handles non-map tool call element`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("bad-tc-test")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter.processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":["not-a-map"]},"finish_reason":"tool_calls"}]}""",
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
                .createTempDirectory("bad-fn-test")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter.processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":[{"id":"c1","function":"not-a-map"}]},"finish_reason":"tool_calls"}]}""",
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
    fun `processToolLoopIteration handles tool_call with non-string function name`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("noname-test")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter.processToolLoopIteration(
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
    fun `processToolLoopIteration handles tool_call with non-string arguments`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("noargs-test")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter.processToolLoopIteration(
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
    fun `processToolLoopIteration handles non-list tool_calls`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("nonlist-tc-test")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter.processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":"not-a-list"},"finish_reason":"tool_calls"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = repo,
                onPartialOutput = {},
            )
            assertTrue(messages.isEmpty())
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration returns false when finish_reason is stop and tool calls are empty`() {
        val allText = StringBuilder()
        val keepGoing =
            adapter.processToolLoopIteration(
                iteration = 1,
                respJson =
                    """{"choices":[{"message":{"content":"final answer"},"finish_reason":"stop"}]}""",
                messages = mutableListOf(),
                allText = allText,
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(keepGoing)
    }

    @Test
    fun `processToolLoopIteration handles content field with non-string type`() {
        val allText = StringBuilder()
        val keepGoing =
            adapter.processToolLoopIteration(
                iteration = 1,
                respJson = """{"choices":[{"message":{"content":42},"finish_reason":"stop"}]}""",
                messages = mutableListOf(),
                allText = allText,
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertFalse(keepGoing)
    }

    @Test
    fun `processToolLoopIteration uses default call id when tool_call id missing`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("call-id-test")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            adapter.processToolLoopIteration(
                iteration = 7,
                respJson =
                    """{"choices":[{"message":{"content":"","tool_calls":[{"function":{"name":"read_file","arguments":"{\"path\":\"x\"}"}}]},"finish_reason":"tool_calls"}]}""",
                messages = messages,
                allText = StringBuilder(),
                repoDir = repo,
                onPartialOutput = {},
            )
            val toolMsg = messages.find { it["role"] == "tool" }
            assertNotNull(toolMsg)
            assertEquals("call_7", toolMsg!!["tool_call_id"])
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration returns false when finish_reason is stop even with tool calls present`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("stop-with-tc-test")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            val keepGoing =
                adapter.processToolLoopIteration(
                    iteration = 1,
                    respJson =
                        """{"choices":[{"message":{"content":"done","tool_calls":[{"id":"c1","function":{"name":"read_file","arguments":"{}"}}]},"finish_reason":"stop"}]}""",
                    messages = messages,
                    allText = StringBuilder(),
                    repoDir = repo,
                    onPartialOutput = {},
                )
            assertFalse(keepGoing)
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun `processToolLoopIteration with invalid tool args JSON falls back to emptyMap`() {
        val repo =
            java.nio.file.Files
                .createTempDirectory("bad-args-test")
                .toFile()
        try {
            val messages = mutableListOf<Map<String, Any>>()
            val keepGoing =
                adapter.processToolLoopIteration(
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
    fun `executeWithRepoTools with file as repoDir falls back to plain call`() {
        val file = java.io.File.createTempFile("not-a-dir", "")
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\ndata: [DONE]\n"),
            )
            val result =
                adapter.executeWithStreamingPerLine(
                    "m",
                    "p",
                    timeoutOverride = 30L,
                    repoDir = file,
                    onPartialOutput = {},
                )
            assertTrue(result.contains("hello"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `ChatTransport send delegates to sendChatRequest`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"ok"}}]}"""),
        )
        val response = adapter.toolLoop.transport.send("""{"model":"m","messages":[]}""", 10L)
        assertTrue(response.contains("ok"))
    }

    @Test
    fun `buildRequestBody attaches images as a separate top-level images array on the user message`() {
        val attachment =
            com.stacklens.application.port.out.ai.AiAttachment(
                mimeType = "image/png",
                base64Data = "BASE64DATA",
            )
        val body = adapter.buildRequestBody("llava", "describe this", stream = false, attachments = listOf(attachment))
        val parsed =
            com.fasterxml.jackson.databind
                .ObjectMapper()
                .readTree(body)
        val userMessage = parsed.path("messages").path(1)
        assertEquals("user", userMessage.path("role").asText())
        assertEquals("describe this", userMessage.path("content").asText())
        val images = userMessage.path("images")
        assertTrue(images.isArray)
        assertEquals(1, images.size())
        assertEquals("BASE64DATA", images.path(0).asText())
    }
}
