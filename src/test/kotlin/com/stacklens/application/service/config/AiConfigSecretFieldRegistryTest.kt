package com.stacklens.application.service.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AiConfigSecretFieldRegistryTest {
    @Test
    fun `every built-in secret field exposes its identifier`() {
        assertEquals("github", GithubTokenSecretField().id)
        assertEquals("openai", OpenAiSecretField().id)
        assertEquals("openrouter", OpenRouterSecretField().id)
        assertEquals("claudecode", ClaudeCodeSecretField().id)
        assertEquals("compatible", CompatibleSecretField().id)
    }

    @Test
    fun `the default registry contains the built-in fields in order`() {
        val ids = AiConfigSecretFieldRegistry().fields().map { it.id }

        assertEquals(listOf("github", "openai", "openrouter", "claudecode", "compatible"), ids)
    }
}
