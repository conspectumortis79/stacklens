package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AiConfigCoverageTest {
    @Test
    fun `AiConfig defaults are sensible`() {
        val c = AiConfig()
        assertEquals(1L, c.id)
        assertNull(c.githubToken)
        assertNull(c.model)
        assertEquals("en", c.language)
        assertNull(c.availableModels)
        assertEquals("copilot", c.aiProvider)
        assertNull(c.ollamaUrl)
        assertEquals(32768, c.ollamaNumCtx)
        assertNull(c.lmStudioUrl)
        assertNull(c.openAiApiKey)
        assertNull(c.openAiReasoningEffort)
        assertNull(c.openRouterApiKey)
    }

    @Test
    fun `AiConfig stores provided values verbatim`() {
        val c =
            AiConfig(
                id = 1L,
                githubToken = "tok",
                model = "gpt-4",
                language = "de",
                availableModels = "gpt-4,gpt-3.5",
                aiProvider = "openai",
                ollamaUrl = "http://localhost:11434",
                ollamaNumCtx = 8192,
                lmStudioUrl = "http://localhost:1234",
                openAiApiKey = "k",
                openAiReasoningEffort = "low",
                openRouterApiKey = "rk",
            )
        assertEquals("tok", c.githubToken)
        assertEquals("gpt-4", c.model)
        assertEquals("de", c.language)
        assertEquals("gpt-4,gpt-3.5", c.availableModels)
        assertEquals("openai", c.aiProvider)
        assertEquals("http://localhost:11434", c.ollamaUrl)
        assertEquals(8192, c.ollamaNumCtx)
        assertEquals("http://localhost:1234", c.lmStudioUrl)
        assertEquals("k", c.openAiApiKey)
        assertEquals("low", c.openAiReasoningEffort)
        assertEquals("rk", c.openRouterApiKey)
    }
}
