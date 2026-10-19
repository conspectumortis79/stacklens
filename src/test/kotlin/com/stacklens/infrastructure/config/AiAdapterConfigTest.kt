package com.stacklens.infrastructure.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AiAdapterConfigTest {
    private fun config(): AiAdapterConfig =
        AiAdapterConfig(
            objectMapper =
                com.fasterxml.jackson.databind
                    .ObjectMapper(),
            resourceLoader =
                org.springframework.core.io
                    .DefaultResourceLoader(),
            stackLensProperties = StackLensProperties(),
        )

    @Test
    fun `resolveOllamaUrl returns localhost default when envValue is null`() {
        assertEquals("http://localhost:11434", config().resolveOllamaUrl(null))
    }

    @Test
    fun `resolveOllamaUrl returns localhost default when envValue is blank`() {
        assertEquals("http://localhost:11434", config().resolveOllamaUrl(""))
        assertEquals("http://localhost:11434", config().resolveOllamaUrl("   "))
    }

    @Test
    fun `resolveOllamaUrl returns the envValue when non-blank`() {
        assertEquals("http://custom-host:9999", config().resolveOllamaUrl("http://custom-host:9999"))
    }

    @Test
    fun `resolveLmStudioUrl returns localhost default when envValue is null`() {
        assertEquals("http://localhost:1234", config().resolveLmStudioUrl(null))
    }

    @Test
    fun `resolveLmStudioUrl returns localhost default when envValue is blank`() {
        assertEquals("http://localhost:1234", config().resolveLmStudioUrl(""))
        assertEquals("http://localhost:1234", config().resolveLmStudioUrl("   "))
    }

    @Test
    fun `resolveLmStudioUrl returns the envValue when non-blank`() {
        assertEquals("http://lm-studio-host:8080", config().resolveLmStudioUrl("http://lm-studio-host:8080"))
    }
}
