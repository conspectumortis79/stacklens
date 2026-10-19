package com.stacklens.adapter.inbound.rest.config

import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiConfigDtoMapperSecretsTest {
    private val mapper = AiConfigDtoMapper()

    private val mask = "••••••••"

    private fun config(
        githubToken: String? = null,
        openAiApiKey: String? = null,
        openRouterApiKey: String? = null,
        claudeCodeApiKey: String? = null,
        compatibleApiKey: String? = null,
        openAiReasoningEffort: String? = null,
        aiProvider: String = "copilot",
    ): AiConfig =
        AiConfig(
            githubToken = githubToken,
            openAiApiKey = openAiApiKey,
            openRouterApiKey = openRouterApiKey,
            claudeCodeApiKey = claudeCodeApiKey,
            compatibleApiKey = compatibleApiKey,
            openAiReasoningEffort = openAiReasoningEffort,
            aiProvider = aiProvider,
        )

    @Test fun `missing configuration maps to an empty dto`() {
        val dto = mapper.toDto(null)

        assertEquals("", dto.githubToken)
        assertFalse(dto.githubTokenSet)
        assertEquals("", dto.openRouterApiKey)
        assertFalse(dto.openRouterApiKeySet)
        assertEquals("", dto.compatibleApiKey)
        assertFalse(dto.compatibleApiKeySet)
        assertEquals("medium", dto.openAiReasoningEffort)
        assertEquals("copilot", dto.aiProvider)
        assertEquals(32768, dto.ollamaNumCtx)
    }

    @Test fun `present and blank secrets are masked differently`() {
        val filled =
            mapper.toDto(
                config(
                    githubToken = "gh",
                    openAiApiKey = "  ",
                    openRouterApiKey = "or",
                    claudeCodeApiKey = "",
                    compatibleApiKey = "compat",
                    openAiReasoningEffort = " HIGH ",
                ),
            )

        assertEquals(mask, filled.githubToken)
        assertTrue(filled.githubTokenSet)
        assertEquals("", filled.openAiApiKey)
        assertFalse(filled.openAiApiKeySet)
        assertEquals(mask, filled.openRouterApiKey)
        assertTrue(filled.openRouterApiKeySet)
        assertEquals("", filled.claudeCodeApiKey)
        assertFalse(filled.claudeCodeApiKeySet)
        assertEquals(mask, filled.compatibleApiKey)
        assertTrue(filled.compatibleApiKeySet)
        assertEquals("high", filled.openAiReasoningEffort)
    }

    @Test fun `an open router key that is only empty fails the set flag`() {
        val dto = mapper.toDto(config(openRouterApiKey = ""))

        assertEquals("", dto.openRouterApiKey)
        assertFalse(dto.openRouterApiKeySet)
    }

    @Test fun `available models are split and cleaned`() {
        val source = config().apply { availableModels = "a, ,b," }

        assertEquals(listOf("a", "b"), mapper.toDto(source).availableModels)
    }

    @Test fun `the merged view exposes the resolved model and the stored provider`() {
        val merged =
            mapper.toDto(
                config(githubToken = "g", openRouterApiKey = "", claudeCodeApiKey = "cc", compatibleApiKey = "  "),
                "resolved-model",
            )

        assertEquals("resolved-model", merged.model)
        assertEquals(mask, merged.githubToken)
        assertEquals("", merged.openRouterApiKey)
        assertEquals(mask, merged.claudeCodeApiKey)
        assertEquals("", merged.compatibleApiKey)
        assertEquals(emptyList<String>(), merged.availableModels)
    }

    @Test fun `unknown reasoning efforts fall back to the default`() {
        assertEquals("medium", mapper.normalizeOpenAiReasoningEffort(null))
        assertEquals("medium", mapper.normalizeOpenAiReasoningEffort("extreme"))
        assertEquals("low", mapper.normalizeOpenAiReasoningEffort("  LOW "))
    }
}
