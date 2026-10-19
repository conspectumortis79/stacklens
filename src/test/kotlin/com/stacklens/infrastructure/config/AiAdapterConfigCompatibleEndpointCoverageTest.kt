package com.stacklens.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.ai.AiDispatcherAdapter
import com.stacklens.adapter.out.ai.OpenAiCompatibleAdapter
import com.stacklens.adapter.out.ai.OpenAiCompatibleEndpoint
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.domain.enums.AiProvider
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.DefaultResourceLoader

class AiAdapterConfigCompatibleEndpointCoverageTest {
    private fun compatibleEndpoint(): OpenAiCompatibleEndpoint {
        val config =
            AiAdapterConfig(
                objectMapper = ObjectMapper().findAndRegisterModules(),
                resourceLoader = DefaultResourceLoader(),
                stackLensProperties = StackLensProperties(),
            )
        val dispatcher =
            config.copilotStreamingPort(
                mock(),
                mock(),
                mock(),
                mock(),
                com.stacklens.adapter.out.ai.ClaudeCodeTestSupport
                    .tokenLookup(null),
                com.stacklens.adapter.out.ai.ClaudeCodeTestSupport
                    .settings(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            ) as AiDispatcherAdapter
        val providersField = AiDispatcherAdapter::class.java.declaredFields.first { Map::class.java.isAssignableFrom(it.type) }
        providersField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val providers = providersField.get(dispatcher) as Map<String, Any>
        val adapter = providers.getValue(AiProvider.COMPATIBLE.id) as OpenAiCompatibleAdapter
        val endpointField = OpenAiCompatibleAdapter::class.java.declaredFields.first { it.type == OpenAiCompatibleEndpoint::class.java }
        endpointField.isAccessible = true
        return endpointField.get(adapter) as OpenAiCompatibleEndpoint
    }

    @Test
    fun `the compatible credential lambdas accept a missing and a stored configuration`() {
        val endpoint = compatibleEndpoint()
        val repo = mock<AiConfigPort>()
        val stored = AiConfig(compatibleApiKey = "stored-key", compatibleBaseUrl = "https://stored")

        assertNull(endpoint.apiKeyOf(null))
        assertNull(endpoint.baseUrlOf(null))

        whenever(repo.findAiConfig()).thenReturn(stored)
        assertEquals("stored-key", endpoint.apiKeyOf(repo.findAiConfig()))
        assertEquals("https://stored", endpoint.baseUrlOf(repo.findAiConfig()))

        whenever(repo.findAiConfig()).thenReturn(null)
        assertNull(endpoint.apiKeyOf(repo.findAiConfig()))
        assertNull(endpoint.baseUrlOf(repo.findAiConfig()))
    }
}
