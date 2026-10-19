package com.stacklens.adapter.inbound.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.filesystem.ClasspathResourceAdapter
import com.stacklens.application.port.inbound.grafana.GrafanaAnalysisUseCase
import com.stacklens.application.port.inbound.grafana.GrafanaProxyOutcome
import com.stacklens.application.port.inbound.grafana.GrafanaProxyUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.support.ResourcePatternResolver

class GrafanaControllerOutcomeMatrixTest {
    private val proxyUseCase: GrafanaProxyUseCase = mock()

    private val analysisUseCase: GrafanaAnalysisUseCase = mock()

    private val i18n: I18nUseCase = mock()

    private val controller = GrafanaAnalyseApiController(analysisUseCase, proxyUseCase, ObjectMapper(), i18n)

    private val bareController =
        GrafanaAnalyseApiController(
            analysisUseCase,
            proxyUseCase,
            ObjectMapper(),
            com.stacklens.application.service
                .FakeI18n(),
        )

    @Test
    fun `the request payloads fall back to their empty defaults`() {
        assertEquals("", GrafanaAnalyseApiController.GenerateDummyDataRequest().dashboardJson)
        assertEquals("", GrafanaAnalyseApiController.RebuildDashboardRequest().instructions)
        assertEquals("", GrafanaAnalyseApiController.FetchDashboardJsonRequest().grafanaUrl)
        assertEquals(
            GrafanaAnalyseApiController.FetchDashboardJsonRequest(
                grafanaUrl = "http://gf",
                username = "u",
                password = "p",
                apiToken = "t",
            ),
            GrafanaAnalyseApiController.FetchDashboardJsonRequest("http://gf", "u", "p", "t"),
        )
    }

    @Test
    fun `every relay endpoint answers a missing config with 404`() {
        whenever(proxyUseCase.proxyApi(any(), any(), anyOrNull())).thenReturn(GrafanaProxyOutcome.ConfigNotFound)
        whenever(proxyUseCase.queryDemoPrometheus(any(), anyOrNull(), any())).thenReturn(GrafanaProxyOutcome.ConfigNotFound)
        whenever(proxyUseCase.fetchDashboardJson(any(), any(), any(), any(), anyOrNull())).thenReturn(GrafanaProxyOutcome.ConfigNotFound)
        whenever(proxyUseCase.homeDashboard(any())).thenReturn(GrafanaProxyOutcome.ConfigNotFound)

        assertEquals(404, controller.proxyGrafanaApi(1L, request("/api/grafana-analyse/proxy/1/api/x")).statusCode.value())
        assertEquals(404, controller.demoprometheusQuery("up", null, "http://localhost:9090").statusCode.value())
        assertEquals(
            404,
            controller
                .fetchDashboardJson(
                    GrafanaAnalyseApiController.FetchDashboardJsonRequest(),
                ).statusCode
                .value(),
        )
        assertEquals(404, controller.homeDashboard(1L).statusCode.value())
    }

    @Test
    fun `a rejection with a raw message is rendered without translation`() {
        whenever(proxyUseCase.proxyApi(any(), any(), anyOrNull()))
            .thenReturn(GrafanaProxyOutcome.Rejected(message = "Invalid path characters"))
        whenever(proxyUseCase.queryDemoPrometheus(any(), anyOrNull(), any()))
            .thenReturn(GrafanaProxyOutcome.Rejected(message = "Invalid Prometheus URL"))
        whenever(proxyUseCase.fetchDashboardJson(any(), any(), any(), any(), anyOrNull()))
            .thenReturn(GrafanaProxyOutcome.Rejected(message = "Invalid URL"))
        whenever(proxyUseCase.homeDashboard(any())).thenReturn(GrafanaProxyOutcome.Rejected(message = "no route"))

        assertEquals("""{"error":"Invalid path characters"}""", controller.proxyGrafanaApi(1L, request("/api/x")).body)
        assertEquals("""{"error":"Invalid Prometheus URL"}""", controller.demoprometheusQuery("up", null, "http://x").body)
        assertEquals(
            """{"error":"Invalid URL"}""",
            controller
                .fetchDashboardJson(
                    GrafanaAnalyseApiController.FetchDashboardJsonRequest(grafanaUrl = "http://x"),
                ).body,
        )
        assertEquals("""{"error":"no route"}""", controller.homeDashboard(1L).body)
    }

