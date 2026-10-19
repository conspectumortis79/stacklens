package com.stacklens.application.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class CopilotAgentPipelineProviderTest : CopilotAgentPipelineServiceTestBase() {
    @Test
    fun `getActiveAiProvider returns null when no AI config exists`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(null)
        val svc = makeService()
        assertNull(svc.getActiveAiProvider())
    }

    @Test
    fun `getActiveAiProvider returns lowercased trimmed provider from config`() {
        val config =
            com.stacklens.domain.model
                .AiConfig(id = 1L, aiProvider = "  OpenAI  ")
        whenever(aiConfigPort.findAiConfig()).thenReturn(config)
        val svc = makeService()
        assertEquals("openai", svc.getActiveAiProvider())
    }

    @Test
    fun `getActiveAiProvider returns copilot for copilot provider`() {
        val config =
            com.stacklens.domain.model
                .AiConfig(id = 1L, aiProvider = "copilot")
        whenever(aiConfigPort.findAiConfig()).thenReturn(config)
        val svc = makeService()
        assertEquals("copilot", svc.getActiveAiProvider())
    }
}
