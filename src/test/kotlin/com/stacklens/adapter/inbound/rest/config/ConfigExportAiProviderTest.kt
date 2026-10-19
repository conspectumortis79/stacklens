package com.stacklens.adapter.inbound.rest.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.inbound.rest.ConfigExportDto
import com.stacklens.adapter.out.security.ConfigEnvelopeCipherAdapter
import com.stacklens.application.port.inbound.config.ConfigExportSnapshot
import com.stacklens.application.port.inbound.config.DeveloperOptionSettings
import com.stacklens.application.service.config.ConfigEnvelopeService
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ConfigExportAiProviderTest {
    private val mapper = jacksonObjectMapper()

    private val codec = ConfigEnvelopeCodec(mapper, ConfigEnvelopeService(ConfigEnvelopeCipherAdapter(mapper)))

    private val exportDtoMapper = ConfigExportDtoMapper()

    private val importMapper = ConfigImportMapper()

    private val password = "secret-password"

    private val compatibleConfig =
        AiConfig(
            githubToken = "ghp-secret",
            model = "qwen3.8-flash",
            availableModels = "qwen3.8-flash,glm-4.7-air",
            aiProvider = "compatible",
            ollamaUrl = "http://localhost:11434",
            ollamaNumCtx = 8192,
            lmStudioUrl = "http://localhost:1234/v1",
            openAiApiKey = "sk-openai-secret",
            openAiReasoningEffort = "high",
            openRouterApiKey = "sk-openrouter-secret",
            claudeCodeApiKey = "sk-claude-secret",
            compatibleApiKey = "sk-compatible-secret",
            compatibleBaseUrl = "https://gateway.example.com/compatible-mode/v1",
        )

    private fun snapshotWith(
        ai: AiConfig?,
        preferences: Map<String, String> = emptyMap(),
    ): ConfigExportSnapshot =
        ConfigExportSnapshot(
            exportedAt = LocalDateTime.of(2026, 9, 2, 20, 0),
            gitlab = emptyList(),
            databases = emptyList(),
            confluence = emptyList(),
            grafana = emptyList(),
            jira = emptyList(),
            github = emptyList(),
            mcp = emptyList(),
            ai = ai,
            language = ai?.language,
            theme = "modern",
            developerOptions =
                DeveloperOptionSettings(
                    showPromptButtons = false,
                    textFilterEnabled = true,
                    showTokenUsage = true,
                    dockerTestEnv = false,
                    dockerAutoStart = false,
                ),
            uiPreferences = preferences,
        )

    private fun exportThenReadBack(
        ai: AiConfig?,
        preferences: Map<String, String> = emptyMap(),
    ): ConfigExportDto {
        val envelope = codec.encrypt(exportDtoMapper.toDto(snapshotWith(ai, preferences)), password)
        val outcome = codec.decode(envelope, password)
        assertTrue(outcome is ConfigEnvelopeOutcome.Loaded, "the written envelope must decrypt again")
        return (outcome as ConfigEnvelopeOutcome.Loaded).dto
    }

    @Test
    fun `exported envelope holds the openai compatible provider with its real values`() {
        val dto = exportThenReadBack(compatibleConfig)

        assertEquals("compatible", dto.ai?.aiProvider)
        assertEquals("https://gateway.example.com/compatible-mode/v1", dto.ai?.compatibleBaseUrl)
        assertEquals("sk-compatible-secret", dto.ai?.compatibleApiKey)
        assertEquals("qwen3.8-flash", dto.ai?.model)
        assertEquals(listOf("qwen3.8-flash", "glm-4.7-air"), dto.ai?.availableModels)
        assertEquals("en", dto.language)
    }

    @Test
    fun `exported envelope holds every other ai provider credential`() {
        val dto = exportThenReadBack(compatibleConfig)

        assertEquals("sk-openai-secret", dto.ai?.openAiApiKey)
        assertEquals("high", dto.ai?.openAiReasoningEffort)
        assertEquals("sk-openrouter-secret", dto.ai?.openRouterApiKey)
        assertEquals("sk-claude-secret", dto.ai?.claudeCodeApiKey)
        assertEquals("http://localhost:11434", dto.ai?.ollamaUrl)
        assertEquals(8192, dto.ai?.ollamaNumCtx)
        assertEquals("http://localhost:1234/v1", dto.ai?.lmStudioUrl)
        assertEquals("ghp-secret", dto.ai?.githubToken)
    }

    @Test
    fun `import rebuilds the ai provider config from the exported envelope`() {
        val restored = importMapper.toRequest(exportThenReadBack(compatibleConfig)).aiConfig

        assertNotNull(restored)
        assertEquals("compatible", restored?.aiProvider)
        assertEquals("https://gateway.example.com/compatible-mode/v1", restored?.compatibleBaseUrl)
        assertEquals("sk-compatible-secret", restored?.compatibleApiKey)
        assertEquals("qwen3.8-flash", restored?.model)
        assertEquals("sk-openai-secret", restored?.openAiApiKey)
        assertEquals("sk-openrouter-secret", restored?.openRouterApiKey)
        assertEquals("sk-claude-secret", restored?.claudeCodeApiKey)
        assertEquals("http://localhost:11434", restored?.ollamaUrl)
        assertEquals(8192, restored?.ollamaNumCtx)
        assertEquals("http://localhost:1234/v1", restored?.lmStudioUrl)
        assertEquals("ghp-secret", restored?.githubToken)
    }

    @Test
    fun `exported envelope carries the per provider model memory written by the config page`() {
        val remembered = """{"openai":"gpt-5-mini","compatible":"qwen3.8-flash"}"""

        val dto = exportThenReadBack(compatibleConfig, mapOf("ai_model_per_provider" to remembered))

        assertEquals(remembered, dto.uiPreferences["ai_model_per_provider"])
    }

    @Test
    fun `export without a saved ai config stays empty instead of inventing defaults`() {
        val dto = exportThenReadBack(null)

        assertNull(dto.ai)
        assertNull(importMapper.toRequest(dto).aiConfig)
    }
}
