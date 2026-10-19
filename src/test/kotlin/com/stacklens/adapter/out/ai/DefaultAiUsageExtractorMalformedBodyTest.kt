package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultAiUsageExtractorMalformedBodyTest {
    private val extractor = DefaultAiUsageExtractor()

    @Test
    fun `a blank body yields no usage`() {
        assertNull(extractor.extract("   ", "OpenAI"))
    }

    @Test
    fun `a body that cannot be parsed yields no usage`() {
        assertNull(extractor.extract("{ this is not json ]", "OpenAI"))
    }

    @Test
    fun `a parser that returns no node yields no usage`() {
        val mapper = mock<ObjectMapper>()
        whenever(mapper.readTree(any<String>())) doReturn null

        assertNull(DefaultAiUsageExtractor(mapper).extract("""{"usage":{}}""", "Ollama"))
    }

    @Test
    fun `an unknown provider label yields no usage`() {
        assertNull(extractor.extract("""{"usage":{"prompt_tokens":1,"completion_tokens":2}}""", "SelfManaged"))
    }

    @Test
    fun `claude usage is read from its own token fields`() {
        val usage =
            extractor.extract(
                """{"usage":{"input_tokens":11,"output_tokens":22}}""",
                "ClaudeCode",
            )

        assertEquals(11L, usage?.promptTokens)
        assertEquals(22L, usage?.completionTokens)
    }

    @Test
    fun `ollama falls back to its native counters when no usage block is present`() {
        val usage = extractor.extract("""{"prompt_eval_count":5,"eval_count":6}""", "ollama")

        assertEquals(5L, usage?.promptTokens)
        assertEquals(6L, usage?.completionTokens)
    }
}
