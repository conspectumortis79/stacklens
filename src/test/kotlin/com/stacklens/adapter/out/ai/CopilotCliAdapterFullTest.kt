package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CopilotCliAdapterFullTest {
    private val aiConfigRepo: AiConfigPort = mock()

    private fun adapter() =
        CopilotCliAdapter(
            configuredBinary = "copilot",
            legacyCliBin = "github-copilot-cli",
            timeoutSeconds = 10L,
            defaultModel = "gpt-4o",
            aiConfigRepo = aiConfigRepo,
        )

    private fun aiConfig(
        githubToken: String = "key",
        model: String = "gpt-4o",
        language: String = "en",
    ): AiConfig {
        val cfg = AiConfig(model = model, language = language)
        cfg.githubToken = githubToken
        return cfg
    }

    @Test
    fun `resolveModel applies alias for gpt-4o`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(model = "gpt-4o"))
        val model = adapter().resolveModel()
        assertEquals("gpt-5.4-mini", model)
    }

    @Test
    fun `resolveModel applies alias for gpt-4o-mini`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(model = "gpt-4o-mini"))
        val model = adapter().resolveModel()
        assertEquals("gpt-5.4-mini", model)
    }

    @Test
    fun `resolveModel keeps model unchanged when no alias matches`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(model = "custom-model"))
        val model = adapter().resolveModel()
        assertEquals("custom-model", model)
    }

    @Test
    fun `resolveLanguage returns 'de' when config has blank language`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = ""))
        assertEquals("de", adapter().resolveLanguage())
    }

    @Test
    fun `resolveLanguage returns 'de' when no config`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals("de", adapter().resolveLanguage())
    }

    @Test
    fun `resolveModel returns hardcoded default when config and default both blank`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(model = ""))
        val adapter =
            CopilotCliAdapter(
                configuredBinary = "copilot",
                legacyCliBin = "x",
                timeoutSeconds = 10L,
                defaultModel = "",
                aiConfigRepo = aiConfigRepo,
            )
        val model = adapter.resolveModel()
        assertEquals("gpt-5.4-mini", model)
    }

    @Test
    fun `initialize can be called without throwing`() {
        adapter().initialize()
        assertTrue(true)
    }

    @Test
    fun `DEFAULT_MODEL_ALIASES contains gpt-4o mapping`() {
        assertNotNull(CopilotCliAdapter.DEFAULT_MODEL_ALIASES["gpt-4o"])
    }

    @Test
    fun `DEFAULT_MODEL_ALIASES contains gpt-4o-mini mapping`() {
        assertNotNull(CopilotCliAdapter.DEFAULT_MODEL_ALIASES["gpt-4o-mini"])
    }

    @Test
    fun `isReady returns boolean (no exception) when no config`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val ready = adapter().isReady()
        assertNotNull(ready)
    }

    @Test
    fun `isReady is callable with blank token config`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(githubToken = ""))
        val ready = adapter().isReady()
        assertNotNull(ready)
    }
}
