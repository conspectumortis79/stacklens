package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class AnthropicToolLoopUnknownBlockCoverageTest {
    private val transport = mock<AnthropicToolLoop.ChatTransport>()

    init {
        whenever(transport.providerLabel).thenReturn("anthropic")
    }

    @Test
    fun `a content block of an unknown type is ignored and the loop stops`() {
        val response =
            """
            {"content":[{"type":"thinking","text":"reasoning"}],"stop_reason":"end_turn"}
            """.trimIndent()

        val continued =
            AnthropicToolLoop(ObjectMapper(), transport)
                .processToolLoopIteration(1, response, mutableListOf(), StringBuilder(), File(".")) { }

        assertFalse(continued)
    }

    @Test
    fun `a content block whose type is missing is ignored and the loop stops`() {
        val response = """{"content":[{"text":"no type"}],"stop_reason":"end_turn"}"""

        val continued =
            AnthropicToolLoop(ObjectMapper(), transport)
                .processToolLoopIteration(1, response, mutableListOf(), StringBuilder(), File(".")) { }

        assertFalse(continued)
    }
}
