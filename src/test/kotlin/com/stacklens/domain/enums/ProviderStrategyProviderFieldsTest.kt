package com.stacklens.domain.enums

import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ProviderStrategyProviderFieldsTest {
    @Test
    fun `base strategies apply a submitted reasoning effort only`() {
        val config = AiConfig(openAiReasoningEffort = "high")

        ProviderStrategies.COPILOT.applyRequestFields(config, null)
        assertEquals("high", config.openAiReasoningEffort)

        ProviderStrategies.COPILOT.applyRequestFields(config, "  HIGH  ")
        assertEquals("high", config.openAiReasoningEffort)
    }

    @Test
    fun `openai applies the reasoning effort even without a submitted value`() {
        val config = AiConfig(openAiReasoningEffort = "high")

        ProviderStrategies.OPENAI.applyRequestFields(config, null)

        assertEquals(OpenAiReasoningEffort.DEFAULT, config.openAiReasoningEffort)
    }

    @Test
    fun `lmstudio applies a submitted reasoning effort only`() {
        val config = AiConfig(openAiReasoningEffort = "high")

        ProviderStrategies.LMSTUDIO.applyRequestFields(config, null)
        assertEquals("high", config.openAiReasoningEffort)

        ProviderStrategies.LMSTUDIO.applyRequestFields(config, "low")
        assertEquals("low", config.openAiReasoningEffort)
    }

    @Test
    fun `existing config preserves every field and only drops the model for a blank dto model`() {
        val stored = storedConfig()

        val kept = ProviderStrategies.COPILOT.existingForModelResolution(stored, "typed")
        assertSame(stored, kept)

        val cleared = ProviderStrategies.LMSTUDIO.existingForModelResolution(stored, "   ")
        assertNull(cleared.model)
        assertEquals(stored.id, cleared.id)
        assertEquals(stored.githubToken, cleared.githubToken)
        assertEquals(stored.language, cleared.language)
        assertEquals(stored.availableModels, cleared.availableModels)
        assertEquals(stored.aiProvider, cleared.aiProvider)
        assertEquals(stored.ollamaUrl, cleared.ollamaUrl)
        assertEquals(stored.ollamaNumCtx, cleared.ollamaNumCtx)
        assertEquals(stored.lmStudioUrl, cleared.lmStudioUrl)
        assertEquals(stored.openAiApiKey, cleared.openAiApiKey)
        assertEquals(stored.openAiReasoningEffort, cleared.openAiReasoningEffort)
        assertEquals(stored.openRouterApiKey, cleared.openRouterApiKey)
        assertEquals(stored.claudeCodeApiKey, cleared.claudeCodeApiKey)
        assertEquals(stored.compatibleApiKey, cleared.compatibleApiKey)
        assertEquals(stored.compatibleBaseUrl, cleared.compatibleBaseUrl)

        val nullModel = ProviderStrategies.COPILOT.existingForModelResolution(stored, null)
        assertNull(nullModel.model)
        assertEquals(stored.aiProvider, nullModel.aiProvider)

        val lmStudioKept = ProviderStrategies.LMSTUDIO.existingForModelResolution(stored, "typed")
        assertSame(stored, lmStudioKept)
    }

    @Test
    fun `only lmstudio delegates live model resolution for the lmstudio id`() {
        var calls = 0
        val resolve = { _: String?, _: String?, _: AiConfig ->
            calls++
            "live-model"
        }

        assertNull(ProviderStrategies.LMSTUDIO.resolveLiveModel(ProviderStrategies.OPENAI_ID, "model", "url", AiConfig(), resolve))
        assertEquals(0, calls)

        assertEquals("live-model", ProviderStrategies.LMSTUDIO.resolveLiveModel(ProviderStrategies.LMSTUDIO_ID, "model", "url", AiConfig(), resolve))
        assertEquals(1, calls)
    }

    private fun storedConfig(): AiConfig =
        AiConfig(
            id = 7L,
            githubToken = "github",
            model = "stored-model",
            language = "de",
            availableModels = "a,b",
            aiProvider = "openai",
            ollamaUrl = "http://ollama:11434",
            ollamaNumCtx = 4096,
            lmStudioUrl = "http://lmstudio:1234",
            openAiApiKey = "openai-key",
            openAiReasoningEffort = "high",
            openRouterApiKey = "openrouter-key",
            claudeCodeApiKey = "claude-key",
            compatibleApiKey = "compatible-key",
            compatibleBaseUrl = "http://compatible:8000",
        )
}
