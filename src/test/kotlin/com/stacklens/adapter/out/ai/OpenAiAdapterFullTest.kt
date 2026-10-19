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

class OpenAiAdapterFullTest {
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

    private fun aiConfig(key: String = "sk-test"): AiConfig = AiConfig(openAiApiKey = key)

    @Test
    fun `parseResponsesApiResponse returns empty when output is missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{}"""),
        )
        val result = adapter().execute("gpt-5.2", "hello")
        assertEquals("", result)
    }

    @Test
    fun `parseResponsesApiResponse handles output array with content field`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"output":[{"content":[{"content":"inner-content"}]}]}""",
                ),
        )
        val result = adapter().execute("gpt-5.2", "hello")
        assertEquals("inner-content", result)
    }

    @Test
    fun `parseResponsesApiResponse handles multiple content items`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"output":[{"content":[{"text":"a"},{"text":"b"}]},{"content":[{"text":"c"}]}]}""",
                ),
        )
        val result = adapter().execute("gpt-5.2", "hello")
        assertEquals("abc", result)
    }

    @Test
    fun `execute throws on HTTP 400`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("gpt-5.2", "hello")
            }
        assertTrue(ex.message!!.contains("HTTP 400"))
    }

    @Test
    fun `execute throws on HTTP 401`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("gpt-5.2", "hello")
            }
        assertTrue(ex.message!!.contains("HTTP 401"))
    }

    @Test
    fun `resolveToken returns env var when config has no key`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(key = ""))
        val prev = System.getenv("OPENAI_API_KEY")
        try {
            assertEquals(null, adapter().resolveToken())
        } finally {
            prev
        }
    }

    @Test
    fun `fetchAvailableModels returns gpt-prefixed model ids`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"data":[{"id":"gpt-5.2"},{"id":"gpt-4o"},{"id":"dall-e-3"}]}""",
                ),
        )
        val models = adapter().fetchAvailableModels("sk-test")
        assertTrue(models.contains("gpt-5.2"))
        assertTrue(models.contains("gpt-4o"))
        assertTrue(!models.contains("dall-e-3"))
    }

    @Test
    fun `fetchAvailableModels handles missing data field`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))
        val models = adapter().fetchAvailableModels("sk-test")
        assertEquals(emptyList<String>(), models)
    }

    @Test
    fun `fetchAvailableModels throws on HTTP error`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().fetchAvailableModels("sk-test")
            }
        assertTrue(ex.message!!.contains("/v1/models"))
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
        var captured: String? = null
        val result =
            adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { partial ->
                captured = partial
            })
        assertEquals("hello", result)
        assertEquals("hello", captured)
    }

    @Test
    fun `executeWithRepoTools handles missing content in response`(
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
        val result = adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("", result)
    }

    @Test
    fun `executeWithRepoTools handles empty choices array`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[]}"""),
        )
        val result = adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertEquals("", result)
    }

    @Test
    fun `executeWithRepoTools handles missing choices field`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))
        val result = adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
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
                    """{"choices":[{"message":{"content":"creating file","tool_calls":[{"id":"c1","function":{"name":"write","arguments":"{\"path\":\"a.txt\",\"content\":\"hi\"}"}}]},"finish_reason":"tool_calls"}]}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"choices":[{"message":{"content":"done"},"finish_reason":"stop"}]}""",
                ),
        )
        val result = adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertNotNull(result)
        val writtenFile = tmp.resolve("a.txt").toFile()
        assertTrue(writtenFile.exists())
    }

    @Test
    fun `executeWithRepoTools handles parsed text tool calls`(
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
        val result = adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertNotNull(result)
        val writtenFile = tmp.resolve("b.txt").toFile()
        assertTrue(writtenFile.exists())
    }

    @Test
    fun `executeWithRepoTools throws on HTTP error during loop`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertThrows(IllegalStateException::class.java) {
            adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        }
    }

    @Test
    fun `executeWithRepoTools throws when no token available`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertThrows(IllegalStateException::class.java) {
            adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        }
    }

    @Test
    fun `executeWithStreamingPerLine 4-arg uses default URL path`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"output_text":"via-4-arg","output":[{"content":[{"text":"via-4-arg"}]}]}""",
                ),
        )
        var captured: String? = null
        val result =
            adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, onPartialOutput = { partial ->
                captured = partial
            })
        assertEquals("via-4-arg", result)
        assertEquals("via-4-arg", captured)
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
                    """{"choices":[{"message":{"content":"Here is the file:\n\n```kotlin // path: c.kt\nval x = 1\n```\n"},"finish_reason":"stop"}]}""",
                ),
        )
        adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
    }

    @Test
    fun `executeWithRepoTools handles partial tool_call with missing fields`(
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
        val result = adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertNotNull(result)
    }

    @Test
    fun `executeWithRepoTools handles invalid JSON in tool arguments`(
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
        val result = adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
        assertNotNull(result)
    }

    @Test
    fun `executeWithRepoTools handles max iterations without finishing`(
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
        adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { _ -> })
    }

    @Test
    fun `executeWithRepoTools 5-arg falls back when repoDir does not exist`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"output_text":"plain","output":[{"content":[{"text":"plain"}]}]}""",
                ),
        )
        val nonExistent = java.io.File("/tmp/__definitely_does_not_exist__")
        val result =
            adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, nonExistent, onPartialOutput = { _ -> })
        assertEquals("plain", result)
    }
}
