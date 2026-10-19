package com.stacklens.application.port.out.ai

import com.stacklens.domain.model.AiUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TokenEstimatorTest {
    private val est = TokenEstimator.Default

    @Test
    fun `empty string yields zero tokens`() {
        assertEquals(0L, est.estimateTokens(""))
    }

    @Test
    fun `one char yields one token (floor)`() {
        assertEquals(1L, est.estimateTokens("a"))
    }

    @Test
    fun `three chars still yields one token (floor)`() {
        assertEquals(1L, est.estimateTokens("abc"))
    }

    @Test
    fun `four chars yields one token`() {
        assertEquals(1L, est.estimateTokens("abcd"))
    }

    @Test
    fun `five chars yields two tokens (ceil)`() {
        assertEquals(2L, est.estimateTokens("abcde"))
    }

    @Test
    fun `eight chars yields two tokens`() {
        assertEquals(2L, est.estimateTokens("abcdefgh"))
    }

    @Test
    fun `nine chars yields three tokens (ceil)`() {
        assertEquals(3L, est.estimateTokens("abcdefghi"))
    }

    @Test
    fun `long prompt yields proportional estimate`() {
        val text = "a".repeat(1_000)
        assertEquals(250L, est.estimateTokens(text))
    }

    @Test
    fun `AiUsage CHARS_PER_TOKEN constant matches the formula`() {
        val text = "a".repeat(AiUsage.CHARS_PER_TOKEN)
        assertEquals(1L, est.estimateTokens(text))
    }
}
