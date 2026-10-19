package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.AiConfigDto
import com.stacklens.application.port.inbound.config.AiConfigSaveRequest
import com.stacklens.application.port.inbound.config.AiConfigUseCase
import com.stacklens.application.port.inbound.config.AiConnectionTestRequest
import com.stacklens.application.port.inbound.config.AiConnectionTestTarget
import com.stacklens.application.port.inbound.config.AiConnectionTestUseCase
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AiConfigApiControllerEndpointTest {
    private val configManagement: AiConfigUseCase = mock()
    private val connectionTest: AiConnectionTestUseCase = mock()
    private val controller = AiConfigApiController(configManagement, connectionTest, AiConfigDtoMapper())

    private val payload: Map<String, Any> = mapOf("success" to true)

    private fun stubProbe() {
        whenever(connectionTest.testConnection(any(), any())).thenReturn(payload)
    }

    @Test
    fun `each test endpoint reaches the use case with its own target`() {
        stubProbe()
        val dto = AiConfigDto(model = "m", githubToken = "gh")

        val responses =
            mapOf(
                AiConnectionTestTarget.GITHUB_TOKEN to controller.testAiToken(dto),
                AiConnectionTestTarget.COPILOT_MODELS to controller.fetchCopilotModels(dto),
                AiConnectionTestTarget.COPILOT_MODEL to controller.testCopilotModel(dto),
                AiConnectionTestTarget.OLLAMA to controller.testOllamaConnection(dto),
                AiConnectionTestTarget.OPENAI to controller.testOpenAiConnection(dto),
                AiConnectionTestTarget.LMSTUDIO to controller.testLmStudioConnection(dto),
                AiConnectionTestTarget.OPENROUTER to controller.testOpenRouterConnection(dto),
                AiConnectionTestTarget.CLAUDE_CODE to controller.testClaudeCodeConnection(dto),
                AiConnectionTestTarget.COMPATIBLE to controller.testCompatibleConnection(dto),
            )

        assertEquals(9, responses.size)
        responses.forEach { (target, response) -> assertEquals(payload, response, target.toString()) }
        verify(connectionTest).testConnection(eq(AiConnectionTestTarget.GITHUB_TOKEN), any())
        verify(connectionTest).testConnection(eq(AiConnectionTestTarget.COMPATIBLE), any())
    }

    @Test
    fun `a test endpoint without a request body still probes with an empty request`() {
        stubProbe()

        assertEquals(payload, controller.testAiToken(null))
        assertEquals(payload, controller.testOllamaConnection(null))

        verify(connectionTest, org.mockito.kotlin.times(2)).testConnection(any(), any())
    }

    @Test
    fun `every submitted dto field is mapped onto the connection request`() {
        val captured = org.mockito.kotlin.argumentCaptor<AiConnectionTestRequest>()
        whenever(connectionTest.testConnection(any(), captured.capture())).thenReturn(payload)

        controller.testCompatibleConnection(
            AiConfigDto(
                model = "model-value",
                githubToken = "gh-value",
                ollamaUrl = "http://ollama",
                lmStudioUrl = "http://lmstudio",
                openAiApiKey = "oa-key",
                openRouterApiKey = "or-key",
                claudeCodeApiKey = "cc-key",
                compatibleApiKey = "compat-key",
                compatibleBaseUrl = "http://compat",
            ),
        )

        val request = captured.firstValue
        assertEquals("model-value", request.model)
        assertEquals("gh-value", request.githubToken)
        assertEquals("http://ollama", request.ollamaUrl)
        assertEquals("http://lmstudio", request.lmStudioUrl)
        assertEquals("oa-key", request.openAiApiKey)
        assertEquals("or-key", request.openRouterApiKey)
        assertEquals("cc-key", request.claudeCodeApiKey)
        assertEquals("compat-key", request.compatibleApiKey)
        assertEquals("http://compat", request.compatibleBaseUrl)
    }

    @Test
    fun `saving forwards every dto field to the save request`() {
        val captured = org.mockito.kotlin.argumentCaptor<AiConfigSaveRequest>()
        whenever(configManagement.saveAiConfigFrom(captured.capture())).thenReturn(AiConfig(id = 1L, model = "gpt-5.4-mini"))

        val dto =
            controller.saveAiConfig(
                AiConfigDto(
                    githubToken = "gh",
                    openAiApiKey = "oa",
                    openAiReasoningEffort = "low",
                    model = "m",
                    aiProvider = "openai",
                    ollamaUrl = "http://o",
                    ollamaNumCtx = 8192,
                    lmStudioUrl = "http://l",
                    openRouterApiKey = "orr",
                    claudeCodeApiKey = "cc",
                    compatibleApiKey = "ca",
                    compatibleBaseUrl = "http://c",
                    explicitClearGithubToken = true,
                    explicitClearOpenAiKey = true,
                    explicitClearOpenRouterKey = true,
                    explicitClearClaudeCodeKey = true,
                    explicitClearCompatibleKey = true,
                ),
            )

        val request = captured.firstValue
        assertEquals("gh", request.githubToken)
        assertEquals("oa", request.openAiApiKey)
        assertEquals("low", request.openAiReasoningEffort)
        assertEquals("m", request.model)
        assertEquals("openai", request.aiProvider)
        assertEquals("http://o", request.ollamaUrl)
        assertEquals(8192, request.ollamaNumCtx)
        assertEquals("http://l", request.lmStudioUrl)
        assertEquals("orr", request.openRouterApiKey)
        assertEquals("cc", request.claudeCodeApiKey)
        assertEquals("ca", request.compatibleApiKey)
        assertEquals("http://c", request.compatibleBaseUrl)
        assertTrue(request.explicitClearGithubToken)
        assertTrue(request.explicitClearOpenAiKey)
        assertTrue(request.explicitClearOpenRouterKey)
        assertTrue(request.explicitClearClaudeCodeKey)
        assertTrue(request.explicitClearCompatibleKey)
        assertEquals("gpt-5.4-mini", dto.model)
    }

    @Test
    fun `saving without a stored model answers with an empty model instead of failing`() {
        whenever(configManagement.saveAiConfigFrom(any())).thenReturn(AiConfig(id = 1L, model = null))

        val dto = controller.saveAiConfig(AiConfigDto(aiProvider = "copilot"))

        assertEquals("", dto.model)
    }

    @Test
    fun `getAiConfig keeps delegating to the use case`() {
        whenever(configManagement.getAiConfig()).thenReturn(AiConfig(id = 1L, model = "stored"))

        assertEquals("stored", controller.getAiConfig().model)
    }
}
