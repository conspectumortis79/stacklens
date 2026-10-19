package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.testsupport.HashCollisions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ToolLoopPartialAndStopMarkerCoverageTest {
    private val objectMapper = ObjectMapper()

    private val transport =
        object : OpenAiCompatibleToolLoop.ChatTransport {
            override val providerLabel: String = "test"

            override fun send(
                body: String,
                timeoutSeconds: Long,
            ): String = body
        }

    private val anthropicTransport =
        object : AnthropicToolLoop.ChatTransport {
            override val providerLabel: String = "test"

            override fun send(
                body: String,
                timeoutSeconds: Long,
            ): String = body
        }

    private fun toolCallsJson(
        finishReason: String?,
        content: String,
    ): String {
        val finish = finishReason?.let { "\"finish_reason\":\"$it\"," } ?: ""
        return """{"choices":[{$finish"message":{"content":${objectMapper.writeValueAsString(content)},""" +
            """"tool_calls":[{"id":"c1","function":{"name":"list","arguments":"{}"}}]}}]}"""
    }

    @Test
    fun `text alongside tool calls is pushed to the partial callback`(
        @TempDir dir: Path,
    ) {
        val loop = OpenAiCompatibleToolLoop(objectMapper, transport)
        val partials = mutableListOf<String>()

        val keepGoing =
            loop.processToolLoopIteration(
                iteration = 1,
                respJson = toolCallsJson(null, "working"),
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = dir.toFile(),
                onPartialOutput = { partials += it },
            )

        assertTrue(keepGoing)
        assertEquals(listOf("working"), partials)
    }

    @Test
    fun `a finish reason that only collides with stop keeps the loop running`(
        @TempDir dir: Path,
    ) {
        val collision = HashCollisions.sharingHashCodeOf("stop")
        assertNotEquals("stop", collision)
        val loop = OpenAiCompatibleToolLoop(objectMapper, transport)

        val keepGoing =
            loop.processToolLoopIteration(
                iteration = 1,
                respJson = toolCallsJson(collision, "more"),
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = dir.toFile(),
            )

        assertTrue(keepGoing)
    }

    @Test
    fun `a stop finish reason ends the loop even with tool calls present`(
        @TempDir dir: Path,
    ) {
        val loop = OpenAiCompatibleToolLoop(objectMapper, transport)

        val keepGoing =
            loop.processToolLoopIteration(
                iteration = 1,
                respJson = toolCallsJson("stop", "final"),
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = dir.toFile(),
            )

        assertEquals(false, keepGoing)
    }

    @Test
    fun `a partial callback that throws is swallowed`(
        @TempDir dir: Path,
    ) {
        val loop = OpenAiCompatibleToolLoop(objectMapper, transport)

        assertTrue(
            loop.processToolLoopIteration(
                iteration = 1,
                respJson = toolCallsJson(null, "boom"),
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = dir.toFile(),
                onPartialOutput = { throw IllegalStateException("sink closed") },
            ),
        )
    }

    @Test
    fun `the anthropic loop uses a no-op default callback and its own stop markers`(
        @TempDir dir: Path,
    ) {
        val loop = AnthropicToolLoop(objectMapper, anthropicTransport)
        val messages = mutableListOf<Map<String, Any>>()
        val respJson =
            """{"content":[{"type":"text","text":"hello"},{"type":"tool_use","id":"c1","name":"list","input":{}}],"stop_reason":"tool_use"}"""
        val method =
            AnthropicToolLoop::class.java.getDeclaredMethod(
                "processToolLoopIteration\$default",
                AnthropicToolLoop::class.java,
                Int::class.javaPrimitiveType,
                String::class.java,
                List::class.java,
                StringBuilder::class.java,
                File::class.java,
                Function1::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )

        val keepGoing = method.invoke(null, loop, 1, respJson, messages, StringBuilder(), dir.toFile(), null, 0x20, null)

        assertEquals(true, keepGoing)
        assertEquals(2, messages.size)
    }

    @Test
    fun `a text block without an id or name is skipped`(
        @TempDir dir: Path,
    ) {
        val loop = AnthropicToolLoop(objectMapper, anthropicTransport)
        val partials = mutableListOf<String>()
        val respJson =
            """{"content":[{"type":"text"},{"type":"tool_use","name":"list"},{"type":"tool_use","id":"c1"},{"type":"other"},"stray"],"stop_reason":"tool_use"}"""

        val keepGoing =
            loop.processToolLoopIteration(
                iteration = 1,
                respJson = respJson,
                messages = mutableListOf(),
                allText = StringBuilder(),
                repoDir = dir.toFile(),
                onPartialOutput = { partials += it },
            )

        assertEquals(false, keepGoing)
        assertTrue(partials.isEmpty())
    }
}
