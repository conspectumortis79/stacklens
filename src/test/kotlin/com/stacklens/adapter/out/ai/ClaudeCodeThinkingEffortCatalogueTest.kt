package com.stacklens.adapter.out.ai

import com.stacklens.infrastructure.config.ClaudeThinkingProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClaudeCodeThinkingEffortCatalogueTest {
    @Test
    fun `the adapter and the settings advertise the same thinking efforts`() {
        val expected = listOf("low", "medium", "high", "xhigh", "max")

        assertEquals(expected, ClaudeCodeAdapter.SUPPORTED_THINKING_EFFORTS)
        assertEquals(ClaudeCodeAdapter.SUPPORTED_THINKING_EFFORTS, ClaudeThinkingProperties.SUPPORTED_EFFORTS)
        assertEquals(ClaudeThinkingProperties.DEFAULT_EFFORT, expected.first())
        assertEquals(ClaudeThinkingProperties.DEFAULT_MODE, ClaudeThinkingProperties.MODE_ADAPTIVE)
    }
}
