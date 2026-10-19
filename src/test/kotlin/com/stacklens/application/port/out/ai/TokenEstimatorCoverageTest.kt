package com.stacklens.application.port.out.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenEstimatorCoverageTest {
    @Test
    fun `Default estimator returns zero for empty input`() {
        assertEquals(0L, TokenEstimator.Default.estimateTokens(""))
    }

    @Test
    fun `Default estimator returns at least one for any non-empty input`() {
        assertEquals(1L, TokenEstimator.Default.estimateTokens("a"))
        assertEquals(1L, TokenEstimator.Default.estimateTokens("ab"))
        assertEquals(1L, TokenEstimator.Default.estimateTokens("abc"))
        assertEquals(1L, TokenEstimator.Default.estimateTokens("abcd"))
    }

    @Test
    fun `Default estimator applies the chars-per-token rule`() {
        assertEquals(1L, TokenEstimator.Default.estimateTokens("abcd"))
        assertEquals(2L, TokenEstimator.Default.estimateTokens("a".repeat(8)))
        assertEquals(4L, TokenEstimator.Default.estimateTokens("a".repeat(16)))
    }

    @Test
    fun `Default estimator never rounds down to zero for non-empty input`() {
        assertEquals(2L, TokenEstimator.Default.estimateTokens("abcde"))
    }

    @Test
    fun `Default estimator scales linearly`() {
        val a = TokenEstimator.Default.estimateTokens("a".repeat(100))
        val b = TokenEstimator.Default.estimateTokens("a".repeat(200))
        assertEquals(2 * a, b)
    }

    @Test
    fun `Default estimator is non-null`() {
        assertNotNull(TokenEstimator.Default)
    }

    @Test
    fun `Default estimator reports positive token counts for typical code blobs`() {
        val code = "fun main() { println(\"hello\") }"
        val tokens = TokenEstimator.Default.estimateTokens(code)
        assertTrue(tokens > 0L)
    }
}
