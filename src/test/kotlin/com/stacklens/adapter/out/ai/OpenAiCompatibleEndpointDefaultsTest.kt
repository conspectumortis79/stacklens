package com.stacklens.adapter.out.ai

import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiCompatibleEndpointDefaultsTest {
    @Test
    fun `an endpoint without accessors resolves no key and the fallback url`() {
        val endpoint =
            OpenAiCompatibleEndpoint(
                label = "Local gateway",
                fallbackBaseUrl = "http://127.0.0.1:8080/v1",
                defaultModel = "small-model",
            )

        assertEquals("Local gateway", endpoint.label)
        assertEquals("http://127.0.0.1:8080/v1", endpoint.fallbackBaseUrl)
        assertEquals("small-model", endpoint.defaultModel)
        assertTrue(endpoint.nonToolCapablePatterns.isEmpty())
        assertTrue(endpoint.extraHeaders.isEmpty())
        assertTrue(endpoint.envVarNames.isEmpty())
        assertNull(endpoint.apiKeyOf(AiConfig()))
        assertNull(endpoint.baseUrlOf(AiConfig()))
        assertEquals(System.getenv("PATH"), endpoint.envReader("PATH"))
        assertEquals("http://127.0.0.1:8080/v1", endpoint.resolveBaseUrl(AiConfig()))
        assertNull(endpoint.resolveApiKey(AiConfig()))
    }

    @Test
    fun `the fallback url is trimmed and defaulted to https`() {
        val endpoint =
            OpenAiCompatibleEndpoint(
                label = "gateway",
                fallbackBaseUrl = "  gateway.test/v1/  ",
                defaultModel = "m",
            )

        assertEquals("https://gateway.test/v1", endpoint.resolveBaseUrl(null))
    }

    @Test
    fun `configured accessors and environment variables are preferred in order`() {
        val readEnvironment = mutableListOf<String>()
        val endpoint =
            OpenAiCompatibleEndpoint(
                label = "gateway",
                fallbackBaseUrl = "https://fallback.test/v1",
                defaultModel = "m",
                nonToolCapablePatterns = listOf("legacy"),
                extraHeaders = mapOf("X-Tenant" to "acme"),
                envVarNames = listOf("SECOND_KEY", "FIRST_KEY"),
                apiKeyOf = { config -> config?.openAiApiKey },
                baseUrlOf = { config -> config?.compatibleBaseUrl },
                envReader = { name ->
                    readEnvironment += name
                    when (name) {
                        "FIRST_KEY" -> "  "
                        "SECOND_KEY" -> "second"
                        else -> null
                    }
                },
            )
        val config = AiConfig(openAiApiKey = "   ", compatibleBaseUrl = " https://configured.test ")

        assertEquals("second", endpoint.resolveApiKey(config))
        assertEquals(listOf("SECOND_KEY", "FIRST_KEY"), readEnvironment)
        assertEquals("https://configured.test", endpoint.resolveBaseUrl(config))
        assertEquals(listOf("legacy"), endpoint.nonToolCapablePatterns)
        assertEquals(mapOf("X-Tenant" to "acme"), endpoint.extraHeaders)
    }

    @Test
    fun `a configured key is used before any environment variable`() {
        val endpoint =
            OpenAiCompatibleEndpoint(
                label = "gateway",
                fallbackBaseUrl = "https://fallback.test/v1",
                defaultModel = "m",
                envVarNames = listOf("UNUSED_KEY"),
                apiKeyOf = { "configured" },
            )

        assertEquals("configured", endpoint.resolveApiKey(null))
    }
}
