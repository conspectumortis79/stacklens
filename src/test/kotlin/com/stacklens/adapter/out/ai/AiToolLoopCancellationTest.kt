package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class AiToolLoopCancellationTest {
    private val objectMapper = ObjectMapper()

    private val openAiContinueResponse: String =
        """
        {"choices":[{"finish_reason":"tool_calls","message":{"content":"","tool_calls":
        [{"id":"call_1","type":"function","function":{"name":"list_files","arguments":"{}"}}]}}]}
        """.trimIndent()

    private val anthropicContinueResponse: String =
        """
        {"stop_reason":"tool_use","content":[{"type":"tool_use","id":"tu_1","name":"list_files","input":{}}]}
        """.trimIndent()

    private fun openAiLoop(): OpenAiCompatibleToolLoop =
        OpenAiCompatibleToolLoop(
            objectMapper = objectMapper,
            transport =
                object : OpenAiCompatibleToolLoop.ChatTransport {
                    override val providerLabel: String = "Loop"

                    override fun send(
                        body: String,
                        timeoutSeconds: Long,
                    ): String = openAiContinueResponse
                },
        )

    private fun anthropicLoop(): AnthropicToolLoop =
        AnthropicToolLoop(
            objectMapper = objectMapper,
            transport =
                object : AnthropicToolLoop.ChatTransport {
                    override val providerLabel: String = "Loop"

                    override fun send(
                        body: String,
                        timeoutSeconds: Long,
                    ): String = anthropicContinueResponse
                },
        )

    @Test
    fun `openai compatible tool loop stops before the next request once cancelled`(
        @TempDir repoDir: File,
    ) {
        val sends = AtomicInteger(0)
        val completions = AtomicInteger(0)

        val failure =
            runCatching {
                openAiLoop()
                    .runLoop(
                        model = "test-model",
                        systemPrompt = "system",
                        userPrompt = "user",
                        timeoutSeconds = 5L,
                        repoDir = repoDir,
                        onPartialOutput = { },
                        buildRequestBody = { "{}" },
                        sendRequest = { _, _ ->
                            sends.incrementAndGet()
                            openAiContinueResponse
                        },
                        onComplete = { completions.incrementAndGet() },
                        isCancelled = { sends.get() >= 1 },
                    )
            }.exceptionOrNull()

        assertTrue(failure is InterruptedException, "expected InterruptedException, got $failure")
        assertEquals(1, sends.get(), "the loop must not send another request after cancellation")
        assertEquals(0, completions.get(), "a cancelled loop must not report completion")
    }

    @Test
    fun `anthropic tool loop stops before the next request once cancelled`(
        @TempDir repoDir: File,
    ) {
        val sends = AtomicInteger(0)

        val failure =
            runCatching {
                anthropicLoop()
                    .runLoop(
                        model = "test-model",
                        systemPrompt = "system",
                        userPrompt = "user",
                        timeoutSeconds = 5L,
                        repoDir = repoDir,
                        onPartialOutput = { },
                        buildRequestBody = { "{}" },
                        sendRequest = { _, _ ->
                            sends.incrementAndGet()
                            anthropicContinueResponse
                        },
                        isCancelled = { sends.get() >= 1 },
                    )
            }.exceptionOrNull()

        assertTrue(failure is InterruptedException, "expected InterruptedException, got $failure")
        assertEquals(1, sends.get(), "the loop must not send another request after cancellation")
    }
}
