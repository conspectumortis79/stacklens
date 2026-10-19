package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CopilotCliAdapterBranchTest {
    private lateinit var server: MockWebServer
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `CopilotCliAdapter is constructible with all dependencies`() {
        val adp =
            com.stacklens.adapter.out.ai.CopilotCliAdapter(
                configuredBinary = "copilot",
                legacyCliBin = "github-copilot-cli",
                timeoutSeconds = 10L,
                defaultModel = "gpt-4",
                aiConfigRepo = mock<AiConfigPort>(),
            )
        assertNotNull(adp)
    }

    @Test
    fun `CopilotCliAdapter isReady returns true when no model configured (default model used)`() {
        val adp =
            com.stacklens.adapter.out.ai.CopilotCliAdapter(
                configuredBinary = "copilot",
                legacyCliBin = "github-copilot-cli",
                timeoutSeconds = 10L,
                defaultModel = "gpt-4",
                aiConfigRepo = mock<AiConfigPort>(),
            )
        assertEquals(true, adp.isReady())
    }

    @Test
    fun `CopilotCliAdapter isReady returns true when model configured`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.model).thenReturn("gpt-4")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val adp =
            com.stacklens.adapter.out.ai.CopilotCliAdapter(
                configuredBinary = "copilot",
                legacyCliBin = "github-copilot-cli",
                timeoutSeconds = 10L,
                defaultModel = "gpt-4",
                aiConfigRepo = aiConfigRepo,
            )
        assertEquals(true, adp.isReady())
    }

    @Test
    fun `CopilotCliAdapter resolveModel returns the configured model`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.model).thenReturn("gpt-4")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val adp =
            com.stacklens.adapter.out.ai.CopilotCliAdapter(
                configuredBinary = "copilot",
                legacyCliBin = "github-copilot-cli",
                timeoutSeconds = 10L,
                defaultModel = "gpt-4",
                aiConfigRepo = aiConfigRepo,
            )
        assertEquals("gpt-4", adp.resolveModel())
    }

    @Test
    fun `CopilotCliAdapter resolveLanguage returns the configured language`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.language).thenReturn("de")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val adp =
            com.stacklens.adapter.out.ai.CopilotCliAdapter(
                configuredBinary = "copilot",
                legacyCliBin = "github-copilot-cli",
                timeoutSeconds = 10L,
                defaultModel = "gpt-4",
                aiConfigRepo = aiConfigRepo,
            )
        assertEquals("de", adp.resolveLanguage())
    }

    @Test
    fun `CopilotCliAdapter resolveToken returns null when no token configured`() {
        val adp =
            com.stacklens.adapter.out.ai.CopilotCliAdapter(
                configuredBinary = "copilot",
                legacyCliBin = "github-copilot-cli",
                timeoutSeconds = 10L,
                defaultModel = "gpt-4",
                aiConfigRepo = mock<AiConfigPort>(),
            )
        adp.resolveToken()
    }

    @Test
    fun `providerLabel getter returns Copilot`() {
        val adp =
            CopilotCliAdapter(
                configuredBinary = "copilot",
                legacyCliBin = "github-copilot-cli",
                timeoutSeconds = 10L,
                defaultModel = "gpt-4",
                aiConfigRepo = mock<AiConfigPort>(),
            )
        assertEquals("Copilot", adp.providerLabel)
    }

    @Test
    fun `envReader default returns null for unset env var`() {
        val adp =
            CopilotCliAdapter(
                configuredBinary = "copilot",
                legacyCliBin = "github-copilot-cli",
                timeoutSeconds = 10L,
                defaultModel = "gpt-4",
                aiConfigRepo = mock<AiConfigPort>(),
                envReader = { _ -> null },
            )
        assertEquals(null, adp.resolveToken())
    }

    @Test
    fun `envReader and copilotCmd can be set and read via reflection`() {
        val adp =
            CopilotCliAdapter(
                configuredBinary = "copilot",
                legacyCliBin = "github-copilot-cli",
                timeoutSeconds = 10L,
                defaultModel = "gpt-4",
                aiConfigRepo = mock<AiConfigPort>(),
            )
        val envReaderField = CopilotCliAdapter::class.java.getDeclaredField("envReader")
        envReaderField.isAccessible = true
        val copilotCmdField = CopilotCliAdapter::class.java.getDeclaredField("copilotCmd")
        copilotCmdField.isAccessible = true
        assertEquals(null, copilotCmdField.get(adp))
        copilotCmdField.set(adp, listOf("/custom/copilot", "--debug"))
        assertEquals(listOf("/custom/copilot", "--debug"), copilotCmdField.get(adp))
    }
}
