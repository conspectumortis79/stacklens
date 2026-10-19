package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class ClaudeCodeAdapterTruncationTest {
    private lateinit var server: MockWebServer

    private val aiConfigRepo: AiConfigPort = mock()

    private val promptLoader: PromptReaderPort = mock()

    private val objectMapper = ObjectMapper()

    @TempDir
    lateinit var workDir: Path

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(claudeCodeApiKey = "sk-ant-api03-key", model = "claude-sonnet-5"))
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system prompt")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(
        maxTokens: Int = 32_000,
        maxTokensCap: Int = 128_000,
        truncationRetries: Int = 1,
        thinkingMode: String = ClaudeCodeAdapter.THINKING_MODE_ADAPTIVE,
        thinkingEffort: String = "low",
    ): ClaudeCodeAdapter =
        ClaudeCodeAdapter(
            defaultBaseUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            tokenLookup = ClaudeCodeTestSupport.tokenLookup("sk-ant-api03-key"),
            settings = ClaudeCodeTestSupport.settings(),
            maxTokens = maxTokens,
            maxTokensCap = maxTokensCap,
            truncationRetries = truncationRetries,
            thinkingMode = thinkingMode,
            thinkingEffort = thinkingEffort,
        )

    private fun enqueue(
        status: Int,
        body: String,
    ) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun truncated(
        outputTokens: Int = 8192,
        thinkingTokens: Int = 8192,
        text: String = "",
    ): String =
        """
        {"stop_reason":"max_tokens","content":[{"type":"thinking","thinking":"deep"},{"type":"text","text":"$text"}],
         "usage":{"output_tokens":$outputTokens,"output_tokens_details":{"thinking_tokens":$thinkingTokens}}}
        """.trimIndent()

    private fun completed(text: String): String =
        """
        {"stop_reason":"end_turn","content":[{"type":"text","text":"$text"}],"usage":{"output_tokens":40}}
        """.trimIndent()

    private fun requestBodies(): List<String> =
        generateSequence { server.takeRequest(5, TimeUnit.SECONDS) }
            .map { it.body.readUtf8() }
            .toList()

    @Test
    fun `parseMessages keeps the stop reason and the thinking share`() {
        val parsed = adapter().parseMessages(truncated(outputTokens = 900, thinkingTokens = 700, text = "half"))

        assertEquals("half", parsed.text)
        assertEquals("max_tokens", parsed.stopReason)
        assertEquals(900, parsed.outputTokens)
        assertEquals(700, parsed.thinkingTokens)
    }

    @Test
    fun `parseMessages ignores thinking blocks`() {
        val json = """{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":"secret"},{"type":"text","text":"visible"}]}"""

        assertEquals("visible", adapter().parseMessagesResponse(json))
    }

    @Test
    fun `a truncated answer is retried with a doubled token budget`() {
        enqueue(200, truncated())
        enqueue(200, completed("FINDINGS: []"))

        val result = adapter(maxTokens = 32_000, maxTokensCap = 128_000).execute("claude-sonnet-5", "prompt", 10L, emptyList())

        assertEquals("FINDINGS: []", result)
        val budgets = requestBodies().map { objectMapper.readTree(it).path("max_tokens").asInt() }
        assertEquals(listOf(32_000, 64_000), budgets)
    }

    @Test
    fun `a persistent truncation surfaces the token budget instead of a silent empty answer`() {
        enqueue(200, truncated())
        enqueue(200, truncated(outputTokens = 64_000, thinkingTokens = 64_000))

        val failure =
            assertThrows(StackLensFailureException::class.java) {
                adapter(maxTokens = 32_000, maxTokensCap = 64_000).execute("claude-sonnet-5", "prompt", 10L, emptyList())
            }

        assertEquals(StackLensFailure.AiOutputTruncated(64_000, 64_000, 0), failure.failure)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `the escalation stops at the configured cap`() {
        enqueue(200, truncated())
        enqueue(200, truncated(outputTokens = 48_000, thinkingTokens = 0))

        val failure =
            assertThrows(StackLensFailureException::class.java) {
                adapter(maxTokens = 32_000, maxTokensCap = 40_000, truncationRetries = 3).execute("claude-sonnet-5", "prompt", 10L, emptyList())
            }

        assertEquals(StackLensFailure.AiOutputTruncated(48_000, 0, 0), failure.failure)
        val budgets = requestBodies().map { objectMapper.readTree(it).path("max_tokens").asInt() }
        assertEquals(listOf(32_000, 40_000), budgets)
    }

    @Test
    fun `no retry budget means the first truncation already fails`() {
        enqueue(200, truncated())

        val failure =
            assertThrows(StackLensFailureException::class.java) {
                adapter(truncationRetries = 0).execute("claude-sonnet-5", "prompt", 10L, emptyList())
            }

        assertEquals(StackLensFailure.AiOutputTruncated(8192, 8192, 0), failure.failure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `adaptive thinking is requested with the configured effort`() {
        val body = objectMapper.readTree(adapter().buildRequestBody("claude-sonnet-5", "hello"))

        assertEquals("adaptive", body.path("thinking").path("type").asText())
        assertEquals("low", body.path("output_config").path("effort").asText())
    }

    @Test
    fun `the tool request body carries the same thinking configuration`() {
        val body = objectMapper.readTree(adapter().buildToolRequestBody("claude-sonnet-5", listOf(mapOf("role" to "user", "content" to "hi"))))

        assertEquals("adaptive", body.path("thinking").path("type").asText())
        assertTrue(body.has("tools"))
    }

    @Test
    fun `a provider default thinking mode omits every thinking field`() {
        val body =
            objectMapper.readTree(
                adapter(thinkingMode = ClaudeCodeAdapter.THINKING_MODE_NONE).buildRequestBody("claude-sonnet-4-5", "hello"),
            )

        assertFalse(body.has("thinking"))
        assertFalse(body.has("output_config"))
    }

    @Test
    fun `a disabled thinking mode is sent explicitly`() {
        val body =
            objectMapper.readTree(
                adapter(thinkingMode = ClaudeCodeAdapter.THINKING_MODE_DISABLED).buildRequestBody("claude-sonnet-4-5", "hello"),
            )

        assertEquals("disabled", body.path("thinking").path("type").asText())
        assertFalse(body.has("output_config"))
    }

    @Test
    fun `an unsupported effort falls back to the default`() {
        assertEquals("low", adapter(thinkingEffort = "none").resolvedThinkingEffort())
        assertEquals("xhigh", adapter(thinkingEffort = " XHIGH ").resolvedThinkingEffort())
    }

    @Test
    fun `a model that rejects adaptive thinking is retried without it and remembered`() {
        enqueue(400, """{"type":"error","error":{"type":"invalid_request_error","message":"adaptive thinking is not supported on this model"}}""")
        enqueue(200, completed("ok"))

        val subject = adapter()

        assertEquals("ok", subject.execute("claude-sonnet-4-5", "prompt", 10L, emptyList()))
        val bodies = requestBodies()
        assertTrue(objectMapper.readTree(bodies.first()).has("thinking"))
        assertFalse(objectMapper.readTree(bodies.last()).has("thinking"))
        assertEquals(ClaudeCodeAdapter.THINKING_MODE_NONE, subject.effectiveThinkingMode("claude-sonnet-4-5"))

        enqueue(200, completed("second"))
        assertEquals("second", subject.execute("claude-sonnet-4-5", "prompt", 10L, emptyList()))
        assertFalse(objectMapper.readTree(requestBodies().last()).has("thinking"))
    }

    @Test
    fun `a token limit inside the tool loop restarts the loop with a bigger budget`() {
        enqueue(200, truncated(outputTokens = 32_000, thinkingTokens = 1_000))
        enqueue(200, completed("done by tools"))

        val result =
            adapter().executeWithStreamingPerLine(
                model = "claude-sonnet-5",
                prompt = "write something",
                timeoutOverride = 10L,
                repoDir = workDir.toFile(),
                isCancelled = null,
                onPartialOutput = {},
            )

        assertEquals("done by tools", result)
        val budgets = requestBodies().map { objectMapper.readTree(it).path("max_tokens").asInt() }
        assertEquals(listOf(32_000, 64_000), budgets)
    }

    @Test
    fun `the tool request body uses the anthropic tool shape instead of the openai function wrapper`() {
        val body =
            objectMapper.readTree(
                adapter().buildToolRequestBody("claude-sonnet-5", listOf(mapOf("role" to "user", "content" to "list the repo"))),
            )
        val tools = body.path("tools")

        assertTrue(tools.isArray)
        assertEquals(4, tools.size())
        for (tool in tools) {
            assertTrue(tool.has("name"), tool.toString())
            assertTrue(tool.has("description"), tool.toString())
            assertEquals("object", tool.path("input_schema").path("type").asText())
            assertTrue(tool.path("input_schema").path("properties").isObject)
            assertFalse(tool.has("function"), tool.toString())
            assertFalse(tool.has("parameters"), tool.toString())
        }
        assertEquals(
            listOf("list", "read", "write", "delete"),
            tools.map { it.path("name").asText() },
        )
    }

    @Test
    fun `the openai shape keeps working for the providers that need it`() {
        assertEquals(
            listOf("list", "read", "write", "delete"),
            RepoFileToolDefinitions.DEFINITIONS.map { (it["function"] as Map<*, *>)["name"] },
        )
        assertTrue(RepoFileToolDefinitions.DEFINITIONS.all { it["type"] == "function" })
    }

    @Test
    fun `an unrelated http error is not treated as a thinking rejection`() {
        val subject = adapter()

        assertFalse(subject.isAdaptiveThinkingRejected("ClaudeCode error (HTTP 400): rate limited"))
        assertFalse(subject.isAdaptiveThinkingRejected("ClaudeCode error (HTTP 500): adaptive thinking is not supported"))
        assertTrue(subject.isAdaptiveThinkingRejected("ClaudeCode error (HTTP 400): adaptive thinking is not supported on this model"))
    }

    @Test
    fun `the configured budget is clamped into the cap range`() {
        assertEquals(32_000, adapter(maxTokens = 200_000, maxTokensCap = 32_000).effectiveMaxTokens())
        assertEquals(1_024, adapter(maxTokens = 10).effectiveMaxTokens())
    }
}
