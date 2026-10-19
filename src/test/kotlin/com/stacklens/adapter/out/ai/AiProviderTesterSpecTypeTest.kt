package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.web.client.RestTemplate

class AiProviderTesterSpecTypeTest {
    private val restTemplate = mock<RestTemplate>()
    private val i18n = mock<I18nPort>()

    @Test
    fun `each connection tester declares the spec it serves`() {
        assertEquals(ProviderSpec.LmStudio::class, LmStudioConnectionTester(i18n, com.stacklens.testsupport.FakeContainerRuntimeProbePort(), mock()).specType)
        assertEquals(ProviderSpec.Ollama::class, OllamaConnectionTester(restTemplate, i18n).specType)
        assertEquals(ProviderSpec.OpenRouter::class, OpenRouterConnectionTester(restTemplate, i18n).specType)
        assertEquals(ProviderSpec.OpenAi::class, OpenAiConnectionTester(i18n, mock()).specType)
        assertEquals(ProviderSpec.CopilotModel::class, CopilotModelConnectionTester(mock<CopilotPort>(), i18n).specType)
    }
}
