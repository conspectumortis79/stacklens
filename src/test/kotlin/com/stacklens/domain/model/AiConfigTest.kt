package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AiConfigTest {
    @Test
    fun `default constructor populates sensible defaults`() {
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
        assertNull(c.claudeCodeApiKey)
    }

    @Test
    fun `named constructor populates all fields`() {
        val c =
            AiConfig(
                id = 1L,
                githubToken = "tok",
                model = "gpt-4",
                language = "de",
                availableModels = "m1,m2",
                aiProvider = "openai",
                ollamaUrl = "http://x",
                ollamaNumCtx = 4096,
                lmStudioUrl = "http://lms",
                openAiApiKey = "sk-1",
                openAiReasoningEffort = "high",
                openRouterApiKey = "or-key",
                claudeCodeApiKey = "sk-ant-test",
            )
        assertEquals(1L, c.id)
        assertEquals("tok", c.githubToken)
        assertEquals("gpt-4", c.model)
        assertEquals("de", c.language)
        assertEquals("m1,m2", c.availableModels)
        assertEquals("openai", c.aiProvider)
        assertEquals("http://x", c.ollamaUrl)
        assertEquals(4096, c.ollamaNumCtx)
        assertEquals("http://lms", c.lmStudioUrl)
        assertEquals("sk-1", c.openAiApiKey)
        assertEquals("high", c.openAiReasoningEffort)
        assertEquals("or-key", c.openRouterApiKey)
        assertEquals("sk-ant-test", c.claudeCodeApiKey)
    }

    @Test
    fun `ollamaNumCtx default is 32768 not zero`() {
        val c = AiConfig()
        assertFalse(c.ollamaNumCtx == 0)
    }

    @Test
    fun `setAvailableModels updates the field via reflection`() {
        val c = AiConfig()
        val setter = AiConfig::class.java.getDeclaredMethod("setAvailableModels", String::class.java)
        setter.isAccessible = true
        setter.invoke(c, "[\"gpt-4\",\"claude\"]")
        assertEquals("[\"gpt-4\",\"claude\"]", c.availableModels)
    }
}
