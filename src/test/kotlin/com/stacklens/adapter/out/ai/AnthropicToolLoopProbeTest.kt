package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class AnthropicToolLoopProbeTest {
    private val objectMapper = ObjectMapper()

    private val transport =
        object : AnthropicToolLoop.ChatTransport {
            override val providerLabel: String = "Probe"

            override fun send(
                body: String,
                timeoutSeconds: Long,
            ): String = """{"stop_reason":"end_turn","content":[{"type":"text","text":"done"}]}"""
        }

    private fun loop(maxIterations: Int = 5) = AnthropicToolLoop(objectMapper, transport, maxIterations)

    private class State {
        val messages: MutableList<Map<String, Any>> = mutableListOf()

        val allText = StringBuilder()

        val partials: MutableList<String> = mutableListOf()
    }

    @Test
    fun `an unparsable response ends the loop without touching the messages`() {
        val state = State()

        val keepGoing =
            loop().processToolLoopIteration(
                iteration = 1,
                respJson = "not json",
                messages = state.messages,
                allText = state.allText,
                repoDir = File("."),
                onPartialOutput = { state.partials += it },
            )

        assertFalse(keepGoing)
        assertEquals(emptyList<Map<String, Any>>(), state.messages)
    }

    @Test
    fun `a response without a content list ends the loop`() {
        val state = State()

        assertFalse(loop().processToolLoopIteration(1, """{"content":"text"}""", state.messages, state.allText, File(".")))
        assertEquals(emptyList<Map<String, Any>>(), state.messages)
    }

    @Test
    fun `items that are not objects and non textual blocks are ignored`() {
        val state = State()
        val json =
            """
            {"stop_reason":null,"content":[
              "not-an-object",
              {"type":"text"},
              {"type":"text","text":"kept "},
              {"type":"tool_use","name":"write","input":{"path":"a.txt","content":"x"}},
              {"type":"tool_use","id":"tu-2"},
              {"type":"tool_use","id":"tu-3","name":"read","input":"not-a-map"}
            ]}
            """.trimIndent()

        val keepGoing =
            loop().processToolLoopIteration(
                iteration = 2,
                respJson = json,
                messages = state.messages,
                allText = state.allText,
                repoDir = File("."),
                onPartialOutput = { state.partials += it },
            )

        assertTrue(keepGoing)
        assertEquals(listOf("kept"), state.partials)
        val assistant = state.messages.first { it["role"] == "assistant" }

        @Suppress("UNCHECKED_CAST")
        val blocks = assistant["content"] as List<Map<String, Any>>
        assertEquals(listOf("tu-3"), blocks.filter { it["type"] == "tool_use" }.map { it["id"] })
    }

    @Test
    fun `a tool call without a name is dropped and the tool result is answered`() {
        val state = State()
        val json =
            """
            {"stop_reason":"tool_use","content":[
              {"type":"tool_use","id":"tu-1","input":{"path":"written.txt","content":"payload"}}
            ]}
            """.trimIndent()

        val keepGoing =
            loop().processToolLoopIteration(
                iteration = 1,
                respJson = json,
                messages = state.messages,
                allText = state.allText,
                repoDir = File("."),
                onPartialOutput = {},
            )

        assertFalse(keepGoing)
        assertEquals(emptyList<Map<String, Any>>(), state.messages)
    }

    @Test
    fun `a write tool call creates the file inside the repository`(
        @TempDir tmp: Path,
    ) {
        val state = State()
        val json =
            """
            {"stop_reason":"tool_use","content":[
              {"type":"tool_use","id":"tu-9","name":"write","input":{"path":"src/Written.kt","content":"fun written() = 1"}}
            ]}
            """.trimIndent()

        val keepGoing =
            loop().processToolLoopIteration(
                iteration = 1,
                respJson = json,
                messages = state.messages,
                allText = state.allText,
                repoDir = tmp.toFile(),
                onPartialOutput = {},
            )

        assertTrue(keepGoing)
        assertEquals("fun written() = 1", File(tmp.toFile(), "src/Written.kt").readText())
        @Suppress("UNCHECKED_CAST")
        val result = (state.messages.last()["content"] as List<Map<String, Any>>).single()
        assertEquals("tu-9", result["tool_use_id"])
        assertEquals("OK: written 17 chars to src/Written.kt", result["content"])
    }

    @Test
    fun `a token limit is reported as truncated output instead of a finished answer`() {
        val state = State()
        val json =
            """
            {"stop_reason":"max_tokens","usage":{"output_tokens":8192,"output_tokens_details":{"thinking_tokens":8192}},"content":[
              {"type":"tool_use","id":"tu-1","name":"read","input":{"path":"missing.kt"}}
            ]}
            """.trimIndent()

        val failure =
            assertThrows(StackLensFailureException::class.java) {
                loop().processToolLoopIteration(
                    iteration = 1,
                    respJson = json,
                    messages = state.messages,
                    allText = state.allText,
                    repoDir = File("."),
                    onPartialOutput = {},
                )
            }

        assertEquals(StackLensFailure.AiOutputTruncated(8192, 8192, 0), failure.failure)
        assertEquals(emptyList<Map<String, Any>>(), state.messages)
    }

    @Test
    fun `every other terminal stop reason ends the loop even with pending tool calls`() {
        listOf("end_turn", "stop_sequence").forEach { reason ->
            val state = State()
            val json =
                """
                {"stop_reason":"$reason","content":[
                  {"type":"tool_use","id":"tu-1","name":"read","input":{"path":"missing.kt"}}
                ]}
                """.trimIndent()

            val keepGoing =
                loop().processToolLoopIteration(
                    iteration = 1,
                    respJson = json,
                    messages = state.messages,
                    allText = state.allText,
                    repoDir = File("."),
                    onPartialOutput = {},
                )

            assertFalse(keepGoing, "stop reason $reason must terminate the loop")
        }
    }

    @Test
    fun `runLoop collects the final text and reports it through the completion callback`() {
        val responses =
            ArrayDeque(
                listOf(
                    """{"stop_reason":"tool_use","content":[{"type":"tool_use","id":"tu-1","name":"list","input":{"path":"."}}]}""",
                    """{"stop_reason":"end_turn","content":[{"type":"text","text":"final answer"}]}""",
                ),
            )
        var completed: String? = null
        val loop = AnthropicToolLoop(objectMapper, transport)

        val text =
            loop.runLoop(
                model = "claude",
                systemPrompt = "system",
                userPrompt = "start",
                timeoutSeconds = 5L,
                repoDir = File("."),
                onPartialOutput = {},
                buildRequestBody = { """{"messages":[]}""" },
                sendRequest = { _, _ -> responses.removeFirst() },
                onComplete = { completed = it },
            )

        assertEquals("final answer", text)
        assertEquals("final answer", completed)
    }

    @Test
    fun `runLoop stops when the iteration budget is exhausted`() {
        var sent = 0
        val loop = AnthropicToolLoop(objectMapper, transport, maxIterations = 2)

        val text =
            loop.runLoop(
                model = "claude",
                systemPrompt = "system",
                userPrompt = "start",
                timeoutSeconds = 5L,
                repoDir = File("."),
                onPartialOutput = {},
                buildRequestBody = {
                    sent++
                    """{"messages":[]}"""
                },
                sendRequest = { _, _ -> """{"stop_reason":"tool_use","content":[{"type":"tool_use","id":"tu","name":"list","input":{}}]}""" },
            )

        assertEquals(2, sent)
        assertEquals("", text)
    }

    @Test
    fun `runLoop reports a cancellation before the first request`() {
        val loop = AnthropicToolLoop(objectMapper, transport, maxIterations = 1)

        val failure =
            runCatching {
                loop.runLoop(
                    model = "claude",
                    systemPrompt = "system",
                    userPrompt = "start",
                    timeoutSeconds = 5L,
                    repoDir = File("."),
                    onPartialOutput = {},
                    buildRequestBody = { "{}" },
                    sendRequest = { _, _ -> "{}" },
                    isCancelled = { true },
                )
            }.exceptionOrNull()

        assertTrue(failure is InterruptedException || failure is java.util.concurrent.CancellationException, "unexpected: $failure")
    }
}
