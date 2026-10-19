package com.stacklens.adapter.inbound.rest.config
import com.stacklens.application.port.inbound.config.AiConfigSaveRequest
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.service.config.AiConfigSecretFieldRegistry
import com.stacklens.application.service.config.AiConfigService
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiConfigDtoMapperSecretCoverageTest {
    private val mapper = AiConfigDtoMapper()

    private val mask = "••••••••"

    private fun storedGithubSecret(submitted: String?): String? {
        val repo: AiConfigPort = mock()
        whenever(repo.findAiConfig()).thenReturn(AiConfig(id = 1L, githubToken = "stored"))
        whenever(repo.saveAiConfig(any())).thenAnswer { it.arguments[0] }

        return AiConfigService(repo, mock(), AiConfigSecretFieldRegistry()).saveAiConfigFrom(AiConfigSaveRequest(githubToken = submitted)).githubToken
    }

    @Test
    fun `an omitted secret keeps the stored value without an explicit clear flag`() {
        assertEquals("stored", storedGithubSecret(null))
        assertEquals("stored", storedGithubSecret(mask))
        assertNull(storedGithubSecret(""))
        assertEquals("fresh", storedGithubSecret("  fresh  "))
    }

    @Test
    fun `masked compatible key of the optional config stays masked and set`() {
        val dto =
            mapper.toDto(
                AiConfig(
                    githubToken = " ",
                    openAiApiKey = " ",
                    openRouterApiKey = " ",
                    claudeCodeApiKey = " ",
                    compatibleApiKey = " ",
                ),
            )

        assertFalse(dto.githubTokenSet)
        assertFalse(dto.openAiApiKeySet)
        assertFalse(dto.claudeCodeApiKeySet)
        assertFalse(dto.compatibleApiKeySet)
        assertEquals("", dto.githubToken)
        assertEquals("", dto.openAiApiKey)
        assertEquals("", dto.claudeCodeApiKey)
        assertEquals("", dto.compatibleApiKey)
        assertEquals(mask, dto.openRouterApiKey)
        assertTrue(dto.openRouterApiKeySet)
    }

    @Test
    fun `blank stored keys of the resolved dto render as unset`() {
        val existing =
            AiConfig(
                aiProvider = "copilot",
                claudeCodeApiKey = " ",
                compatibleApiKey = " ",
                openRouterApiKey = " ",
                githubToken = " ",
                openAiApiKey = " ",
            )

        val dto = mapper.toDto(existing, resolvedModel = "model-x")

        assertEquals("", dto.claudeCodeApiKey)
        assertEquals("", dto.compatibleApiKey)
        assertFalse(dto.claudeCodeApiKeySet)
        assertFalse(dto.compatibleApiKeySet)
        assertEquals(mask, dto.openRouterApiKey)
        assertTrue(dto.openRouterApiKeySet)
        assertEquals("model-x", dto.model)
    }
}
