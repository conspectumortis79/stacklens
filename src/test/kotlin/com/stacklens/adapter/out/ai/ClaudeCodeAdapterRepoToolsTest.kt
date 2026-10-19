package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class ClaudeCodeAdapterRepoToolsTest {
    private lateinit var server: MockWebServer

    private val aiConfigRepo: AiConfigPort = mock()

    private val promptLoader: PromptReaderPort = mock()

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        whenever(promptLoader.get(any<String>(), any<String>(), any())).thenReturn("system prompt")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(claudeCodeApiKey = "sk-ant-config-key"))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `executeWithStreamingPerLine falls back to the plain call when no repo directory is given`() {
        server.enqueue(textResponse("plain answer"))

        val partials = mutableListOf<String>()

        val result =
            adapter().executeWithStreamingPerLine(
                model = "claude-sonnet",
                prompt = "do it",
                timeoutOverride = null,
                repoDir = null,
                isCancelled = null,
                onPartialOutput = { partials += it },
            )

        assertEquals("plain answer", result)
        assertEquals(listOf("plain answer"), partials)
        assertTrue(server.takeRequest().path!!.endsWith("/v1/messages"))
    }

    @Test
    fun `executeWithStreamingPerLine falls back when the repo path is a file`(
        @TempDir tmp: Path,
    ) {
        val file = File(tmp.toFile(), "not-a-directory").apply { writeText("x") }
        server.enqueue(textResponse("file fallback"))

        val result =
            adapter().executeWithStreamingPerLine(
                model = "claude-sonnet",
                prompt = "do it",
                timeoutOverride = 5L,
                repoDir = file,
                isCancelled = null,
                onPartialOutput = {},
            )

        assertEquals("file fallback", result)
    }

    @Test
    fun `executeWithStreamingPerLine falls back when the repo directory is missing`(
        @TempDir tmp: Path,
    ) {
        server.enqueue(textResponse("missing fallback"))

        val result =
            adapter().executeWithStreamingPerLine(
                model = "claude-sonnet",
                prompt = "do it",
                timeoutOverride = 5L,
                repoDir = File(tmp.toFile(), "gone"),
                isCancelled = null,
                onPartialOutput = {},
            )

        assertEquals("missing fallback", result)
    }

    @Test
    fun `executeWithStreamingPerLine runs the tool loop and writes the markdown files of the final answer`(
        @TempDir tmp: Path,
    ) {
        val requests = AtomicInteger(0)
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    return if (requests.getAndIncrement() == 0 && !body.contains("\"tool_result\"")) {
                        MockResponse()
                            .setResponseCode(200)
                            .setBody(
                                """
                                {"stop_reason":"tool_use","content":[
                                  {"type":"tool_use","id":"tu_1","name":"write",
                                   "input":{"path":"src/Report.kt","content":"fun report() = 1"}}
                                ]}
                                """.trimIndent(),
                            )
                    } else {
                        MockResponse()
                            .setResponseCode(200)
                            .setBody(
                                """
                                {"stop_reason":"end_turn","content":[
                                  {"type":"text","text":"## src/Summary.kt\n```kotlin\nfun summary() = 2\n```"}
                                ]}
                                """.trimIndent(),
                            )
                    }
                }
            }
        val partials = mutableListOf<String>()

        val result =
            adapter().executeWithStreamingPerLine(
                model = "claude-sonnet",
                prompt = "write a report",
                timeoutOverride = 20L,
                repoDir = tmp.toFile(),
                isCancelled = null,
                onPartialOutput = { partials += it },
            )

        assertEquals(
            "fun report() = 1",
            File(tmp.toFile(), "src/Report.kt").readText().trim(),
        )
        assertEquals(
            "fun summary() = 2",
            File(tmp.toFile(), "src/Summary.kt").readText().trim(),
        )
        assertTrue(result.contains("Summary.kt"), "unexpected result: $result")
        assertEquals(2, requests.get())
        assertEquals(1, partials.size)
    }

    @Test
    fun `a rejected x-api-key is retried with the bearer header`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        server.enqueue(textResponse("after bearer"))

        val result =
            adapter(token = "sk-ant-prefixed-token-1234")
                .executeWithStreaming("claude-sonnet", "prompt", null, null, {})

        assertEquals("after bearer", result)
        val retried = server.takeRequest().let { server.takeRequest() }
        assertEquals("Bearer sk-ant-prefixed-token-1234", retried.getHeader("Authorization"))
        assertEquals(null, retried.getHeader("x-api-key"))
    }

    @Test
    fun `oauth style tokens are sent as bearer tokens on the first attempt`() {
        server.enqueue(textResponse("oauth ok"))

        val result = adapter(token = "sk-ant-oat01-abcdef").executeWithStreaming("claude", "p", null, null, {})

        assertEquals("oauth ok", result)
        assertEquals("Bearer sk-ant-oat01-abcdef", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a server error is reported with the status code and body`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter(token = "short").executeWithStreaming("claude", "p", null, null, {})
            }

        assertTrue(failure.message!!.contains("HTTP 503"), "unexpected: ${failure.message}")
        assertTrue(failure.message!!.contains("unavailable"), "unexpected: ${failure.message}")
    }

    @Test
    fun `a failing bearer retry is reported as well`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("first"))
        server.enqueue(MockResponse().setResponseCode(403).setBody("second"))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter(token = "plain-token-without-prefix").executeWithStreaming("claude", "p", null, null, {})
            }

        assertTrue(failure.message!!.contains("HTTP 403"), "unexpected: ${failure.message}")
    }

    @Test
    fun `parseMessagesResponse ignores content items that are not objects`() {
        assertEquals("kept", adapter().parseMessagesResponse("""{"content":["skip",{"type":"text","text":"kept"}]}"""))
    }

    @Test
    fun `execute fails when no token can be resolved`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)

        val failure =
            assertThrows(IllegalStateException::class.java) {
                ClaudeCodeAdapter(
                    defaultBaseUrl = server.url("/").toString().trimEnd('/'),
                    timeoutSeconds = 5L,
                    aiConfigRepo = aiConfigRepo,
                    objectMapper = objectMapper,
                    promptLoader = promptLoader,
                    tokenLookup = ClaudeCodeTestSupport.tokenLookup(null),
                    settings = ClaudeCodeTestSupport.settings(),
                ).execute("claude", "prompt")
            }

        assertEquals("No Claude Code API key set.", failure.message)
    }

    private fun textResponse(text: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody("""{"stop_reason":"end_turn","content":[{"type":"text","text":"$text"}]}""")

    private fun adapter(token: String = "sk-ant-config-key"): ClaudeCodeAdapter {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(claudeCodeApiKey = token))
        return ClaudeCodeAdapter(
            defaultBaseUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            tokenLookup = ClaudeCodeTestSupport.tokenLookup(token),
            settings = ClaudeCodeTestSupport.settings(),
        )
    }
}
