package com.stacklens.domain.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiProviderTest {
    @Test
    fun `contains exactly the supported providers`() {
        assertEquals(
            listOf("copilot", "ollama", "lmstudio", "openai", "openrouter", "claudecode", "compatible"),
            AiProvider.entries.map { it.id },
        )
    }

    @Test
    fun `copilot has expected properties`() {
        assertEquals("copilot", AiProvider.COPILOT.id)
        assertEquals("gpt-5.4-mini", AiProvider.COPILOT.defaultModel)
        assertTrue(AiProvider.COPILOT.requiresToken)
    }

    @Test
    fun `ollama has expected properties`() {
        assertEquals("ollama", AiProvider.OLLAMA.id)
        assertEquals("gpt-5.4-mini", AiProvider.OLLAMA.defaultModel)
        assertFalse(AiProvider.OLLAMA.requiresToken)
    }

    @Test
    fun `lmstudio has expected properties`() {
        assertEquals("lmstudio", AiProvider.LMSTUDIO.id)
        assertEquals("local-model", AiProvider.LMSTUDIO.defaultModel)
        assertFalse(AiProvider.LMSTUDIO.requiresToken)
    }

    @Test
    fun `openai has expected properties`() {
        assertEquals("openai", AiProvider.OPENAI.id)
        assertEquals("gpt-5.2", AiProvider.OPENAI.defaultModel)
        assertTrue(AiProvider.OPENAI.requiresToken)
    }

    @Test
    fun `openrouter has expected properties`() {
        assertEquals("openrouter", AiProvider.OPENROUTER.id)
        assertEquals("openai/gpt-4.1", AiProvider.OPENROUTER.defaultModel)
        assertTrue(AiProvider.OPENROUTER.requiresToken)
    }

    @Test
    fun `claudecode has expected properties`() {
        assertEquals("claudecode", AiProvider.CLAUDECODE.id)
        assertEquals("claude-sonnet-4-5", AiProvider.CLAUDECODE.defaultModel)
        assertTrue(AiProvider.CLAUDECODE.requiresToken)
    }

    @Test
    fun `compatible has expected properties`() {
        assertEquals("compatible", AiProvider.COMPATIBLE.id)
        assertEquals("qwen3.8-flash", AiProvider.COMPATIBLE.defaultModel)
        assertTrue(AiProvider.COMPATIBLE.requiresToken)
    }

    @Test
    fun `compatible resolves case-insensitively and with whitespace`() {
        assertEquals(AiProvider.COMPATIBLE, AiProvider.fromId("Compatible"))
        assertEquals(AiProvider.COMPATIBLE, AiProvider.fromId("  compatible  "))
    }

    @Test
    fun `claudecode resolves case-insensitively`() {
        assertEquals(AiProvider.CLAUDECODE, AiProvider.fromId("ClaudeCode"))
        assertEquals(AiProvider.CLAUDECODE, AiProvider.fromId("claudecode"))
    }

    @Test
    fun `fromId resolves every id`() {
        for (provider in AiProvider.entries) {
            assertEquals(provider, AiProvider.fromId(provider.id))
        }
    }

    @Test
    fun `fromId is case-insensitive`() {
        assertEquals(AiProvider.LMSTUDIO, AiProvider.fromId("LMStudio"))
        assertEquals(AiProvider.COPILOT, AiProvider.fromId("COPILOT"))
    }

    @Test
    fun `fromId trims whitespace`() {
        assertEquals(AiProvider.OLLAMA, AiProvider.fromId("  ollama  "))
    }

    @Test
    fun `fromId returns null for null input`() {
        assertNull(AiProvider.fromId(null))
    }

    @Test
    fun `fromId returns null for unknown provider`() {
        assertNull(AiProvider.fromId("gemini"))
    }

    @Test
    fun `fromId returns null for blank input`() {
        assertNull(AiProvider.fromId(""))
        assertNull(AiProvider.fromId("   "))
    }

    @Test
    fun `valueOf resolves enum constants`() {
        assertEquals(AiProvider.OPENAI, AiProvider.valueOf("OPENAI"))
    }
}
