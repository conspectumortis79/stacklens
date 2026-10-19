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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class ClaudeCodeAdapterThinkingDegradationCoverageTest {
    private lateinit var server: MockWebServer

    private val aiConfigRepo: AiConfigPort = mock()

    private val promptLoader: PromptReaderPort = mock()

    private val objectMapper = ObjectMapper()

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
        storedKey: String = "sk-ant-api03-key",
        thinkingMode: String = ClaudeCodeAdapter.THINKING_MODE_ADAPTIVE,
    ): ClaudeCodeAdapter =
        ClaudeCodeAdapter(
            defaultBaseUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            tokenLookup = ClaudeCodeTestSupport.tokenLookup(storedKey),
            settings = ClaudeCodeTestSupport.settings(),
            thinkingMode = thinkingMode,
        )

    private val adaptiveRejection =
        """{"type":"error","error":{"type":"invalid_request_error","message":"adaptive thinking is not supported on this model"}}"""

    private fun enqueue(
        status: Int,
        body: String,
    ) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun bodies(): List<String> =
        generateSequence { server.takeRequest(5, TimeUnit.SECONDS) }
            .map { it.body.readUtf8() }
            .toList()

    @Test
    fun `a model that keeps rejecting adaptive thinking stops instead of looping`() {
        enqueue(400, adaptiveRejection)
        enqueue(400, adaptiveRejection)

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("claude-sonnet-4-5", "prompt", 10L, emptyList())
            }

        assertTrue(failure.message!!.contains("adaptive thinking is not supported"), failure.message)
        val seen = bodies()
        assertEquals(2, seen.size)
        assertTrue(objectMapper.readTree(seen.first()).has("thinking"))
        assertFalse(objectMapper.readTree(seen.last()).has("thinking"))
    }

    @Test
    fun `a rejection message without a status is not a thinking rejection`() {
        assertFalse(adapter().isAdaptiveThinkingRejected(null))
    }

    @Test
    fun `a blank model never counts as degraded`() {
        assertEquals(ClaudeCodeAdapter.THINKING_MODE_ADAPTIVE, adapter().effectiveThinkingMode(""))
    }

    @Test
    fun `a model that was never degraded keeps the trimmed configured mode`() {
        assertEquals("adaptive", adapter().effectiveThinkingMode(" claude-sonnet-4-5 "))
    }

    @Test
    fun `a blank thinking mode disables thinking for every model`() {
        assertEquals(ClaudeCodeAdapter.THINKING_MODE_NONE, adapter(thinkingMode = "   ").effectiveThinkingMode("claude-sonnet-4-5"))
    }

    @Test
    fun `a degraded model disables thinking`() {
        enqueue(400, adaptiveRejection)
        enqueue(200, """{"stop_reason":"end_turn","content":[{"type":"text","text":"ok"}]}""")

        val subject = adapter()
        assertEquals("ok", subject.execute("claude-sonnet-4-5", "prompt", 10L, emptyList()))

        assertEquals(ClaudeCodeAdapter.THINKING_MODE_NONE, subject.effectiveThinkingMode("claude-sonnet-4-5"))
        assertTrue(subject.isAdaptiveThinkingRejected("ClaudeCode error (HTTP 400): adaptive thinking is not supported"))
    }
}
