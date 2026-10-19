package com.stacklens.application.service.config

import com.stacklens.application.port.inbound.config.AiConnectionTestRequest
import com.stacklens.application.port.inbound.config.AiConnectionTestTarget
import com.stacklens.application.port.inbound.config.AiConnectionTestUseCase
import com.stacklens.application.port.model.ConnectionFailureKind
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class AiConnectionTestServiceEndpointTest {
    @Mock private lateinit var aiConfigPort: AiConfigPort

    @Mock private lateinit var gateway: AiConnectionTestGateway

    private val successPayload: Map<String, Any> = mapOf("success" to true)

    private val failurePayload: Map<String, Any> = mapOf("success" to false, "error" to "err")

    private fun service(defaultBaseUrl: String = DEFAULT_COMPATIBLE_URL) = AiConnectionTestService(gateway, aiConfigPort, AiDefaultsPort { defaultBaseUrl })

    private fun probe(
        target: AiConnectionTestTarget,
        request: AiConnectionTestRequest = AiConnectionTestRequest(),
    ): Map<String, Any> = service().testConnection(target, request)

    private fun stub(
        spec: ProviderSpec,
        result: TestResult = TestResult.Success(successPayload),
    ) {
        whenever(gateway.testConnection(spec)).thenReturn(result)
    }

    @Test
    fun `the github token endpoint prefers a freshly typed token`() {
        stub(ProviderSpec.GithubToken("mytoken"))

        assertEquals(successPayload, probe(AiConnectionTestTarget.GITHUB_TOKEN, AiConnectionTestRequest(githubToken = "mytoken")))
        verify(gateway).testConnection(ProviderSpec.GithubToken("mytoken"))
    }

    @Test
    fun `the github token endpoint falls back to the stored token for the mask placeholder`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(id = 1L, githubToken = "storedtoken"))
        stub(ProviderSpec.GithubToken("storedtoken"))

        assertEquals(successPayload, probe(AiConnectionTestTarget.GITHUB_TOKEN, AiConnectionTestRequest(githubToken = MASK)))
    }

    @Test
    fun `the github token endpoint keeps a stored token when nothing is submitted`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(id = 1L, githubToken = "storedtoken"))
        stub(ProviderSpec.GithubToken("storedtoken"))

        assertEquals(successPayload, probe(AiConnectionTestTarget.GITHUB_TOKEN))
    }

    @Test
    fun `the github token endpoint submits null when no token is available`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(id = 1L, githubToken = " "))
        stub(ProviderSpec.GithubToken(null), TestResult.Failure(ConnectionFailureKind.AUTHENTICATION, failurePayload))

        assertEquals(failurePayload, probe(AiConnectionTestTarget.GITHUB_TOKEN, AiConnectionTestRequest(githubToken = MASK)))
    }

    @Test
    fun `the copilot model list endpoint uses the resolved token`() {
        stub(ProviderSpec.CopilotModels("mytoken"))

        assertEquals(successPayload, probe(AiConnectionTestTarget.COPILOT_MODELS, AiConnectionTestRequest(githubToken = "mytoken")))
    }

    @Test
    fun `the copilot model endpoint submits the request model with the stored provider`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(id = 1L, aiProvider = "openai"))
        stub(ProviderSpec.CopilotModel("gpt-4", "openai"))

        assertEquals(successPayload, probe(AiConnectionTestTarget.COPILOT_MODEL, AiConnectionTestRequest(model = "gpt-4")))
    }

    @Test
    fun `the copilot model endpoint falls back to the stored model`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(id = 1L, model = "config-model", aiProvider = "copilot"))
        stub(ProviderSpec.CopilotModel("config-model", "copilot"))

        assertEquals(successPayload, probe(AiConnectionTestTarget.COPILOT_MODEL, AiConnectionTestRequest(model = " ")))
    }

    @Test
    fun `the copilot model endpoint defaults the provider to copilot without a stored config`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(null)
        stub(ProviderSpec.CopilotModel(null, "copilot"), TestResult.Failure(ConnectionFailureKind.OTHER, failurePayload))

        assertEquals(failurePayload, probe(AiConnectionTestTarget.COPILOT_MODEL))
    }

    @Test
    fun `the ollama endpoint uses the request url`() {
        stub(ProviderSpec.Ollama("http://localhost:11434"))

        assertEquals(successPayload, probe(AiConnectionTestTarget.OLLAMA, AiConnectionTestRequest(ollamaUrl = "http://localhost:11434")))
    }

    @Test
    fun `the ollama endpoint falls back to the stored url`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(id = 1L, ollamaUrl = "http://myollama:11434"))
        stub(ProviderSpec.Ollama("http://myollama:11434"))

        assertEquals(successPayload, probe(AiConnectionTestTarget.OLLAMA))
    }

    @Test
    fun `the ollama endpoint falls back to the loopback default`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(id = 1L, ollamaUrl = ""))
        stub(ProviderSpec.Ollama(AiConnectionTestUseCase.OLLAMA_DEFAULT_URL))

        assertEquals(successPayload, probe(AiConnectionTestTarget.OLLAMA))
    }

    @Test
    fun `the lm studio endpoint uses the request url and otherwise the default`() {
        stub(ProviderSpec.LmStudio("http://myhost:1234"))
        assertEquals(successPayload, probe(AiConnectionTestTarget.LMSTUDIO, AiConnectionTestRequest(lmStudioUrl = "http://myhost:1234")))

        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(id = 1L, lmStudioUrl = null))
        stub(ProviderSpec.LmStudio(AiConnectionTestUseCase.LMSTUDIO_DEFAULT_URL))
        assertEquals(successPayload, probe(AiConnectionTestTarget.LMSTUDIO))
    }

    @Test
    fun `the api key endpoints resolve mask and blank values from the stored config`() {
        whenever(aiConfigPort.findAiConfig())
            .thenReturn(AiConfig(id = 1L, openAiApiKey = "sk-proj-stored", openRouterApiKey = "sk-or-stored", claudeCodeApiKey = "sk-ant-stored"))
        stub(ProviderSpec.OpenAi("sk-proj-new"))
        stub(ProviderSpec.OpenRouter("sk-or-stored"))
        stub(ProviderSpec.ClaudeCode("sk-ant-stored"))

        assertEquals(successPayload, probe(AiConnectionTestTarget.OPENAI, AiConnectionTestRequest(openAiApiKey = "sk-proj-new")))
        assertEquals(successPayload, probe(AiConnectionTestTarget.OPENROUTER, AiConnectionTestRequest(openRouterApiKey = MASK)))
        assertEquals(successPayload, probe(AiConnectionTestTarget.CLAUDE_CODE, AiConnectionTestRequest(claudeCodeApiKey = "")))
    }

    @Test
    fun `a failing provider probe returns the failure payload of the gateway`() {
        stub(ProviderSpec.OpenAi(null), TestResult.Failure(ConnectionFailureKind.AUTHENTICATION, failurePayload))

        assertEquals(failurePayload, probe(AiConnectionTestTarget.OPENAI))
    }

    @Test
    fun `the compatible endpoint resolves key url and model independently`() {
        whenever(aiConfigPort.findAiConfig())
            .thenReturn(
                AiConfig(
                    id = 1L,
                    aiProvider = "compatible",
                    compatibleApiKey = "sk-stored",
                    compatibleBaseUrl = "https://stored.test/v1",
                    model = "stored-model",
                ),
            )
        stub(ProviderSpec.Compatible("sk-dto", "https://dto.test/v1", "dto-model"))
        assertEquals(
            successPayload,
            probe(
                AiConnectionTestTarget.COMPATIBLE,
                AiConnectionTestRequest(
                    model = "dto-model",
                    compatibleApiKey = "sk-dto",
                    compatibleBaseUrl = "https://dto.test/v1",
                ),
            ),
        )

        stub(ProviderSpec.Compatible("sk-stored", "https://stored.test/v1", "stored-model"))
        assertEquals(successPayload, probe(AiConnectionTestTarget.COMPATIBLE, AiConnectionTestRequest(compatibleApiKey = MASK)))
    }

    @Test
    fun `the compatible endpoint falls back to the configured default base url`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(id = 1L, compatibleBaseUrl = "   "))
        stub(ProviderSpec.Compatible(null, "http://localhost:8000/v1", null))

        assertEquals(successPayload, service("http://localhost:8000/v1").testConnection(AiConnectionTestTarget.COMPATIBLE, AiConnectionTestRequest()))
    }

    @Test
    fun `a stored model of another provider is never probed as compatible`() {
        whenever(aiConfigPort.findAiConfig()).thenReturn(AiConfig(id = 1L, aiProvider = "openrouter", model = "openai/gpt-4.1"))
        stub(ProviderSpec.Compatible(null, DEFAULT_COMPATIBLE_URL, null))

        assertEquals(successPayload, probe(AiConnectionTestTarget.COMPATIBLE))
        verify(gateway).testConnection(ProviderSpec.Compatible(null, DEFAULT_COMPATIBLE_URL, null))
    }

    private companion object {
        private const val MASK: String = "••••••••"
        private const val DEFAULT_COMPATIBLE_URL: String = "https://default.test/v1"
    }
}
