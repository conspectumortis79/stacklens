package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OpenAiCompatibleUsageTest {
    private val extractor = DefaultAiUsageExtractor(ObjectMapper())

    private val body =
        """
        {
          "choices": [{"message": {"content": "ok"}}],
          "usage": {"prompt_tokens": 3019, "completion_tokens": 104, "total_tokens": 3123}
        }
        """.trimIndent()

    @Test
    fun `the OpenAI-compatible provider label is tracked through the openai usage block`() {
        val usage = extractor.extract(body, "OpenAI-compatible")
        assertEquals(3019L, usage!!.promptTokens)
        assertEquals(104L, usage.completionTokens)
    }

    @Test
    fun `the openrouter label keeps being tracked through the openai usage block`() {
        val usage = extractor.extract(body, "OpenRouter")
        assertEquals(3019L, usage!!.promptTokens)
        assertEquals(104L, usage.completionTokens)
    }

    @Test
    fun `an unknown provider label still yields no usage`() {
        assertNull(extractor.extract(body, "Somethin-else"))
    }
}
