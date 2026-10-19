package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ClaudeCodeAdapterResolveTokenTest {
    private val aiConfigRepo: AiConfigPort = mock()

    private val promptLoader: PromptReaderPort = mock()

    private fun adapter(
        token: String? = null,
        defaultModel: String = ClaudeCodeTestSupport.DEFAULT_MODEL,
    ) = ClaudeCodeAdapter(
        defaultBaseUrl = "https://api.anthropic.com",
        timeoutSeconds = 10L,
        aiConfigRepo = aiConfigRepo,
        objectMapper = ObjectMapper(),
        promptLoader = promptLoader,
        tokenLookup = ClaudeCodeTestSupport.tokenLookup(token),
        settings = ClaudeCodeTestSupport.settings(defaultModel = defaultModel),
    )

    @Test
    fun `resolveToken forwards whatever the lookup port resolves`() {
        assertEquals("sk-ant-test", adapter(token = "sk-ant-test").resolveToken())
        assertNull(adapter().resolveToken())
    }

    @Test
    fun `resolveToken trims a padded token from the lookup port`() {
        assertEquals("sk-ant-padded", adapter(token = "  sk-ant-padded\n").resolveToken())
    }

    @Test
    fun `a whitespace only token is not a token`() {
        assertNull(adapter(token = "   ").resolveToken())
    }

    @Test
    fun `isReady follows the resolved token`() {
        assertEquals(true, adapter(token = "sk-ant-abc").isReady())
        assertEquals(false, adapter().isReady())
    }

    @Test
    fun `resolveModel prefers the configured model over the configured default`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(model = "claude-opus-4-5"))

        assertEquals("claude-opus-4-5", adapter(defaultModel = "claude-sonnet-4-5").resolveModel())
    }

    @Test
    fun `resolveModel falls back to the configured default model`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(model = " "))

        assertEquals("claude-haiku-4-5", adapter(defaultModel = "claude-haiku-4-5").resolveModel())
    }

    @Test
    fun `resolveModel without an ai config row uses the configured default`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)

        assertEquals("claude-sonnet-4-5", adapter().resolveModel())
    }

    @Test
    fun `resolveLanguage falls back to german without a configured language`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(language = ""))

        assertNotNull(adapter().resolveLanguage())
    }

    @Test
    fun `the base url and the provider label stay stable`() {
        val adapter = adapter()

        assertEquals("https://api.anthropic.com", adapter.resolveBaseUrl())
        assertEquals("ClaudeCode", adapter.providerLabel)
    }
}
