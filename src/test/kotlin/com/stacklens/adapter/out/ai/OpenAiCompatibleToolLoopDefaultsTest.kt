package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class OpenAiCompatibleToolLoopDefaultsTest {
    private val transport =
        object : OpenAiCompatibleToolLoop.ChatTransport {
            override val providerLabel: String = "openai-compatible"

            override fun send(
                body: String,
                timeoutSeconds: Long,
            ): String = """{"choices":[{"message":{"content":"plain answer"},"finish_reason":"stop"}]}"""
        }

    private val loop = OpenAiCompatibleToolLoop(ObjectMapper(), transport)

    @Test
    fun `a run without completion or cancellation callbacks still returns the answer`(
        @TempDir repo: Path,
    ) {
        val answer =
            loop.runLoop(
                model = "gpt-test",
                systemPrompt = "system",
                userPrompt = "user",
                timeoutSeconds = 5L,
                repoDir = repo.toFile(),
                onPartialOutput = {},
                buildRequestBody = { messages -> """{"messages":${messages.size}}""" },
                sendRequest = { _, _ -> transport.send("", 5L) },
            )

        assertEquals("plain answer", answer)
    }

    @Test
    fun `an unparseable answer ends the loop without further requests`(
        @TempDir repo: Path,
    ) {
        var requests = 0
        val answer =
            loop.runLoop(
                model = "gpt-test",
                systemPrompt = "system",
                userPrompt = "user",
                timeoutSeconds = 5L,
                repoDir = repo.toFile(),
                onPartialOutput = {},
                buildRequestBody = { "[]" },
                sendRequest = { _, _ ->
                    requests++
                    "not json"
                },
                onComplete = { },
                isCancelled = { false },
            )

        assertEquals("", answer)
        assertEquals(1, requests)
    }
}
