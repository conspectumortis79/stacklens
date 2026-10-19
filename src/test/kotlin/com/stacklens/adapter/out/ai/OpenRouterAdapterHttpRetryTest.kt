package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class OpenRouterAdapterHttpRetryTest {
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
        whenever(promptLoader.get(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn("system prompt")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(
        baseUrl: String = server.url("/").toString().trimEnd('/'),
        httpSendRetry: HttpSendRetry = HttpSendRetry(maxAttempts = 3, initialBackoffMs = 0L),
    ) = OpenRouterAdapter(
        defaultBaseUrl = baseUrl,
        timeoutSeconds = 10L,
        aiConfigRepo = aiConfigRepo,
        objectMapper = objectMapper,
        promptLoader = promptLoader,
        httpSendRetry = httpSendRetry,
    )

    private fun aiConfig(key: String = "sk-test"): AiConfig = AiConfig(openRouterApiKey = key)

    private fun dispatcher(responses: List<MockResponse>): Dispatcher =
        object : Dispatcher() {
            private val idx = AtomicInteger(0)

            override fun dispatch(request: RecordedRequest): MockResponse {
                val i = idx.getAndIncrement()
                return responses.getOrNull(i) ?: MockResponse().setResponseCode(500).setBody("queue-exhausted")
            }
        }

    @Test
    fun `execute recovers when the first response drops the connection mid-flight`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.dispatcher =
            dispatcher(
                listOf(
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"choices":[{"message":{"content":"recovered"}}]}"""),
                ),
            )

        val result = adapter().execute("openai/gpt-4.1", "hello")
        assertEquals("recovered", result)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `executeWithStreamingPerLine recovers when the first response drops the connection`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.dispatcher =
            dispatcher(
                listOf(
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"choices":[{"message":{"content":"recovered-too"},"finish_reason":"stop"}]}"""),
                ),
            )
        val result = adapter().executeWithStreamingPerLine("openai/gpt-4.1", "hi", 10L, tmp.toFile(), onPartialOutput = { })
        assertEquals("recovered-too", result)
    }

    @Test
    fun `execute gives up after max attempts and wraps the last IOException in IllegalStateException`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.dispatcher =
            dispatcher(
                List(5) { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) },
            )
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter(httpSendRetry = HttpSendRetry(maxAttempts = 3, initialBackoffMs = 0L))
                    .execute("openai/gpt-4.1", "hello")
            }
        assertTrue(ex.message!!.contains("OpenRouter.chatCompletions"))
        assertTrue(ex.message!!.contains("3 attempt(s)"))
        assertNotNull(ex.cause)
        assertTrue(
            ex.cause is java.io.IOException,
            "expected IOException as cause, got ${ex.cause?.javaClass?.name}",
        )
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `execute gives up immediately with maxAttempts=1 (no retry at all)`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.dispatcher =
            dispatcher(
                List(5) { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) },
            )
        assertThrows(IllegalStateException::class.java) {
            adapter(httpSendRetry = HttpSendRetry(maxAttempts = 1, initialBackoffMs = 0L))
                .execute("openai/gpt-4.1", "hello")
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `execute does NOT retry on HTTP 500 — that's a deterministic failure`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("openai/gpt-4.1", "hello")
            }
        assertTrue(ex.message!!.contains("HTTP 500"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `execute does NOT retry on HTTP 401 — auth failures are deterministic`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("openai/gpt-4.1", "hello")
            }
        assertTrue(ex.message!!.contains("HTTP 401"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `execute makes exactly one request when the first attempt succeeds`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"first-try"}}]}"""),
        )
        val result = adapter().execute("openai/gpt-4.1", "hello")
        assertEquals("first-try", result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `tool-calling loop survives a mid-response disconnect on the first iteration`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.dispatcher =
            dispatcher(
                listOf(
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """{"choices":[{"message":{"content":"x","tool_calls":[{"id":"c1","function":{"name":"write","arguments":"{\"path\":\"a.txt\",\"content\":\"x\"}"}}]},"finish_reason":"tool_calls"}]}""",
                        ),
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"choices":[{"message":{"content":"done"},"finish_reason":"stop"}]}"""),
                ),
            )
        adapter().executeWithStreamingPerLine("gpt-4o", "hi", 10L, tmp.toFile(), onPartialOutput = { })
        assertEquals(3, server.requestCount)
        assertTrue(tmp.resolve("a.txt").toFile().exists())
    }
}
