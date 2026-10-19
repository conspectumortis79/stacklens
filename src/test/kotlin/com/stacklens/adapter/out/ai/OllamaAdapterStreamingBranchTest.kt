package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class OllamaAdapterStreamingBranchTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: OllamaAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter =
            OllamaAdapter(
                aiConfigRepo = mock(),
                objectMapper = ObjectMapper(),
                promptLoader = mock(),
                defaultOllamaUrl = server.url("/").toString().removeSuffix("/"),
                timeoutSeconds = 5L,
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun sseBody(chunks: List<String>): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(
                chunks.joinToString("", prefix = "", postfix = "\n") { "data: $it\n" } +
                    "data: [DONE]\n",
            )

    @Test
    fun `streaming skips chunk with missing choices field`() {
        server.enqueue(
            sseBody(
                listOf(
                    """{"delta":{"content":"x"}}""",
                    """{"choices":[{"delta":{"content":"real"}}]}""",
                ),
            ),
        )
        val result = adapter.executeWithStreaming("model", "prompt", onPartialOutput = { })
        assertEquals("real", result)
    }

    @Test
    fun `streaming skips chunk with empty choices array`() {
        server.enqueue(
            sseBody(
                listOf(
                    """{"choices":[]}""",
                    """{"choices":[{"delta":{"content":"y"}}]}""",
                ),
            ),
        )
        val result = adapter.executeWithStreaming("model", "prompt", onPartialOutput = { })
        assertEquals("y", result)
    }

    @Test
    fun `streaming skips chunk where first choice is not a Map`() {
        server.enqueue(
            sseBody(
                listOf(
                    """{"choices":["not-a-map"]}""",
                    """{"choices":[{"delta":{"content":"z"}}]}""",
                ),
            ),
        )
        val result = adapter.executeWithStreaming("model", "prompt", onPartialOutput = { })
        assertEquals("z", result)
    }

    @Test
    fun `streaming skips chunk where delta key is missing from first choice`() {
        server.enqueue(
            sseBody(
                listOf(
                    """{"choices":[{"other":{"content":"x"}}]}""",
                    """{"choices":[{"delta":{"content":"after-delta-missing"}}]}""",
                ),
            ),
        )
        val result = adapter.executeWithStreaming("model", "prompt", onPartialOutput = { })
        assertEquals("after-delta-missing", result)
    }

    @Test
    fun `streaming skips chunk where delta content is not a String`() {
        server.enqueue(
            sseBody(
                listOf(
                    """{"choices":[{"delta":{"content":42}}]}""",
                    """{"choices":[{"delta":{"content":"a-string"}}]}""",
                ),
            ),
        )
        val result = adapter.executeWithStreaming("model", "prompt", onPartialOutput = { })
        assertEquals("a-string", result)
    }

    @Test
    fun `streaming calls onPartialOutput for first chunk with content`() {
        val captured = mutableListOf<String>()
        server.enqueue(
            sseBody(
                listOf(
                    """{"choices":[{"delta":{"content":"hello"}}]}""",
                    """{"choices":[{"delta":{"content":" world"}}]}""",
                ),
            ),
        )
        adapter.executeWithStreamingPerLine("model", "prompt", 5L, onPartialOutput = { captured.add(it) })
        assertEquals(2, captured.size)
        assertEquals("hello", captured[0])
        assertEquals("hello world", captured[1])
    }

    @Test
    fun `streaming returns only stripped thinking blocks in final result`() {
        server.enqueue(
            sseBody(
                listOf(
                    """{"choices":[{"delta":{"content":"<think>reasoning</think>actual"}}]}""",
                ),
            ),
        )
        val result = adapter.executeWithStreaming("model", "prompt", onPartialOutput = { })
        assertEquals("actual", result)
    }
}
