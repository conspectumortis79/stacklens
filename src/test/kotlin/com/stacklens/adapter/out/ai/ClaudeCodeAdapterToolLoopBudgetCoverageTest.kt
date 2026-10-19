package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class ClaudeCodeAdapterToolLoopBudgetCoverageTest {
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
        maxTokens: Int = 32_000,
        maxTokensCap: Int = 128_000,
        truncationRetries: Int = 1,
        lookup: ClaudeCodeTokenLookupPort = ClaudeCodeTestSupport.tokenLookup("sk-ant-api03-key"),
    ): ClaudeCodeAdapter =
        ClaudeCodeAdapter(
            defaultBaseUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            tokenLookup = lookup,
            settings = ClaudeCodeTestSupport.settings(),
            maxTokens = maxTokens,
            maxTokensCap = maxTokensCap,
            truncationRetries = truncationRetries,
        )

    private fun enqueue(
        status: Int,
        body: String,
    ) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun truncated(outputTokens: Int = 8_192): String =
        """
        {"stop_reason":"max_tokens","content":[{"type":"thinking","thinking":"deep"},{"type":"text","text":"half"}],
         "usage":{"output_tokens":$outputTokens,"output_tokens_details":{"thinking_tokens":1000}}}
        """.trimIndent()

    private fun runToolLoop(
        subject: ClaudeCodeAdapter,
        repoDir: File,
    ): String =
        subject.executeWithStreamingPerLine(
            model = "claude-sonnet-5",
            prompt = "write something",
            timeoutOverride = 10L,
            repoDir = repoDir,
            isCancelled = null,
            onPartialOutput = {},
        )

    private fun requestBodies(): List<String> =
        generateSequence { server.takeRequest(5, TimeUnit.SECONDS) }
            .map { it.body.readUtf8() }
            .toList()

    @Test
    fun `a tool loop that reached the cap reports the truncation without retrying`(
        @TempDir tmp: Path,
    ) {
        enqueue(200, truncated())

        val failure =
            assertThrows(StackLensFailureException::class.java) {
                runToolLoop(adapter(maxTokens = 32_000, maxTokensCap = 32_000), tmp.toFile())
            }

        assertInstanceOf(StackLensFailure.AiOutputTruncated::class.java, failure.failure)
        assertEquals(1, requestBodies().size)
    }

    @Test
    fun `a tool loop without retry allowance reports the truncation`(
        @TempDir tmp: Path,
    ) {
        enqueue(200, truncated())

        val failure =
            assertThrows(StackLensFailureException::class.java) {
                runToolLoop(adapter(truncationRetries = 0), tmp.toFile())
            }

        assertInstanceOf(StackLensFailure.AiOutputTruncated::class.java, failure.failure)
        assertEquals(1, requestBodies().size)
    }

    @Test
    fun `a credit rejection inside the tool loop is not mistaken for a truncation`(
        @TempDir tmp: Path,
    ) {
        enqueue(402, """{"type":"error","error":{"type":"billing_error","message":"credit balance is too low"}}""")

        val failure =
            assertThrows(StackLensFailureException::class.java) {
                runToolLoop(adapter(), tmp.toFile())
            }

        assertInstanceOf(StackLensFailure.AiCreditsExhausted::class.java, failure.failure)
        assertEquals(1, requestBodies().size)
    }
}
