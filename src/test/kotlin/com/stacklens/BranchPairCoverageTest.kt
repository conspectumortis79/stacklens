package com.stacklens

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.inbound.rest.GrafanaAnalyseApiController
import com.stacklens.application.port.inbound.config.AiConnectionTestRequest
import com.stacklens.application.port.inbound.config.AiConnectionTestTarget
import com.stacklens.application.port.inbound.grafana.GrafanaAnalysisUseCase
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestGateway
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.config.AiDefaultsPort
import com.stacklens.application.service.config.AiConnectionTestService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BranchPairCoverageTest {
    private val analysis: GrafanaAnalysisUseCase = mock()

    private val i18n = mock<com.stacklens.application.port.inbound.platform.I18nUseCase>()

    private val controller = GrafanaAnalyseApiController(analysis, mock(), ObjectMapper(), i18n)

    private fun capturedTranslation(key: String): String {
        whenever(i18n.t(eq(key), any())).thenReturn("translated")
        return "translated"
    }

    @Test
    fun `json invalid answers carry the exception message of both kinds`() {
        listOf<RuntimeException>(RuntimeException("boom"), RuntimeException()).forEach { failure ->
            whenever(analysis.generateDummyData(any())).thenAnswer { throw failure }

            val generate = controller.generateDummyData(GrafanaAnalyseApiController.GenerateDummyDataRequest("{broken"))
            assertEquals(400, generate.statusCode.value())

            whenever(analysis.rebuildDashboard(any(), any())).thenAnswer { throw failure }
            val rebuild =
                controller.rebuildDashboard(
                    GrafanaAnalyseApiController.RebuildDashboardRequest(dashboardJson = "{broken", instructions = "text"),
                )
            assertEquals(400, rebuild.statusCode.value())
        }
    }

    @Test
    fun `ai failures answer with and without an exception message`() {
        listOf<RuntimeException>(RuntimeException("ai down"), RuntimeException()).forEach { failure ->
            whenever(analysis.generateDummyData(any())).thenAnswer { throw failure }
            val generate = controller.generateDummyData(GrafanaAnalyseApiController.GenerateDummyDataRequest("{}"))
            assertEquals(500, generate.statusCode.value())

            whenever(analysis.rebuildDashboard(any(), any())).thenAnswer { throw failure }
            val rebuild = controller.rebuildDashboard(GrafanaAnalyseApiController.RebuildDashboardRequest("{}", "text"))
            assertEquals(500, rebuild.statusCode.value())
        }
    }

    @Test
    fun `the exception message helper handles a missing message`() {
        assertEquals("boom", controller.exceptionMessage(RuntimeException("boom")))
        assertEquals("", controller.exceptionMessage(RuntimeException()))
    }

    @Test
    fun `both messages are translated with their detail argument`() {
        whenever(i18n.t(any(), any())).thenReturn("t")

        whenever(analysis.generateDummyData(any())).thenThrow(RuntimeException("boom"))
        assertEquals("t", (controller.generateDummyData(GrafanaAnalyseApiController.GenerateDummyDataRequest("{}")).body as Map<*, *>)["error"])

        whenever(analysis.rebuildDashboard(any(), any())).thenThrow(RuntimeException("boom"))
        assertEquals("t", (controller.rebuildDashboard(GrafanaAnalyseApiController.RebuildDashboardRequest("{}", "x")).body as Map<*, *>)["error"])
    }

    @Test
    fun `without a stored config the router and claude specs carry only submitted keys`() {
        val repo: AiConfigPort = mock()
        whenever(repo.findAiConfig()).thenReturn(null)

        val specs = mutableListOf<ProviderSpec>()
        val recording =
            object : AiConnectionTestGateway {
                override fun testConnection(spec: ProviderSpec): AiConnectionTestPort.TestResult {
                    specs += spec
                    return AiConnectionTestPort.TestResult.Success(mapOf("ok" to true))
                }

                override fun resolveLmStudioModel(
                    requestedModel: String?,
                    requestedLmStudioUrl: String?,
                    existing: com.stacklens.domain.model.AiConfig,
                ): String = "live-model"
            }
        val recordingService = AiConnectionTestService(recording, repo, AiDefaultsPort { "http://default" })

        recordingService.testConnection(AiConnectionTestTarget.OPENROUTER, AiConnectionTestRequest(openRouterApiKey = "fresh"))
        recordingService.testConnection(AiConnectionTestTarget.CLAUDE_CODE, AiConnectionTestRequest(claudeCodeApiKey = "fresh-cc"))
        recordingService.testConnection(AiConnectionTestTarget.OLLAMA, AiConnectionTestRequest(ollamaUrl = ""))
        recordingService.testConnection(AiConnectionTestTarget.OLLAMA, AiConnectionTestRequest(ollamaUrl = "http://submitted"))
        recordingService.testConnection(AiConnectionTestTarget.LMSTUDIO, AiConnectionTestRequest())

        assertEquals(
            listOf(
                ProviderSpec.OpenRouter("fresh"),
                ProviderSpec.ClaudeCode("fresh-cc"),
                ProviderSpec.Ollama("http://localhost:11434"),
                ProviderSpec.Ollama("http://submitted"),
                ProviderSpec.LmStudio("http://localhost:1234"),
            ),
            specs,
        )
    }
}
