package com.stacklens.adapter.out.ai

import com.stacklens.adapter.out.ai.OpenAiCompatibleToolLoop.ContentNormalizer.Companion.LM_STUDIO_THINKING_RESPONSE
import com.stacklens.adapter.out.ai.OpenAiCompatibleToolLoop.ContentNormalizer.Companion.THINK_BLOCK
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiCompatibleToolLoopContentNormalizerCompanionTest {
    @Test
    fun `THINK_BLOCK getter returns the regex instance`() {
        val r = THINK_BLOCK
        assertNotNull(r)
        assertTrue(r.containsMatchIn("<think>hidden</think>visible"))
    }

    @Test
    fun `LM_STUDIO_THINKING_RESPONSE getter returns the regex instance`() {
        val r = LM_STUDIO_THINKING_RESPONSE
        assertNotNull(r)
        assertTrue(r.containsMatchIn(" thinking hidden response visible"))
    }
}
