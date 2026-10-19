package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.AiConfigSaveRequest
import com.stacklens.application.port.inbound.config.AiConnectionTestRequest
import com.stacklens.application.port.inbound.config.AiConnectionTestTarget
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestGateway
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.config.AiDefaultsPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AiConfigServiceAndConnectionTest {
    private val aiConfigRepo: AiConfigPort = mock()
    private val connectionTest: AiConnectionTestGateway = mock()
    private val defaults = AiDefaultsPort { "https://default.example" }
    private val configService = AiConfigService(aiConfigRepo, connectionTest, AiConfigSecretFieldRegistry())
    private val connectionService = AiConnectionTestService(connectionTest, aiConfigRepo, defaults)

    private val ok = AiConnectionTestPort.TestResult.Success(mapOf("success" to true))

    private fun stored(config: AiConfig?) {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config)
    }

    private fun compatibleProbe(request: AiConnectionTestRequest = AiConnectionTestRequest()) = connectionService.testConnection(AiConnectionTestTarget.COMPATIBLE, request)

    @Test
    fun `compatible connection test prefers the request values over the stored ones`() {
        whenever(connectionTest.testConnection(any())).thenReturn(ok)
        stored(
            AiConfig(
                id = 1L,
                compatibleApiKey = "stored-key",
                compatibleBaseUrl = " https://stored.example ",
                model = "stored-model",
                aiProvider = "compatible",
            ),
        )

        val result =
            compatibleProbe(
                AiConnectionTestRequest(
                    compatibleApiKey = "fresh",
                    compatibleBaseUrl = "https://request.example",
                    model = "request-model",
                ),
            )

        assertEquals(true, result["success"])
        verify(connectionTest).testConnection(eq(ProviderSpec.Compatible("fresh", "https://request.example", "request-model")))
    }

    @Test
    fun `compatible connection test falls back through stored values to the configured default`() {
        whenever(connectionTest.testConnection(any())).thenReturn(ok)
        stored(AiConfig(id = 1L, compatibleApiKey = "k", compatibleBaseUrl = "   ", model = "  ", aiProvider = "compatible"))

        compatibleProbe(AiConnectionTestRequest(model = " "))

        verify(connectionTest).testConnection(eq(ProviderSpec.Compatible("k", "https://default.example", null)))
    }

    @Test
    fun `compatible connection test uses the stored base url when the request has none`() {
        whenever(connectionTest.testConnection(any())).thenReturn(ok)
        stored(AiConfig(id = 1L, compatibleBaseUrl = "https://stored.example", model = "m", aiProvider = "compatible"))

        compatibleProbe(AiConnectionTestRequest(compatibleBaseUrl = "  "))

        verify(connectionTest).testConnection(eq(ProviderSpec.Compatible(null, "https://stored.example", "m")))
    }

    @Test
    fun `compatible connection test ignores stored models of another provider`() {
        whenever(connectionTest.testConnection(any())).thenReturn(ok)
        stored(AiConfig(id = 1L, model = "copilot-model", aiProvider = "copilot"))

        compatibleProbe()

        verify(connectionTest).testConnection(eq(ProviderSpec.Compatible(null, "https://default.example", null)))
    }

    @Test
    fun `saving without a stored configuration creates one and resolves the provider default`() {
        stored(null)
        whenever(aiConfigRepo.saveAiConfig(any())).thenAnswer { it.arguments[0] }
        whenever(connectionTest.resolveLmStudioModel(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn("live-model")

        val saved = configService.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "ollama"))

        assertEquals("ollama", saved.aiProvider)
        verify(aiConfigRepo).saveAiConfig(any())
    }

    @Test
    fun `saving an unknown provider keeps the request model or the copilot default`() {
        whenever(aiConfigRepo.saveAiConfig(any())).thenAnswer { it.arguments[0] }

        stored(AiConfig(id = 1L, model = "stored"))
        val kept = configService.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "unbekannt", model = "gewünscht"))
        assertEquals("gewünscht", kept.model)
        assertEquals("copilot", kept.aiProvider)

        stored(AiConfig(id = 1L, model = "stored"))
        val fallback = configService.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "unbekannt", model = "  "))
        assertEquals("gpt-5.4-mini", fallback.model)
    }

    @Test
    fun `saving lmstudio drops the stored model when the request carries none`() {
        val existing =
            AiConfig(
                id = 1L,
                model = "alt",
                aiProvider = "lmstudio",
                lmStudioUrl = "http://localhost:1234/v1",
                githubToken = "gh",
                language = "de",
                availableModels = "a,b",
                ollamaUrl = "http://ollama:11434",
                openAiApiKey = "sk",
                openRouterApiKey = "or",
                claudeCodeApiKey = "cc-key-lang",
                compatibleApiKey = "compat",
                compatibleBaseUrl = "http://compat",
            )
        stored(existing)
        whenever(aiConfigRepo.saveAiConfig(any())).thenAnswer { it.arguments[0] }
        whenever(connectionTest.resolveLmStudioModel(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn("lm-live")

        val saved = configService.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "lmstudio", lmStudioUrl = "http://localhost:1234/v1"))

        assertEquals("lm-live", saved.model)
        assertEquals("lmstudio", existing.aiProvider)
    }

    @Test
    fun `saving a provider without a stored configuration keeps the typed model`() {
        stored(AiConfig(id = 1L, aiProvider = "claudecode", claudeCodeApiKey = null))
        whenever(aiConfigRepo.saveAiConfig(any())).thenAnswer { it.arguments[0] }
        whenever(connectionTest.resolveLmStudioModel(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(null)

        val saved = configService.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "claudecode", model = "claude-sonnet-4-5"))

        assertEquals("claude-sonnet-4-5", saved.model)
    }

    @Test
    fun `saving openai normalises the reasoning effort`() {
        stored(AiConfig(id = 1L, model = "alt", aiProvider = "openai"))
        whenever(aiConfigRepo.saveAiConfig(any())).thenAnswer { it.arguments[0] }
        whenever(connectionTest.resolveLmStudioModel(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(null)

        val saved = configService.saveAiConfigFrom(AiConfigSaveRequest(aiProvider = "openai", model = "gpt-5.2", openAiReasoningEffort = "WILD"))

        assertEquals("gpt-5.2", saved.model)
        assertEquals("medium", saved.openAiReasoningEffort)
    }
}
