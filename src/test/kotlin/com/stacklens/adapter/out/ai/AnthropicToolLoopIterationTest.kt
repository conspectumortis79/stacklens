package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.failure.StackLensFailure
import com.stacklens.domain.failure.StackLensFailureException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class AnthropicToolLoopIterationTest {
    private val mapper = ObjectMapper()
    private val transport = FakeAnthropicTransport()
    private val loop = AnthropicToolLoop(mapper, transport)

    private class FakeAnthropicTransport : AnthropicToolLoop.ChatTransport {
        override val providerLabel: String = "Anthropic"

        override fun send(
            body: String,
            timeoutSeconds: Long,
        ): String = "{}"
    }

    private fun response(
        content: String,
        stopReason: String? = null,
    ): String = """{"content":[$content],"stop_reason":${stopReason ?: "null"}}"""

    private fun iterate(
        respJson: String,
        repoDir: File,
        onPartialOutput: (String) -> Unit = {},
    ): Pair<Boolean, StringBuilder> {
        val messages = mutableListOf<Map<String, Any>>()
        val allText = StringBuilder()
        val keepGoing =
            loop.processToolLoopIteration(
                iteration = 1,
                respJson = respJson,
                messages = messages,
                allText = allText,
                repoDir = repoDir,
                onPartialOutput = onPartialOutput,
            )
        return keepGoing to allText
    }

    @Test
    fun `the loop keeps the transport it was wired with`() {
        assertSame(transport, loop.transport)
    }

    @Test
    fun `a body that is no json ends the loop`(
        @TempDir dir: Path,
    ) {
        assertFalse(iterate("not json", dir.toFile()).first)
    }

    @Test
    fun `a response without a content list ends the loop`(
        @TempDir dir: Path,
    ) {
        assertFalse(iterate("""{"content":"text"}""", dir.toFile()).first)
    }

    @Test
    fun `text blocks are collected and reported as partial output`(
        @TempDir dir: Path,
    ) {
        val parts = mutableListOf<String>()

        val (keepGoing, allText) =
            iterate(response("""{"type":"text","text":"hello "}"""), dir.toFile()) { parts.add(it) }

        assertFalse(keepGoing)
        assertEquals(listOf("hello"), parts)
        assertEquals("hello", allText.toString().trim())
    }

    @Test
    fun `a failing partial output consumer does not break the iteration`(
        @TempDir dir: Path,
    ) {
        val (keepGoing, allText) =
            iterate(response("""{"type":"text","text":"hello"}"""), dir.toFile()) {
                throw IllegalStateException("consumer is gone")
            }

        assertFalse(keepGoing)
        assertEquals("hello", allText.toString().trim())
    }

    @Test
    fun `blank text produces no partial output and no appended block`(
        @TempDir dir: Path,
    ) {
        val parts = mutableListOf<String>()

        val (keepGoing, allText) =
            iterate(response("""{"type":"text","text":"   "}"""), dir.toFile()) { parts.add(it) }

        assertFalse(keepGoing)
        assertTrue(parts.isEmpty())
        assertTrue(allText.isEmpty())
    }

    @Test
    fun `content entries that are no objects are skipped`(
        @TempDir dir: Path,
    ) {
        assertFalse(iterate(response("42"), dir.toFile()).first)
    }

    @Test
    fun `an unknown content block type is ignored`(
        @TempDir dir: Path,
    ) {
        assertFalse(iterate(response("""{"type":"thinking","text":"hmm"}"""), dir.toFile()).first)
    }

    @Test
    fun `a text block without text is ignored`(
        @TempDir dir: Path,
    ) {
        assertFalse(iterate(response("""{"type":"text"}"""), dir.toFile()).first)
    }

    @Test
    fun `a tool call without an id cannot be executed`(
        @TempDir dir: Path,
    ) {
        val respJson = response("""{"type":"tool_use","name":"list"}""", "\"tool_use\"")

        assertFalse(iterate(respJson, dir.toFile()).first)
    }

    @Test
    fun `a tool call without a name cannot be executed`(
        @TempDir dir: Path,
    ) {
        val respJson = response("""{"type":"tool_use","id":"t1"}""", "\"tool_use\"")

        assertFalse(iterate(respJson, dir.toFile()).first)
    }

    @Test
    fun `a completed turn stops the loop even when tools were requested`(
        @TempDir dir: Path,
    ) {
        val respJson = response("""{"type":"tool_use","id":"t1","name":"list","input":{}}""", "\"end_turn\"")

        assertFalse(iterate(respJson, dir.toFile()).first)
    }

    @Test
    fun `a tool call is executed and answered so the loop continues`(
        @TempDir dir: Path,
    ) {
        val respJson = response("""{"type":"tool_use","id":"t1","name":"list","input":{"path":"."}}""", "\"tool_use\"")

        assertTrue(iterate(respJson, dir.toFile()).first)
    }

    @Test
    fun `a tool call without an input object receives empty arguments`(
        @TempDir dir: Path,
    ) {
        val respJson = response("""{"type":"tool_use","id":"t9","name":"unknown-tool"}""", "\"tool_use\"")

        assertTrue(iterate(respJson, dir.toFile()).first)
    }

    @Test
    fun `text and tool calls in one turn are both forwarded`(
        @TempDir dir: Path,
    ) {
        val respJson =
            response(
                """{"type":"text","text":"working"},{"type":"tool_use","id":"t1","name":"unknown","input":{"a":1}}""",
                "\"tool_use\"",
            )

        val (keepGoing, allText) = iterate(respJson, dir.toFile())

        assertTrue(keepGoing, respJson)
        assertEquals("working", allText.toString().trim())
    }

    @Test
    fun `a stop sequence ends the loop while a token limit reports truncation`(
        @TempDir dir: Path,
    ) {
        val respJson = """{"type":"tool_use","id":"t1","name":"list","input":{}}"""

        assertFalse(iterate(response(respJson, "\"stop_sequence\""), dir.toFile()).first)

        val failure =
            assertThrows(StackLensFailureException::class.java) {
                iterate(response(respJson, "\"max_tokens\""), dir.toFile())
            }
        assertEquals(StackLensFailure.AiOutputTruncated(0, 0, 0), failure.failure)
    }
}
