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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class OpenAiAdapterHttpRetryTest {
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
    ) = OpenAiAdapter(
        defaultOpenAiBaseUrl = baseUrl,
        timeoutSeconds = 10L,
        aiConfigRepo = aiConfigRepo,
        objectMapper = objectMapper,
        promptLoader = promptLoader,
        httpSendRetry = httpSendRetry,
    )

    private fun aiConfig(key: String = "sk-test"): AiConfig = AiConfig(openAiApiKey = key)

    private fun dispatcher(responses: List<MockResponse>): Dispatcher =
        object : Dispatcher() {
            private val idx = AtomicInteger(0)

            override fun dispatch(request: RecordedRequest): MockResponse {
                val i = idx.getAndIncrement()
                return responses.getOrNull(i) ?: MockResponse().setResponseCode(500).setBody("queue-exhausted")
            }
        }

    @Test
    fun `execute recovers when the first Responses-API response drops the connection`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.dispatcher =
            dispatcher(
                listOf(
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"output_text":"recovered","output":[{"content":[{"text":"recovered"}]}]}"""),
                ),
            )
        val result = adapter().execute("gpt-5.2", "hello")
        assertEquals("recovered", result)
        assertEquals(2, server.requestCount)
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
        adapter().executeWithStreamingPerLine("gpt-5.2", "hi", 10L, tmp.toFile(), onPartialOutput = { })
        assertEquals(3, server.requestCount)
        assertTrue(tmp.resolve("a.txt").toFile().exists())
    }

    @Test
    fun `execute does NOT retry on HTTP 500`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("gpt-5.2", "hello")
            }
        assertTrue(ex.message!!.contains("HTTP 500"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `execute gives up after max attempts when every request drops the connection`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.dispatcher =
            dispatcher(
                List(5) { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) },
            )
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("gpt-5.2", "hello")
            }
        assertTrue(ex.message!!.contains("OpenAI.responses"))
        assertTrue(ex.message!!.contains("3 attempt(s)"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `fetchAvailableModels recovers from a dropped connection`(
        @TempDir tmp: Path,
    ) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        server.dispatcher =
            dispatcher(
                listOf(
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"data":[{"id":"gpt-5.2"},{"id":"gpt-4o"}]}"""),
                ),
            )
        val models = adapter().fetchAvailableModels("sk-test")
        assertTrue(models.contains("gpt-5.2"))
        assertEquals(2, server.requestCount)
    }
}
