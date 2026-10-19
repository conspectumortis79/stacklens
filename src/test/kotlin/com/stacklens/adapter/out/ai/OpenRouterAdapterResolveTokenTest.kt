package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenRouterAdapterResolveTokenTest {
    private val aiConfigRepo: AiConfigPort = mock()
    private val promptLoader: PromptReaderPort = mock()

    private fun adapter() =
        OpenRouterAdapter(
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = ObjectMapper(),
            promptLoader = promptLoader,
        )

    @Test
    fun `resolveToken returns config key when config has a key`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openRouterApiKey = "sk-or-test"))
        assertEquals("sk-or-test", adapter().resolveToken())
    }

    @Test
    fun `resolveToken returns null when config key is blank and no env`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openRouterApiKey = ""))
        if (System.getenv("OPENROUTER_API_KEY").isNullOrBlank()) {
            assertEquals(null, adapter().resolveToken())
        }
    }

    @Test
    fun `resolveToken returns null when config is null and no env`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        if (System.getenv("OPENROUTER_API_KEY").isNullOrBlank()) {
            assertEquals(null, adapter().resolveToken())
        }
    }

    @Test
    fun `isReady returns true when config has a key`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openRouterApiKey = "sk-or-abc"))
        assertEquals(true, adapter().isReady())
    }

    @Test
    fun `isReady returns false when no token available`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        if (System.getenv("OPENROUTER_API_KEY").isNullOrBlank()) {
            assertEquals(false, adapter().isReady())
        }
    }

    @Test
    fun `resolveModel returns default when config model is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(model = ""))
        val model = adapter().resolveModel()
        assertEquals(true, model.isNotEmpty())
    }

    @Test
    fun `resolveLanguage returns default when config language is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = ""))
        val lang = adapter().resolveLanguage()
        assertEquals(true, lang.isNotEmpty())
    }
}