    @Test
    fun `an upstream answer keeps its status and body on every relay endpoint`() {
        whenever(proxyUseCase.proxyApi(any(), any(), anyOrNull())).thenReturn(GrafanaProxyOutcome.Upstream(204, ""))
        whenever(proxyUseCase.queryDemoPrometheus(any(), anyOrNull(), any())).thenReturn(GrafanaProxyOutcome.Upstream(200, "{}"))
        whenever(proxyUseCase.fetchDashboardJson(any(), any(), any(), any(), anyOrNull())).thenReturn(GrafanaProxyOutcome.Upstream(201, "created"))
        whenever(proxyUseCase.homeDashboard(any())).thenReturn(GrafanaProxyOutcome.Upstream(200, "home"))

        assertEquals(204, controller.proxyGrafanaApi(1L, request("/api/x")).statusCode.value())
        assertEquals("{}", controller.demoprometheusQuery("up", 1L, "http://x").body)
        assertEquals(
            "created",
            controller
                .fetchDashboardJson(
                    GrafanaAnalyseApiController.FetchDashboardJsonRequest(grafanaUrl = "http://x"),
                ).body,
        )
        assertEquals("home", controller.homeDashboard(1L).body)
    }

    @Test
    fun `a failing ai rebuild answers with the translated server error`() {
        whenever(analysisUseCase.rebuildDashboard(any(), any())).thenThrow(RuntimeException("ai offline"))
        whenever(i18n.t(eq("grafana_analysis.be_ai_rebuild_failed"), any())).thenReturn("künstliche intelligenz weg")

        val response =
            controller.rebuildDashboard(
                GrafanaAnalyseApiController.RebuildDashboardRequest(dashboardJson = "{}", instructions = "bitte"),
            )

        assertEquals(500, response.statusCode.value())
        assertEquals("künstliche intelligenz weg", (response.body as Map<*, *>)["error"])
    }

    @Test
    fun `a broken dashboard json is rejected by both ai endpoints`() {
        whenever(i18n.t(any(), any())).thenReturn(" kaputt ")

        val generate = controller.generateDummyData(GrafanaAnalyseApiController.GenerateDummyDataRequest("{oops"))
        val rebuild = controller.rebuildDashboard(GrafanaAnalyseApiController.RebuildDashboardRequest("{oops", "text"))

        assertEquals(400, generate.statusCode.value())
        assertEquals(" kaputt ", (generate.body as Map<*, *>)["error"])
        assertEquals(400, rebuild.statusCode.value())
    }

    @Test
    fun `without an i18n mapping the raw key is answered`() {
        val response = bareController.generateDummyData(GrafanaAnalyseApiController.GenerateDummyDataRequest("{oops"))

        assertEquals(400, response.statusCode.value())
        assertTrue(
            (response.body as Map<*, *>)["error"]
                .toString()
                .startsWith("grafana_analysis.be_dashboard_json_invalid"),
        )
    }

    private fun request(uri: String): jakarta.servlet.http.HttpServletRequest {
        val request = mock<jakarta.servlet.http.HttpServletRequest>()
        whenever(request.requestURI).thenReturn(uri)
        return request
    }
}

class ClasspathResourceAdapterExistsTest {
    private fun adapterWith(exists: Boolean): ClasspathResourceAdapter {
        val resource = mock<org.springframework.core.io.Resource>()
        whenever(resource.exists()).thenReturn(exists)
        val resolver = mock<ResourcePatternResolver>()
        whenever(resolver.getResource("classpath:agents/pipeline/lena.md")).thenReturn(resource)
        return ClasspathResourceAdapter(resolver)
    }

    @Test
    fun `a present classpath resource is reported as existing`() {
        assertTrue(adapterWith(exists = true).exists("agents/pipeline/lena.md"))
    }

    @Test
    fun `an absent classpath resource is reported as missing`() {
        assertFalse(adapterWith(exists = false).exists("agents/pipeline/lena.md"))
    }
}
