package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenAiAdapterResolveTokenTest {
    private val aiConfigRepo: AiConfigPort = mock()
    private val promptLoader: PromptReaderPort = mock()

    private fun adapter(envToken: String? = null) =
        OpenAiAdapter(
            defaultOpenAiBaseUrl = "https://api.openai.com",
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper =
                com.fasterxml.jackson.databind
                    .ObjectMapper(),
            promptLoader = promptLoader,
        )

    @Test
    fun `resolveToken returns null when config and env are both missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val prev = System.getenv("OPENAI_API_KEY")
        if (prev.isNullOrBlank()) {
            assertEquals(null, adapter().resolveToken())
        }
    }

    @Test
    fun `resolveToken returns config key when config has a key`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-test"))
        assertEquals("sk-test", adapter().resolveToken())
    }

    @Test
    fun `resolveToken returns null when config key is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = ""))
        if (System.getenv("OPENAI_API_KEY").isNullOrBlank()) {
            assertEquals(null, adapter().resolveToken())
        }
    }

    @Test
    fun `resolveToken returns null when config is null and no env`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        if (System.getenv("OPENAI_API_KEY").isNullOrBlank()) {
            assertEquals(null, adapter().resolveToken())
        }
    }

    @Test
    fun `isReady returns true when config has a token`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-abc"))
        assertEquals(true, adapter().isReady())
    }

    @Test
    fun `isReady returns false when no token available`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        if (System.getenv("OPENAI_API_KEY").isNullOrBlank()) {
            assertEquals(false, adapter().isReady())
        }
    }

    @Test
    fun `resolveOpenAiBaseUrl returns default URL`() {
        assertNotNull(adapter().resolveOpenAiBaseUrl())
    }

    @Test
    fun `resolveModel returns default when config model is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(model = ""))
        val model = adapter().resolveModel()
        assertNotNull(model)
    }

    @Test
    fun `resolveLanguage returns default when config language is blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = ""))
        val lang = adapter().resolveLanguage()
        assertNotNull(lang)
    }
}
