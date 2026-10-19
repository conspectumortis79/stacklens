package com.stacklens.adapter.out.ai

import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OpenAiCompatibleEndpointTest {
    private fun endpoint(
        fallbackBaseUrl: String = "https://fallback.test/v1",
        envVarNames: List<String> = emptyList(),
        envValues: Map<String, String> = emptyMap(),
        apiKeyOf: (AiConfig?) -> String? = { config -> config?.compatibleApiKey },
        baseUrlOf: (AiConfig?) -> String? = { config -> config?.compatibleBaseUrl },
    ) = OpenAiCompatibleEndpoint(
        label = "OpenAI-compatible",
        fallbackBaseUrl = fallbackBaseUrl,
        defaultModel = "example-model",
        envVarNames = envVarNames,
        apiKeyOf = apiKeyOf,
        baseUrlOf = baseUrlOf,
        envReader = { name -> envValues[name] },
    )

    @Test
    fun `resolveApiKey returns the stored key`() {
        assertEquals("sk-stored", endpoint().resolveApiKey(AiConfig(compatibleApiKey = "sk-stored")))
    }

    @Test
    fun `resolveApiKey falls back to the environment in declaration order`() {
        val subject =
            endpoint(
                envVarNames = listOf("PRIMARY_KEY", "SECONDARY_KEY"),
                envValues = mapOf("SECONDARY_KEY" to "sk-secondary"),
            )
        assertEquals("sk-secondary", subject.resolveApiKey(AiConfig(compatibleApiKey = "   ")))
    }

    @Test
    fun `resolveApiKey prefers the stored key over the environment`() {
        val subject =
            endpoint(
                envVarNames = listOf("PRIMARY_KEY"),
                envValues = mapOf("PRIMARY_KEY" to "sk-env"),
            )
        assertEquals("sk-stored", subject.resolveApiKey(AiConfig(compatibleApiKey = "sk-stored")))
    }

    @Test
    fun `resolveApiKey returns null without config and without environment`() {
        assertNull(endpoint(envVarNames = listOf("PRIMARY_KEY")).resolveApiKey(null))
    }

    @Test
    fun `resolveApiKey tolerates a missing config`() {
        val subject = endpoint(envVarNames = listOf("PRIMARY_KEY"), envValues = mapOf("PRIMARY_KEY" to "sk-env"))
        assertEquals("sk-env", subject.resolveApiKey(null))
    }

    @Test
    fun `resolveBaseUrl prefers the stored url`() {
        val config = AiConfig(compatibleBaseUrl = "http://localhost:8000/v1/")
        assertEquals("http://localhost:8000/v1", endpoint().resolveBaseUrl(config))
    }

    @Test
    fun `resolveBaseUrl falls back when no url is stored`() {
        assertEquals("https://fallback.test/v1", endpoint().resolveBaseUrl(AiConfig()))
        assertEquals("https://fallback.test/v1", endpoint().resolveBaseUrl(null))
    }

    @Test
    fun `resolveBaseUrl ignores a blank stored url`() {
        assertEquals("https://fallback.test/v1", endpoint().resolveBaseUrl(AiConfig(compatibleBaseUrl = "  ")))
    }

    @Test
    fun `the openrouter endpoint preset keeps its own key field and headers`() {
        val subject =
            OpenAiCompatibleEndpoint(
                label = "OpenRouter",
                fallbackBaseUrl = "https://openrouter.ai/api/v1",
                defaultModel = "openai/gpt-4.1",
                extraHeaders = mapOf("X-Title" to "StackLens"),
                apiKeyOf = { config -> config?.openRouterApiKey },
            )
        assertEquals("sk-or", subject.resolveApiKey(AiConfig(openRouterApiKey = "sk-or")))
        assertEquals("https://openrouter.ai/api/v1", subject.resolveBaseUrl(AiConfig()))
        assertEquals(mapOf("X-Title" to "StackLens"), subject.extraHeaders)
    }
}
