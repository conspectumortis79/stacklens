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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

class OpenRouterAdapterFullTest {
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

    private fun adapter(
        baseUrl: String = server.url("/").toString().trimEnd('/'),
        nonToolCapablePatterns: List<String> = listOf("minimax"),
    ) = OpenRouterAdapter(
        defaultBaseUrl = baseUrl,
        timeoutSeconds = 10L,
        aiConfigRepo = aiConfigRepo,
        objectMapper = objectMapper,
        promptLoader = promptLoader,
        nonToolCapablePatterns = nonToolCapablePatterns,
    )

    private fun aiConfig(key: String = "sk-test"): AiConfig = AiConfig(openRouterApiKey = key)

    @Test
    fun `isNonToolCapableModel returns true for matching pattern`() {
        val a = adapter(nonToolCapablePatterns = listOf("minimax"))
        assertTrue(a.isNonToolCapableModel("minimax/M3"))
        assertTrue(a.isNonToolCapableModel("MiniMax-M3"))
    }

    @Test
    fun `isNonToolCapableModel returns false for non-matching pattern`() {
        val a = adapter(nonToolCapablePatterns = listOf("minimax"))
        assertFalse(a.isNonToolCapableModel("gpt-4o"))
        assertFalse(a.isNonToolCapableModel("claude-opus"))
    }

    @Test
    fun `isNonToolCapableModel returns false when patterns list is empty`() {
        val a = adapter(nonToolCapablePatterns = emptyList())
        assertFalse(a.isNonToolCapableModel("minimax/M3"))
    }

    @Test
    fun `isNonToolCapableModel is case-insensitive`() {
        val a = adapter(nonToolCapablePatterns = listOf("MiniMax"))
        assertTrue(a.isNonToolCapableModel("minimax/M3"))
    }

    @Test
    fun `resolveSystemPrompt appends markdown hint for non-tool-capable models`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openRouterApiKey = "sk"))
        val a = adapter(nonToolCapablePatterns = listOf("minimax"))
        a.resolveSystemPrompt()
    }

    @Test
    fun `executeWithRepoTools returns text when no tool calls`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"hello"},"finish_reason":"stop"}]}""",
                ),
        )
        val result =
            adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("hello", result)
    }

    @Test
    fun `executeWithRepoTools handles empty choices`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[]}"""))
        val result =
            adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("", result)
    }

    @Test
    fun `executeWithRepoTools handles missing choices`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))
        val result =
            adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("", result)
    }

    @Test
    fun `executeWithRepoTools handles missing message`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"finish_reason":"stop"}]}"""),
        )
        val result =
            adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("", result)
    }

    @Test
    fun `executeWithRepoTools handles empty message content`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{},"finish_reason":"stop"}]}""",
                ),
        )
        val result =
            adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("", result)
    }

    @Test
    fun `executeWithRepoTools handles structured tool_calls`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
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
        adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertTrue(tmp.resolve("a.txt").toFile().exists())
    }

    @Test
    fun `executeWithRepoTools handles Qwen3-style text tool calls`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
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
        adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertTrue(tmp.resolve("b.txt").toFile().exists())
    }

    @Test
    fun `executeWithRepoTools handles Hermes-style text tool calls`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"<function=write><parameter=path>c.txt</parameter><parameter=content>hermes-content</parameter></function>"},"finish_reason":"tool_calls"}]}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"done"},"finish_reason":"stop"}]}""",
                ),
        )
        adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertTrue(tmp.resolve("c.txt").toFile().exists())
    }

    @Test
    fun `executeWithRepoTools handles fenced JSON tool calls`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"```tool_call\n{\"name\":\"write\",\"arguments\":{\"path\":\"d.txt\",\"content\":\"fenced\"}}\n```"},"finish_reason":"tool_calls"}]}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"done"},"finish_reason":"stop"}]}""",
                ),
        )
        adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertTrue(tmp.resolve("d.txt").toFile().exists())
    }

    @Test
    fun `executeWithRepoTools throws on HTTP error`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertThrows(IllegalStateException::class.java) {
            adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        }
    }

    @Test
    fun `executeWithRepoTools throws when no token available`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertThrows(IllegalStateException::class.java) {
            adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        }
    }

    @Test
    fun `executeWithStreamingPerLine 5-arg falls back when repoDir is invalid`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"plain"},"finish_reason":"stop"}]}""",
                ),
        )
        val nonExistent = java.io.File("/tmp/__definitely_does_not_exist_openrouter__")
        val result =
            adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, nonExistent, onPartialOutput = { _ -> })
        assertEquals("plain", result)
    }

    @Test
    fun `executeWithRepoTools handles invalid JSON in tool args`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"x","tool_calls":[{"id":"c1","function":{"name":"write","arguments":"not-valid-json"}}]},"finish_reason":"tool_calls"}]}""",
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
            adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertNotNull(result)
    }

    @Test
    fun `executeWithRepoTools handles tool_calls with missing function fields`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
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
            adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertNotNull(result)
    }

    @Test
    fun `executeWithRepoTools exits after max iterations without finishing`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        repeat(65) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """{"choices":[{"message":{"content":"iter","tool_calls":[{"id":"c$it","function":{"name":"write","arguments":"{\"path\":\"f$it.txt\",\"content\":\"x\"}"}}]},"finish_reason":"tool_calls"}]}""",
                    ),
            )
        }
        adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
    }

    @Test
    fun `executeWithRepoTools exits early when finish_reason is stop`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"final","tool_calls":[{"id":"c1","function":{"name":"write","arguments":"{\"path\":\"x.txt\",\"content\":\"x\"}"}}]},"finish_reason":"stop"}]}""",
                ),
        )
        val result =
            adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("final", result)
    }

    @Test
    fun `executeWithRepoTools extracts markdown file blocks`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"Here is the file:\n\n```kotlin // path: extracted.kt\nval x = 1\n```\n"},"finish_reason":"stop"}]}""",
                ),
        )
        adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
    }

    @Test
    fun `resolveToken returns null when config has no key and no env var`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(key = ""))
        assertEquals(null, adapter().resolveToken())
    }

    @Test
    fun `resolveToken returns null when no config exists`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals(null, adapter().resolveToken())
    }

    @Test
    fun `resolveToken skips blank key in config`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(key = "   "))
        assertEquals(null, adapter().resolveToken())
    }

    @Test
    fun `execute throws on HTTP 401`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("gpt-4o", "hi")
            }
        assertTrue(ex.message!!.contains("HTTP 401"))
    }

    @Test
    fun `execute throws on HTTP 429`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate-limited"))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("gpt-4o", "hi")
            }
        assertTrue(ex.message!!.contains("HTTP 429"))
    }

    @Test
    fun `execute throws when no token configured`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertThrows(IllegalStateException::class.java) {
            adapter().execute("gpt-4o", "hi")
        }
    }
}
