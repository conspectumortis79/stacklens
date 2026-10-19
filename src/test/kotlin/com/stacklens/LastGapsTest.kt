package com.stacklens

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.inbound.rest.GrafanaAnalyseApiController
import com.stacklens.adapter.out.docker.DockerCommandAdapter
import com.stacklens.adapter.out.docker.DockerOsPaths
import com.stacklens.adapter.out.docker.DockerProcessEnvironment
import com.stacklens.adapter.out.docker.DockerSocketDiscoveryAdapter
import com.stacklens.adapter.out.grafana.GrafanaDemoDashboardFixture
import com.stacklens.adapter.out.platform.HostBrowserAdapter
import com.stacklens.application.port.inbound.config.AiConnectionTestRequest
import com.stacklens.application.port.inbound.config.AiConnectionTestTarget
import com.stacklens.application.port.inbound.grafana.GrafanaAnalysisUseCase
import com.stacklens.application.port.inbound.grafana.GrafanaProxyOutcome
import com.stacklens.application.port.inbound.grafana.GrafanaProxyUseCase
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiConnectionTestGateway
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.config.AiDefaultsPort
import com.stacklens.application.port.out.grafana.GrafanaConfigLookupPort
import com.stacklens.application.port.out.grafana.GrafanaProxyPort
import com.stacklens.application.port.out.grafana.GrafanaResponse
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.service.config.AiConnectionTestService
import com.stacklens.application.service.grafana.GrafanaProxyService
import com.stacklens.testsupport.FakePlatformPort
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LastGapsTest {
    @Test
    fun `a status just below the success band skips the redirect lookup`() {
        val analysis: GrafanaConfigLookupPort = mock()
        val proxyPort: GrafanaProxyPort = mock()
        whenever(analysis.findConfig(1L))
            .thenReturn(
                com.stacklens.application.port.out.grafana
                    .GrafanaConfigInfo(1L, "g", "http://gf", "u", "p", "t", 1L),
            )
        whenever(proxyPort.fetchGrafanaJsonWithRetry(any(), any(), any(), any())).thenReturn(GrafanaResponse(199, "early"))

        val outcome = GrafanaProxyService(analysis, proxyPort, mock(), GrafanaDemoDashboardFixture()).homeDashboard(1L)

        assertEquals(GrafanaProxyOutcome.Upstream(199, "early"), outcome)
    }

    @Test
    fun `a rejection with a translation key on a relay endpoint is translated`() {
        val proxyUseCase: GrafanaProxyUseCase = mock()
        val i18n = mock<com.stacklens.application.port.inbound.platform.I18nUseCase>()
        whenever(i18n.t(any(), *emptyArray())).thenReturn("nur http")
        val controller = GrafanaAnalyseApiController(mock(), proxyUseCase, ObjectMapper(), i18n)
        whenever(proxyUseCase.queryDemoPrometheus(any(), anyOrNull(), any()))
            .thenReturn(GrafanaProxyOutcome.Rejected(messageKey = "grafana_analysis.be_only_http_urls"))
        whenever(proxyUseCase.proxyApi(any(), any(), anyOrNull()))
            .thenReturn(GrafanaProxyOutcome.Rejected(messageKey = "grafana_analysis.be_only_api_paths"))
        whenever(proxyUseCase.fetchDashboardJson(any(), any(), any(), any(), anyOrNull()))
            .thenReturn(GrafanaProxyOutcome.Rejected(messageKey = "grafana_analysis.be_only_http_urls"))
        whenever(proxyUseCase.homeDashboard(any()))
            .thenReturn(GrafanaProxyOutcome.Rejected(messageKey = "grafana_analysis.be_only_http_urls"))
        val request = mock<HttpServletRequest>()
        whenever(request.requestURI).thenReturn("/api/grafana-analyse/proxy/1/api/x")

        assertEquals("""{"error":"nur http"}""", controller.proxyGrafanaApi(1L, request).body)
        assertEquals("""{"error":"nur http"}""", controller.demoprometheusQuery("up", null, "http://x").body)
        assertEquals(
            """{"error":"nur http"}""",
            controller
                .fetchDashboardJson(GrafanaAnalyseApiController.FetchDashboardJsonRequest(grafanaUrl = "http://x"))
                .body,
        )
        assertEquals("""{"error":"nur http"}""", controller.homeDashboard(1L).body)
    }

    @Test
    fun `an exception without a message is reported as an empty detail`() {
        val controller =
            GrafanaAnalyseApiController(
                mock(),
                mock(),
                ObjectMapper(),
                com.stacklens.application.service
                    .FakeI18n(),
            )
        val brokenJson: GrafanaAnalysisUseCase = mock()
        whenever(brokenJson.generateDummyData(any())).thenThrow(RuntimeException())
        whenever(brokenJson.rebuildDashboard(any(), any())).thenThrow(RuntimeException())
        val withBrokenAnalysis =
            GrafanaAnalyseApiController(
                brokenJson,
                mock(),
                ObjectMapper(),
                com.stacklens.application.service
                    .FakeI18n(),
            )

        val generate =
            withBrokenAnalysis
                .generateDummyData(GrafanaAnalyseApiController.GenerateDummyDataRequest("{broken"))
        assertEquals(400, generate.statusCode.value())
        assertTrue((generate.body as Map<*, *>)["error"].toString().startsWith("grafana_analysis.be_dashboard_json_invalid"))

        val rebuild =
            withBrokenAnalysis.rebuildDashboard(
                GrafanaAnalyseApiController.RebuildDashboardRequest(dashboardJson = "{broken", instructions = "text"),
            )
        assertEquals(400, rebuild.statusCode.value())

        whenever(brokenJson.generateDummyData(any())).thenThrow(RuntimeException("boom"))
        val withMessage = withBrokenAnalysis.generateDummyData(GrafanaAnalyseApiController.GenerateDummyDataRequest("{broken"))
        assertTrue((withMessage.body as Map<*, *>)["error"].toString().startsWith("grafana_analysis.be_dashboard_json_invalid"))

        val aiFailure = withBrokenAnalysis.generateDummyData(GrafanaAnalyseApiController.GenerateDummyDataRequest("{}"))
        assertEquals(500, aiFailure.statusCode.value())
        assertTrue((aiFailure.body as Map<*, *>)["error"].toString().startsWith("grafana_analysis.be_ai_generation_failed"))
        val aiRebuildFailure = withBrokenAnalysis.rebuildDashboard(GrafanaAnalyseApiController.RebuildDashboardRequest("{}", "text"))
        assertEquals(500, aiRebuildFailure.statusCode.value())
        assertTrue((aiRebuildFailure.body as Map<*, *>)["error"].toString().startsWith("grafana_analysis.be_ai_rebuild_failed"))
        assertDoesNotThrow { controller.demoDashboardJson() }
    }

    @Test
    fun `the real compose process start works without a work dir`() {
        val adapter = DockerCommandAdapter(DockerProcessEnvironment(DockerOsPaths(), DockerSocketDiscoveryAdapter()))

        val process = adapter.startComposeProcess(listOf("/bin/echo", "hello"), null)

        assertEquals(0, process.waitFor())
    }

    @Test
    fun `the browser adapter starts a real process`() {
        val adapter = HostBrowserAdapter(FakePlatformPort(OsType.LINUX))

        assertDoesNotThrow { adapter.start(listOf("/bin/echo", "hello")) }
    }

    @Test
    fun `submitted urls win over stored ones for every provider`() {
        val gateway: AiConnectionTestGateway = mock()
        val repo: AiConfigPort = mock()
        whenever(repo.findAiConfig()).thenReturn(null)
        whenever(gateway.testConnection(any())).thenReturn(AiConnectionTestPort.TestResult.Success(mapOf("ok" to true)))
        val service = AiConnectionTestService(gateway, repo, AiDefaultsPort { "http://default" })

        service.testConnection(AiConnectionTestTarget.OLLAMA, AiConnectionTestRequest(ollamaUrl = "http://submitted:11434"))
        service.testConnection(AiConnectionTestTarget.LMSTUDIO, AiConnectionTestRequest(lmStudioUrl = "http://submitted:1234"))

        verifySpec(gateway, ProviderSpec.Ollama("http://submitted:11434"))
        verifySpec(gateway, ProviderSpec.LmStudio("http://submitted:1234"))
    }

    private fun verifySpec(
        gateway: AiConnectionTestGateway,
        spec: ProviderSpec,
    ) {
        org.mockito.Mockito
            .verify(gateway)
            .testConnection(eq(spec))
    }
}
