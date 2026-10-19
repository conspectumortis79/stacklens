package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.AiConnectionTestRequest
import com.stacklens.application.port.inbound.config.AiConnectionTestTarget
import com.stacklens.application.port.inbound.config.AiConnectionTestUseCase
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestGateway
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.config.AiDefaultsPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class AiConnectionTestServiceCompatibleTest {
    @Mock private lateinit var aiConfigPort: AiConfigPort

    @Mock private lateinit var gateway: AiConnectionTestGateway

    private val defaultUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"

    private val mask = "••••••••"

    private val payload: Map<String, Any> = mapOf("success" to true, "modelCount" to 3)

    private fun service(defaultBaseUrl: String = defaultUrl) = AiConnectionTestService(gateway, aiConfigPort, AiDefaultsPort { defaultBaseUrl })

    private fun probe(request: AiConnectionTestRequest = AiConnectionTestRequest()) = service().testConnection(AiConnectionTestTarget.COMPATIBLE, request)

    private fun expectSpec(spec: ProviderSpec.Compatible) {
        whenever(gateway.testConnection(spec)).thenReturn(TestResult.Success(payload))
        assertEquals(payload, probe())
    }

    @Test
    fun `the configured default base url is used when nothing is stored`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(null)

        expectSpec(ProviderSpec.Compatible(apiKey = null, baseUrl = defaultUrl, model = null))
    }

    @Test
    fun `an overridden default base url reaches the connection test`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(null)
        whenever(gateway.testConnection(ProviderSpec.Compatible(null, "http://localhost:8000/v1", null)))
            .thenReturn(TestResult.Success(payload))

        assertEquals(payload, service("http://localhost:8000/v1").testConnection(AiConnectionTestTarget.COMPATIBLE, AiConnectionTestRequest()))
    }

    @Test
    fun `request values win over stored configuration`() {
        whenever(aiConfigPort.findAiConfig())
            .thenReturn(
                AiConfig(
                    id = 1L,
                    aiProvider = "compatible",
                    model = "stored-model",
                    compatibleApiKey = "sk-stored",
                    compatibleBaseUrl = "https://stored.test/v1",
                ),
            )
        whenever(gateway.testConnection(ProviderSpec.Compatible("sk-dto", "https://dto.test/v1", "dto-model")))
            .thenReturn(TestResult.Success(payload))

        val result =
            probe(
                AiConnectionTestRequest(
                    model = "dto-model",
                    compatibleApiKey = "sk-dto",
                    compatibleBaseUrl = "https://dto.test/v1",
                ),
            )

        assertEquals(payload, result)
    }

    @Test
    fun `the masked request key falls back to the stored key`() {
        whenever(aiConfigPort.findAiConfig())
            .thenReturn(
                AiConfig(
                    aiProvider = "compatible",
                    compatibleApiKey = "sk-stored",
                    compatibleBaseUrl = "https://stored.test/v1",
                    model = "stored-model",
                ),
            )
        whenever(gateway.testConnection(ProviderSpec.Compatible("sk-stored", "https://stored.test/v1", "stored-model")))
            .thenReturn(TestResult.Success(payload))

        assertEquals(payload, probe(AiConnectionTestRequest(compatibleApiKey = mask, compatibleBaseUrl = "https://stored.test/v1")))
    }

    @Test
    fun `a whitespace only request base url falls back to the stored base url`() {
        whenever(aiConfigPort.findAiConfig())
            .thenReturn(AiConfig(aiProvider = "compatible", compatibleBaseUrl = "https://stored.test/v1"))
        whenever(gateway.testConnection(ProviderSpec.Compatible(null, "https://stored.test/v1", null)))
            .thenReturn(TestResult.Success(payload))

        assertEquals(payload, probe(AiConnectionTestRequest(compatibleBaseUrl = "   ")))
    }

    @Test
    fun `a whitespace only stored base url falls back to the configured default`() {
        whenever(aiConfigPort.findAiConfig())
            .thenReturn(AiConfig(aiProvider = "compatible", compatibleBaseUrl = "  "))

        expectSpec(ProviderSpec.Compatible(apiKey = null, baseUrl = defaultUrl, model = null))
    }

    @Test
    fun `a stored model of another provider is not probed against the compatible endpoint`() {
        whenever(aiConfigPort.findAiConfig())
            .thenReturn(
                AiConfig(
                    aiProvider = "openrouter",
                    model = "openai/gpt-4.1",
                    compatibleApiKey = "sk-stored",
                ),
            )

        expectSpec(ProviderSpec.Compatible(apiKey = "sk-stored", baseUrl = defaultUrl, model = null))
    }

    @Test
    fun `a stored model of the compatible provider is used for the probe`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "compatible", model = "qwen3.8-flash"))

        expectSpec(ProviderSpec.Compatible(apiKey = null, baseUrl = defaultUrl, model = "qwen3.8-flash"))
    }

    @Test
    fun `a compatible probe without any key still reaches the connection test`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(aiProvider = "compatible"))

        expectSpec(ProviderSpec.Compatible(apiKey = null, baseUrl = defaultUrl, model = null))
    }

    @Test
    fun `each endpoint reaches the gateway with its own provider spec`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(id = 1L, aiProvider = "copilot", githubToken = "gh-stored"))
        val targets =
            listOf(
                AiConnectionTestTarget.GITHUB_TOKEN to ProviderSpec.GithubToken("gh-stored"),
                AiConnectionTestTarget.COPILOT_MODELS to ProviderSpec.CopilotModels("gh-stored"),
                AiConnectionTestTarget.COPILOT_MODEL to ProviderSpec.CopilotModel(null, "copilot"),
            )
        targets.forEach { (target, spec) ->
            whenever(gateway.testConnection(spec)).thenReturn(TestResult.Success(payload))
            assertEquals(payload, service().testConnection(target, AiConnectionTestRequest()), target.toString())
        }
    }

    @Test
    fun `url endpoints fall back through stored values to their defaults`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(id = 1L, ollamaUrl = "http://stored:11434"))
        whenever(gateway.testConnection(ProviderSpec.Ollama("http://stored:11434"))).thenReturn(TestResult.Success(payload))
        assertEquals(payload, service().testConnection(AiConnectionTestTarget.OLLAMA, AiConnectionTestRequest()))

        whenever(gateway.testConnection(ProviderSpec.LmStudio(AiConnectionTestUseCase.LMSTUDIO_DEFAULT_URL))).thenReturn(TestResult.Success(payload))
        assertEquals(payload, service().testConnection(AiConnectionTestTarget.LMSTUDIO, AiConnectionTestRequest()))
    }

    @Test
    fun `api key endpoints resolve the stored key`() {
        whenever(aiConfigPort.findAiConfig())
            .thenReturn(AiConfig(id = 1L, openAiApiKey = "oa", openRouterApiKey = "orr", claudeCodeApiKey = "cc"))
        whenever(gateway.testConnection(ProviderSpec.OpenAi("oa"))).thenReturn(TestResult.Success(payload))
        whenever(gateway.testConnection(ProviderSpec.OpenRouter("orr"))).thenReturn(TestResult.Success(payload))
        whenever(gateway.testConnection(ProviderSpec.ClaudeCode("cc"))).thenReturn(TestResult.Success(payload))

        assertEquals(payload, service().testConnection(AiConnectionTestTarget.OPENAI, AiConnectionTestRequest()))
        assertEquals(payload, service().testConnection(AiConnectionTestTarget.OPENROUTER, AiConnectionTestRequest()))
        assertEquals(payload, service().testConnection(AiConnectionTestTarget.CLAUDE_CODE, AiConnectionTestRequest()))
    }
}
