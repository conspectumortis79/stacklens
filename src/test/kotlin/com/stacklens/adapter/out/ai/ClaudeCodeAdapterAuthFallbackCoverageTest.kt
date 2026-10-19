package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

class ClaudeCodeAdapterAuthFallbackCoverageTest {
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
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system prompt")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(key: String?): ClaudeCodeAdapter {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(key?.let { AiConfig(claudeCodeApiKey = it) })
        return ClaudeCodeAdapter(
            defaultBaseUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 5L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            tokenLookup = ClaudeCodeTestSupport.lookupOfStoredKey(aiConfigRepo),
            settings = ClaudeCodeTestSupport.settings(),
        )
    }

    private fun enqueue(
        status: Int,
        body: String,
    ) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))
    }

    private val oversized = "b".repeat(430)

    @Test
    fun `an oauth token is sent as bearer and its error body is truncated`() {
        enqueue(503, oversized)

        val failure = assertThrows(IllegalStateException::class.java) { adapter("sk-ant-oat01-oauth").toolLoop.transport.send("{}", 5L) }

        assertTrue(failure.message!!.endsWith("b".repeat(400)), failure.message)
    }

    @Test
    fun `an api key answer is returned unchanged`() {
        enqueue(200, """{"content":[{"type":"text","text":"hi"}]}""")

        assertEquals("""{"content":[{"type":"text","text":"hi"}]}""", adapter("sk-ant-api03-key").toolLoop.transport.send("{}", 5L))
    }

    @Test
    fun `a non 401 rejection of the api key is reported with a truncated body`() {
        enqueue(500, oversized)

        val failure = assertThrows(IllegalStateException::class.java) { adapter("sk-ant-api03-key").toolLoop.transport.send("{}", 5L) }

        assertTrue(failure.message!!.endsWith("b".repeat(400)), failure.message)
    }

    @Test
    fun `a 401 falls back to the bearer token and succeeds`() {
        enqueue(401, "{}")
        enqueue(200, """{"content":[{"type":"text","text":"retry ok"}]}""")

        assertEquals("""{"content":[{"type":"text","text":"retry ok"}]}""", adapter("sk-ant-api03-key").toolLoop.transport.send("{}", 5L))
    }

    @Test
    fun `a 401 followed by another failure reports the retry status`() {
        enqueue(401, "{}")
        enqueue(502, oversized)

        val failure = assertThrows(IllegalStateException::class.java) { adapter("sk-ant-api03-key").toolLoop.transport.send("{}", 5L) }

        assertTrue(failure.message!!.contains("HTTP 502"), failure.message)
        assertTrue(failure.message!!.endsWith("b".repeat(400)), failure.message)
    }

    @Test
    fun `a missing api key aborts before any request`() {
        val failure = assertThrows(IllegalStateException::class.java) { adapter(null).toolLoop.transport.send("{}", 5L) }

        assertEquals("No Claude Code API key set.", failure.message)
    }

    @Test
    fun `an oauth token with a successful answer is returned unchanged`() {
        enqueue(200, """{"content":[{"type":"text","text":"ok"}]}""")

        assertEquals("""{"content":[{"type":"text","text":"ok"}]}""", adapter("sk-ant-oat01-oauth").toolLoop.transport.send("{}", 5L))
    }

    @Test
    fun `an error without a body of its own reports the fallback status`() {
        enqueue(500, "too short")

        val failure = assertThrows(IllegalStateException::class.java) { adapter("sk-ant-oat01-oauth").toolLoop.transport.send("{}", 5L) }

        assertEquals("ClaudeCode error (HTTP 500): too short", failure.message)
    }

    @Test
    fun `a rejected api key with a short body reports the plain status`() {
        enqueue(401, "{}")
        enqueue(500, "short body")

        val failure = assertThrows(IllegalStateException::class.java) { adapter("sk-ant-api03-key").toolLoop.transport.send("{}", 5L) }

        assertEquals("ClaudeCode error (HTTP 500): short body", failure.message)
    }

    @Test
    fun `a non 401 rejection with a short body is reported directly`() {
        enqueue(500, "short")

        val failure = assertThrows(IllegalStateException::class.java) { adapter("sk-ant-api03-key").toolLoop.transport.send("{}", 5L) }

        assertEquals("ClaudeCode error (HTTP 500): short", failure.message)
    }

    @Test
    fun `a final answer without file blocks extracts nothing`() {
        enqueue(200, """{"content":[{"type":"text","text":"just prose"}],"stop_reason":"end_turn"}""")

        val answer = adapter("sk-ant-api03-key").executeWithStreamingPerLine("claude", "prompt", null, workDir.toFile(), null, {}, emptyList())

        assertEquals("just prose", answer.trim())
        assertTrue(workDir.toFile().list()!!.isEmpty())
    }

    @Test
    fun `a markdown file block in the final answer is extracted into the repository`() {
        val answer = "## src/main/kotlin/Claude.kt\n```kotlin\nval generated = 2\n```\n"
        enqueue(200, """{"content":[{"type":"text","text":${objectMapper.writeValueAsString(answer)}}],"stop_reason":"end_turn"}""")

        adapter("sk-ant-api03-key").executeWithStreamingPerLine("claude", "prompt", null, workDir.toFile(), null, {}, emptyList())

        assertTrue(File(workDir.toFile(), "src/main/kotlin/Claude.kt").exists())
    }
}
