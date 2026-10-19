package com.stacklens.adapter.inbound.rest.config

import com.stacklens.application.port.model.AiConfigMasking
import com.stacklens.domain.enums.OpenAiReasoningEffort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiConfigDtoMapperTest {
    private val mapper = AiConfigDtoMapper()

    @Test
    fun `the mask reuses the core sentinel`() {
        val dto = mapper.toDto(AiConfig(id = 1L, githubToken = "secret"))
        assertEquals(AiConfigMasking.MASKED_SECRET, dto.githubToken)
    }

    @Test
    fun `the shared reasoning effort vocabulary is exposed by the domain`() {
        assertEquals("medium", OpenAiReasoningEffort.DEFAULT)
        assertEquals(setOf("low", "medium", "high"), OpenAiReasoningEffort.SUPPORTED)
    }

    @Test
    fun `toDto returns empty defaults when config is null`() {
        val dto = mapper.toDto(null)
        assertEquals("", dto.githubToken)
        assertFalse(dto.githubTokenSet)
        assertEquals("", dto.openAiApiKey)
        assertFalse(dto.openAiApiKeySet)
        assertEquals("medium", dto.openAiReasoningEffort)
        assertEquals("", dto.model)
        assertEquals(emptyList<String>(), dto.availableModels)
        assertEquals("copilot", dto.aiProvider)
        assertEquals("", dto.ollamaUrl)
        assertEquals(32768, dto.ollamaNumCtx)
        assertEquals("", dto.lmStudioUrl)
        assertEquals("", dto.openRouterApiKey)
        assertFalse(dto.openRouterApiKeySet)
        assertEquals("", dto.claudeCodeApiKey)
        assertFalse(dto.claudeCodeApiKeySet)
    }

    @Test
    fun `toDto masks githubToken and openAiApiKey when set`() {
        val cfg =
            AiConfig(
                id = 1L,
                githubToken = "ghp_abc",
                openAiApiKey = "sk-proj-test",
                openRouterApiKey = "sk-or-123",
                claudeCodeApiKey = "sk-ant-456",
            )
        val dto = mapper.toDto(cfg)
        assertTrue(dto.githubTokenSet)
        assertTrue(dto.openAiApiKeySet)
        assertTrue(dto.openRouterApiKeySet)
        assertTrue(dto.claudeCodeApiKeySet)
        assertEquals("••••••••", dto.githubToken)
        assertEquals("••••••••", dto.openAiApiKey)
        assertEquals("••••••••", dto.openRouterApiKey)
        assertEquals("••••••••", dto.claudeCodeApiKey)
    }

    @Test
    fun `toDto clears flags for blank secrets and normalises reasoning effort`() {
        val cfg =
            AiConfig(
                id = 1L,
                githubToken = "",
                openAiApiKey = " ",
                openAiReasoningEffort = "high",
                openRouterApiKey = "",
                claudeCodeApiKey = "",
            )
        val dto = mapper.toDto(cfg)
        assertEquals("", dto.githubToken)
        assertFalse(dto.githubTokenSet)
        assertEquals("", dto.openAiApiKey)
        assertFalse(dto.openAiApiKeySet)
        assertEquals("", dto.openRouterApiKey)
        assertFalse(dto.openRouterApiKeySet)
        assertEquals("", dto.claudeCodeApiKey)
        assertFalse(dto.claudeCodeApiKeySet)
        assertEquals("high", dto.openAiReasoningEffort)
    }

    @Test
    fun `toDto splits availableModels by comma and drops blanks`() {
        val cfg = AiConfig(githubToken = "t", availableModels = "a,,b,c ")
        val dto = mapper.toDto(cfg)
        assertEquals(listOf("a", "b", "c "), dto.availableModels)
    }

    @Test
    fun `toDto forwards ollamaNumCtx value verbatim when config exists`() {
        assertEquals(0, mapper.toDto(AiConfig(ollamaNumCtx = 0)).ollamaNumCtx)
        assertEquals(32768, mapper.toDto(AiConfig(ollamaNumCtx = 32768)).ollamaNumCtx)
    }

    @Test
    fun `toDto forwards non-default ollamaNumCtx and lmStudioUrl`() {
        val cfg = AiConfig(ollamaNumCtx = 65536, lmStudioUrl = "http://my-host:1234")
        val dto = mapper.toDto(cfg)
        assertEquals(65536, dto.ollamaNumCtx)
        assertEquals("http://my-host:1234", dto.lmStudioUrl)
    }

    @Test
    fun `toDto with non-null entity uses resolvedModel and masks secrets`() {
        val existing =
            AiConfig(
                id = 1L,
                githubToken = "secret",
                openAiApiKey = "openai-secret",
                openRouterApiKey = "router-secret",
                ollamaNumCtx = 4096,
                ollamaUrl = "http://o:11434",
                lmStudioUrl = "http://l:1234",
                aiProvider = "openai",
                openAiReasoningEffort = "high",
            )
        val dto = mapper.toDto(existing, "gpt-5.2")
        assertEquals("gpt-5.2", dto.model)
        assertTrue(dto.githubTokenSet)
        assertEquals("••••••••", dto.githubToken)
        assertEquals("high", dto.openAiReasoningEffort)
        assertEquals("openai", dto.aiProvider)
        assertEquals(emptyList<String>(), dto.availableModels)
        assertEquals(4096, dto.ollamaNumCtx)
        assertEquals("http://o:11434", dto.ollamaUrl)
        assertEquals("http://l:1234", dto.lmStudioUrl)
        assertTrue(dto.openRouterApiKeySet)
    }

    @Test
    fun `toDto with non-null entity returns cleared flags for blank secrets`() {
        val existing = AiConfig(id = 1L, githubToken = "", openAiApiKey = "  ", openRouterApiKey = "")
        val dto = mapper.toDto(existing, "gpt-5.4-mini")
        assertEquals("", dto.githubToken)
        assertFalse(dto.githubTokenSet)
        assertEquals("", dto.openAiApiKey)
        assertFalse(dto.openAiApiKeySet)
        assertEquals("", dto.openRouterApiKey)
        assertFalse(dto.openRouterApiKeySet)
    }

    @Test
    fun `normalizeOpenAiReasoningEffort accepts lowercase known values`() {
        assertEquals("low", mapper.normalizeOpenAiReasoningEffort("low"))
        assertEquals("medium", mapper.normalizeOpenAiReasoningEffort("medium"))
        assertEquals("high", mapper.normalizeOpenAiReasoningEffort("high"))
    }

    @Test
    fun `normalizeOpenAiReasoningEffort is case-insensitive and trims whitespace`() {
        assertEquals("high", mapper.normalizeOpenAiReasoningEffort("  HIGH "))
        assertEquals("low", mapper.normalizeOpenAiReasoningEffort("LoW"))
    }

    @Test
    fun `normalizeOpenAiReasoningEffort falls back to default for unknown values`() {
        assertEquals("medium", mapper.normalizeOpenAiReasoningEffort("turbo"))
        assertEquals("medium", mapper.normalizeOpenAiReasoningEffort(""))
        assertEquals("medium", mapper.normalizeOpenAiReasoningEffort(null))
    }

    @Test
    fun `toDto with non-null model and ollamaUrl returns them in dto`() {
        val cfg = AiConfig(id = 1L, ollamaUrl = "http://localhost:11434")
        cfg.model = "gpt-4o"
        val dto = mapper.toDto(cfg)
        assertEquals("gpt-4o", dto.model)
        assertEquals("http://localhost:11434", dto.ollamaUrl)
    }

    @Test
    fun `toDto with null ollamaUrl and null lmStudioUrl returns empty strings`() {
        val existing = AiConfig(id = 1L, ollamaUrl = null, lmStudioUrl = null)
        val dto = mapper.toDto(existing, "gpt-4o")
        assertEquals("", dto.ollamaUrl)
        assertEquals("", dto.lmStudioUrl)
    }
}
