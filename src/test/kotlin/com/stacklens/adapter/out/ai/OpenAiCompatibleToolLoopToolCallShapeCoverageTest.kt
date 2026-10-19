package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class OpenAiCompatibleToolLoopToolCallShapeCoverageTest {
    private val objectMapper = ObjectMapper()
    private val repoDir = Files.createTempDirectory("tool-loop-shape").toFile()
    private val transport =
        object : OpenAiCompatibleToolLoop.ChatTransport {
            override val providerLabel: String = "compatible"

            override fun send(
                body: String,
                timeoutSeconds: Long,
            ): String = "{}"
        }
    private val loop = OpenAiCompatibleToolLoop(objectMapper, transport)

    private fun textFormContent(): String {
        val open = "<"
        val close = ">"
        return "${open}function=list$close${open}parameter=path$close.$open/parameter$close$open/function$close"
    }

    private fun structuredCall(
        id: String?,
        name: String?,
        arguments: String?,
    ): Map<String, Any?> {
        val function = mutableMapOf<String, Any?>()
        if (name != null) function["name"] = name
        if (arguments != null) function["arguments"] = arguments
        return mutableMapOf<String, Any?>().apply {
            if (id != null) put("id", id)
            put("function", function)
        }
    }

    private fun runIteration(
        finishReason: String?,
        content: String? = null,
        toolCalls: List<Any?>? = null,
        messages: MutableList<Map<String, Any>> = mutableListOf(),
        allText: StringBuilder = StringBuilder(),
        onPartial: ((String) -> Unit)? = null,
    ): Boolean {
        val message = mutableMapOf<String, Any?>("role" to "assistant", "content" to content)
        if (toolCalls != null) message["tool_calls"] = toolCalls
        val choice = mapOf<String, Any?>("message" to message, "finish_reason" to finishReason)
        val json = objectMapper.writeValueAsString(mapOf("choices" to listOf(choice)))
        return if (onPartial == null) {
            loop.processToolLoopIteration(1, json, messages, allText, repoDir)
        } else {
            loop.processToolLoopIteration(1, json, messages, allText, repoDir, onPartial)
        }
    }

    @Test
    fun `structured tool calls with a stop finish reason end the loop`() {
        val messages = mutableListOf<Map<String, Any>>()

        val continued = runIteration("stop", "done", listOf(structuredCall("c1", "list", null)), messages)

        assertFalse(continued)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `structured tool calls with a tool finish reason continue the loop`() {
        val messages = mutableListOf<Map<String, Any>>()

        val continued = runIteration("tool_calls", "working", listOf(structuredCall("c1", "list", null)), messages)

        assertTrue(continued)
        assertEquals(2, messages.size)
        assertEquals("assistant", messages[0]["role"])
        assertEquals("c1", messages[1]["tool_call_id"])
    }

    @Test
    fun `an anonymous structured call gets a generated id and empty arguments`() {
        val messages = mutableListOf<Map<String, Any>>()

        val continued = runIteration("tool_calls", null, listOf(structuredCall(null, "list", "{}")), messages)

        assertTrue(continued)
        assertEquals("call_1", messages[1]["tool_call_id"])
    }

    @Test
    fun `unparsable arguments and malformed entries are skipped`() {
        val messages = mutableListOf<Map<String, Any>>()
        val calls =
            listOf<Any?>(
                "not-a-map",
                mapOf("id" to "x"),
                structuredCall("c2", null, null),
                structuredCall("c3", "list", "broken json {"),
            )

        val continued = runIteration("tool_calls", null, calls, messages)

        assertTrue(continued)
        assertEquals(2, messages.size)
        assertEquals("c3", messages[1]["tool_call_id"])
    }

    @Test
    fun `text form tool calls keep the loop running even with a stop finish reason`() {
        val messages = mutableListOf<Map<String, Any>>()

        val continued = runIteration("stop", textFormContent(), null, messages)

        assertTrue(continued)
        assertEquals("tool", messages[1]["role"])
    }

    @Test
    fun `an empty structured list falls back to text form parsing`() {
        val continued = runIteration("tool_calls", textFormContent(), emptyList())

        assertTrue(continued)
    }

    @Test
    fun `an empty structured list without text form calls ends the loop`() {
        assertFalse(runIteration("stop", "nothing to do here", emptyList()))
    }

    @Test
    fun `no tool calls at all ends the loop`() {
        assertFalse(runIteration(null, "plain answer"))
    }

    @Test
    fun `partial output is emitted for non blank content`() {
        var emitted = ""

        val continued = runIteration(null, "half a story", null, onPartial = { emitted = it })

        assertFalse(continued)
        assertEquals("half a story", emitted)
    }

    @Test
    fun `a malformed payload ends the loop`() {
        val messages = mutableListOf<Map<String, Any>>()

        assertFalse(loop.processToolLoopIteration(1, "{oops", messages, StringBuilder(), repoDir))
    }

    @Test
    fun `a payload without choices ends the loop`() {
        assertFalse(runIterationRaw("""{"result":1}"""))
    }

    @Test
    fun `a payload with a non object choice ends the loop`() {
        assertFalse(runIterationRaw("""{"choices":["text"]}"""))
    }

    @Test
    fun `a payload with a choice without a message ends the loop`() {
        assertFalse(runIterationRaw("""{"choices":[{"finish_reason":"stop"}]}"""))
    }

    private fun runIterationRaw(json: String): Boolean = loop.processToolLoopIteration(1, json, mutableListOf(), StringBuilder(), repoDir)
}
