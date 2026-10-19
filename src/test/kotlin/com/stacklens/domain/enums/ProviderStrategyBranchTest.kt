package com.stacklens.domain.enums

import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderStrategyBranchTest {
    @Test
    fun `resolveModelStatic fall through`() {
        assertEquals("dto", ProviderStrategy.resolveModelStatic("dto", AiConfig(model = "stored"), "default"))
        assertEquals("stored", ProviderStrategy.resolveModelStatic("  ", AiConfig(model = "stored"), "default"))
        assertEquals("default", ProviderStrategy.resolveModelStatic(null, AiConfig(model = "  "), "default"))
        assertEquals("default", ProviderStrategy.resolveModelStatic(null, AiConfig(model = null), "default"))
    }

    @Test
    fun `base strategies resolve via default implementation`() {
        assertEquals("dto", ProviderStrategies.COPILOT.resolveModel("dto", AiConfig()))
        assertEquals("gpt-5.4-mini", ProviderStrategies.COPILOT.resolveModel(null, AiConfig(model = null)))
        assertEquals("gpt-5.4-mini", ProviderStrategies.OLLAMA.defaultModel)
        assertEquals("gpt-5.2", ProviderStrategies.OPENAI.defaultModel)
        assertEquals("openai/gpt-4.1", ProviderStrategies.OPENROUTER.defaultModel)
        assertEquals("claude-sonnet-4-5", ProviderStrategies.CLAUDECODE.defaultModel)
        assertEquals(
            "copilot",
            ProviderStrategies.OPENAI.id
                .takeIf { it == "openai" }
                ?.let { ProviderStrategies.COPILOT.id },
        )
        assertTrue(ProviderStrategies.COPILOT.requiresToken)
        assertFalse(ProviderStrategies.OLLAMA.requiresToken)
    }

    @Test
    fun `lmstudio strategy resolves model with live and default fallbacks`() {
        val st = ProviderStrategies.LMSTUDIO
        assertEquals("dto", st.resolveModel("dto", null, AiConfig(), null))
        assertEquals("local-model", st.resolveModel(null, null, AiConfig(), null))
        assertEquals("live", st.resolveModel(null, null, AiConfig(), "live"))
        assertEquals("local-model", st.resolveModel(null, null, AiConfig(aiProvider = "copilot", model = "copilot-model"), "live"))
        assertEquals("stored-lm", st.resolveModel(null, null, AiConfig(aiProvider = "lmstudio", model = "stored-lm"), "live"))
    }

    @Test
    fun `strategy identities`() {
        assertEquals("ollama", ProviderStrategies.OLLAMA.id)
        assertEquals("lmstudio", ProviderStrategies.LMSTUDIO.id)
        assertEquals("openai", ProviderStrategies.OPENAI.id)
        assertEquals("openrouter", ProviderStrategies.OPENROUTER.id)
        assertEquals("claudecode", ProviderStrategies.CLAUDECODE.id)
        assertEquals(false, ProviderStrategies.LMSTUDIO.requiresToken)
    }
}

class CodeHostingPlatformBranchTest {
    @Test
    fun `fromWire parses known and unknown platforms`() {
        assertEquals(CodeHostingPlatform.Gitlab, CodeHostingPlatform.fromWire("gitlab"))
        assertEquals(CodeHostingPlatform.Gitlab, CodeHostingPlatform.fromWire(" GITLAB "))
        assertEquals(CodeHostingPlatform.Github, CodeHostingPlatform.fromWire("github"))
        assertEquals(CodeHostingPlatform.Unknown("unknown"), CodeHostingPlatform.fromWire("unknown"))
        assertEquals(CodeHostingPlatform.Unknown(""), CodeHostingPlatform.fromWire(null))
    }

    @Test
    fun `each platform answers only for its own config family`() {
        assertEquals(listOf("gitlab", "github"), CodeHostingPlatform.allIds)
        assertEquals(CodeHostingPlatform.Github, CodeHostingPlatform.fromWire("github"))
        assertEquals(CodeHostingPlatform.Gitlab, CodeHostingPlatform.fromWire("gitlab"))
        assertEquals(CodeHostingPlatform.Unknown("gitab"), CodeHostingPlatform.fromWire("gitab"))
        assertEquals(CodeHostingPlatform.Unknown(""), CodeHostingPlatform.fromWire("   "))
    }

    @Test
    fun `selectConfigs for gitlab`() {
        val g1 =
            com.stacklens.domain.model
                .GitlabConfig(id = 1L, name = "a", baseUrl = "u", accessToken = "t")
        val g2 =
            com.stacklens.domain.model
                .GitlabConfig(id = 2L, name = "b", baseUrl = "u", accessToken = "t")
        assertEquals(listOf(g1), CodeHostingPlatform.Gitlab.selectConfigs(g1.id, listOf(g1, g2), emptyList()).gitlab)
        assertEquals(listOf(g1, g2), CodeHostingPlatform.Gitlab.selectConfigs(null, listOf(g1, g2), emptyList()).gitlab)
        assertEquals(
            true,
            CodeHostingPlatform.Gitlab
                .selectConfigs(g1.id, listOf(g1, g2), emptyList())
                .github
                .isEmpty(),
        )
    }

    @Test
    fun `selectConfigs for github and unknown`() {
        val h1 =
            com.stacklens.domain.model
                .GithubRepoConfig(id = 1L, name = "a", owner = "o", repo = "r")
        val h2 =
            com.stacklens.domain.model
                .GithubRepoConfig(id = 2L, name = "b", owner = "o", repo = "r")
        assertEquals(listOf(h1), CodeHostingPlatform.Github.selectConfigs(h1.id, emptyList(), listOf(h1, h2)).github)
        assertEquals(listOf(h1, h2), CodeHostingPlatform.Github.selectConfigs(null, emptyList(), listOf(h1, h2)).github)
        val unknown = CodeHostingPlatform.Unknown("x")
        assertEquals(listOf(h1), unknown.selectConfigs(null, emptyList(), listOf(h1)).github)
    }
}
