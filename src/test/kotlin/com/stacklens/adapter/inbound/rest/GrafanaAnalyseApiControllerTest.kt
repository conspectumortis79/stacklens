package com.stacklens.adapter.inbound.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.inbound.grafana.GrafanaAnalysisUseCase
import com.stacklens.application.port.inbound.grafana.GrafanaMetricsOutcome
import com.stacklens.application.port.inbound.grafana.GrafanaProxyOutcome
import com.stacklens.application.port.inbound.grafana.GrafanaProxyUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.service.grafana.GrafanaProxyService
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GrafanaAnalyseApiControllerTest {
    private val analysisUseCase: GrafanaAnalysisUseCase = mock()
    private val proxyUseCase: GrafanaProxyUseCase = mock()
    private val i18n: I18nUseCase = mock()
    private val controller = GrafanaAnalyseApiController(analysisUseCase, proxyUseCase, ObjectMapper(), i18n)

    @Test
    fun `metrics answers the parsed list with status 200`() {
        whenever(proxyUseCase.dashboardMetrics(1L, "uid")).thenReturn(GrafanaMetricsOutcome.Parsed(listOf(mapOf("metric" to "cpu"))))

        val response = controller.getDashboardMetrics(1L, "uid")

        assertEquals(200, response.statusCode.value())
        assertEquals(listOf(mapOf("metric" to "cpu")), response.body)
    }

    @Test
    fun `metrics maps the use case outcomes to their http shapes`() {
        whenever(proxyUseCase.dashboardMetrics(9L, "uid")).thenReturn(GrafanaMetricsOutcome.ConfigNotFound)
        assertEquals(404, controller.getDashboardMetrics(9L, "uid").statusCode.value())

        whenever(proxyUseCase.dashboardMetrics(1L, " "))
            .thenReturn(GrafanaMetricsOutcome.Rejected(messageKey = "grafana_analysis.be_dashboard_uid_empty"))
        whenever(i18n.t(any(), *emptyArray())).thenReturn("uid fehlt")
        val rejected = controller.getDashboardMetrics(1L, " ")
        assertEquals(400, rejected.statusCode.value())
        assertEquals(mapOf("error" to "uid fehlt"), rejected.body)

        whenever(proxyUseCase.dashboardMetrics(1L, "uid")).thenReturn(GrafanaMetricsOutcome.Upstream(502, "bad gateway"))
        val upstream = controller.getDashboardMetrics(1L, "uid")
        assertEquals(502, upstream.statusCode.value())
        assertEquals("bad gateway", upstream.body)
    }

    @Test
    fun `the proxy endpoint forwards the request uri and query string`() {
        val request = mock<HttpServletRequest>()
        whenever(request.requestURI).thenReturn("/api/grafana-analyse/proxy/7/api/dashboards/home")
        whenever(request.queryString).thenReturn("orgId=1")
        whenever(proxyUseCase.proxyApi(eq(7L), any(), any())).thenReturn(GrafanaProxyOutcome.Upstream(200, "{}"))

        val response = controller.proxyGrafanaApi(7L, request)

        assertEquals(200, response.statusCode.value())
        assertEquals("{}", response.body)
        org.mockito.kotlin
            .verify(proxyUseCase)
            .proxyApi(7L, "/api/grafana-analyse/proxy/7/api/dashboards/home", "orgId=1")
    }

    @Test
    fun `a rejected proxy path answers as a bad request with a json error body`() {
        val request = mock<HttpServletRequest>()
        whenever(request.requestURI).thenReturn("/api/grafana-analyse/proxy/7/public/x")
        whenever(request.queryString).thenReturn(null)
        whenever(proxyUseCase.proxyApi(any(), any(), anyOrNull()))
            .thenReturn(GrafanaProxyOutcome.Rejected(message = GrafanaProxyService.INVALID_PATH_CHARACTERS))

        assertEquals("{\"error\":\"Invalid path characters\"}", controller.proxyGrafanaApi(7L, request).body)
    }

    @Test
    fun `the prometheus query endpoint forwards its parameters`() {
        whenever(proxyUseCase.queryDemoPrometheus(eq("up"), eq(null), eq("http://localhost:9090")))
            .thenReturn(GrafanaProxyOutcome.Upstream(200, "{}"))

        assertEquals("{}", controller.demoprometheusQuery("up", null, "http://localhost:9090").body)
    }

    @Test
    fun `fetching dashboard json forwards the credentials of the request`() {
        whenever(proxyUseCase.fetchDashboardJson("http://gf", "u", "p", "t")).thenReturn(GrafanaProxyOutcome.Upstream(200, "{}"))

        val response =
            controller.fetchDashboardJson(
                GrafanaAnalyseApiController.FetchDashboardJsonRequest(
                    grafanaUrl = "http://gf",
                    username = "u",
                    password = "p",
                    apiToken = "t",
                ),
            )

        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `fetching dashboard json forwards the selected config id`() {
        whenever(proxyUseCase.fetchDashboardJson("http://gf", "", "", "", 7L))
            .thenReturn(GrafanaProxyOutcome.Upstream(200, "{}"))

        controller.fetchDashboardJson(
            GrafanaAnalyseApiController.FetchDashboardJsonRequest(
                grafanaUrl = "http://gf",
                configId = 7L,
            ),
        )

        verify(proxyUseCase).fetchDashboardJson("http://gf", "", "", "", 7L)
    }

    @Test
    fun `an unknown config id maps to 404 on the dashboard json endpoint`() {
        whenever(proxyUseCase.fetchDashboardJson(any(), any(), any(), any(), anyOrNull()))
            .thenReturn(GrafanaProxyOutcome.ConfigNotFound)

        val response =
            controller.fetchDashboardJson(
                GrafanaAnalyseApiController.FetchDashboardJsonRequest(grafanaUrl = "http://gf", configId = 3L),
            )

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `the home dashboard endpoint renders the upstream payload`() {
        whenever(proxyUseCase.homeDashboard(1L)).thenReturn(GrafanaProxyOutcome.Upstream(200, "home"))

        assertEquals("home", controller.homeDashboard(1L).body)
    }

    @Test
    fun `the demo dashboard fixture is served as json`() {
        whenever(proxyUseCase.demoDashboardJson()).thenReturn("""{"dashboard":{}}""")

        val response = controller.demoDashboardJson()

        assertEquals("""{"dashboard":{}}""", response.body)
        assertEquals("application/json", response.headers.getFirst("Content-Type"))
    }

    @Test
    fun `dummy data generation validates the dashboard json before asking the use case`() {
        val blank =
            controller.generateDummyData(
                GrafanaAnalyseApiController.GenerateDummyDataRequest("  "),
            )
        assertEquals(400, blank.statusCode.value())

        whenever(i18n.t(any(), *emptyArray())).thenReturn("invalid json")
        val broken = controller.generateDummyData(GrafanaAnalyseApiController.GenerateDummyDataRequest("not json"))
        assertEquals(400, broken.statusCode.value())

        whenever(analysisUseCase.generateDummyData("""{"a":1}""")).thenReturn(mapOf("ok" to true))
        assertEquals(mapOf("ok" to true), (controller.generateDummyData(GrafanaAnalyseApiController.GenerateDummyDataRequest("""{"a":1}"""))).body)
    }

    @Test
    fun `rebuilding a dashboard requires both instructions and json`() {
        whenever(i18n.t(any(), *emptyArray())).thenReturn("missing")

        assertEquals(400, controller.rebuildDashboard(GrafanaAnalyseApiController.RebuildDashboardRequest("{}", "  ")).statusCode.value())
        assertEquals(400, controller.rebuildDashboard(GrafanaAnalyseApiController.RebuildDashboardRequest("", "text")).statusCode.value())

        whenever(analysisUseCase.rebuildDashboard("{}", "text")).thenReturn(mapOf("patched" to true))
        assertTrue(
            (controller.rebuildDashboard(GrafanaAnalyseApiController.RebuildDashboardRequest("{}", "text")).body as Map<*, *>)
                .containsKey("patched"),
        )
    }

    @Test
    fun `a failing ai call is reported as a server error with the translated message`() {
        whenever(analysisUseCase.generateDummyData(any())).thenThrow(RuntimeException("ai down"))
        whenever(i18n.t(eq("grafana_analysis.be_ai_generation_failed"), any())).thenReturn("generation failed")

        val response = controller.generateDummyData(GrafanaAnalyseApiController.GenerateDummyDataRequest("{}"))

        assertEquals(500, response.statusCode.value())
        assertEquals(mapOf("error" to "generation failed"), response.body)
    }

    @Test
    fun `an unmapped key is answered verbatim`() {
        val bare =
            GrafanaAnalyseApiController(
                analysisUseCase,
                proxyUseCase,
                ObjectMapper(),
                com.stacklens.application.service
                    .FakeI18n(),
            )
        whenever(proxyUseCase.dashboardMetrics(1L, " "))
            .thenReturn(GrafanaMetricsOutcome.Rejected(messageKey = "grafana_analysis.be_dashboard_uid_empty"))

        assertEquals(
            mapOf("error" to "grafana_analysis.be_dashboard_uid_empty"),
            bare.getDashboardMetrics(1L, " ").body,
        )
    }

    @Test
    fun `an informational upstream status is passed through unchanged`() {
        whenever(proxyUseCase.dashboardMetrics(1L, "uid")).thenReturn(GrafanaMetricsOutcome.Upstream(100, "continue"))

        val response = controller.getDashboardMetrics(1L, "uid")

        assertEquals(100, response.statusCode.value())
        assertEquals("continue", response.body)
    }

    @Test
    fun `an informational home dashboard status is returned unchanged`() {
        whenever(proxyUseCase.homeDashboard(2L)).thenReturn(GrafanaProxyOutcome.Upstream(100, "{}"))

        val response = controller.homeDashboard(2L)

        assertEquals(100, response.statusCode.value())
        assertEquals("{}", response.body)
    }
}
