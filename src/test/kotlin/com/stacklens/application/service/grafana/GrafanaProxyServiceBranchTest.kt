package com.stacklens.application.service.grafana

import com.stacklens.adapter.out.grafana.GrafanaDemoDashboardFixture
import com.stacklens.application.port.inbound.grafana.GrafanaMetricsOutcome
import com.stacklens.application.port.inbound.grafana.GrafanaProxyOutcome
import com.stacklens.application.port.out.grafana.GrafanaConfigInfo
import com.stacklens.application.port.out.grafana.GrafanaConfigLookupPort
import com.stacklens.application.port.out.grafana.GrafanaDashboardMetricsPort
import com.stacklens.application.port.out.grafana.GrafanaProxyPort
import com.stacklens.application.port.out.grafana.GrafanaResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GrafanaProxyServiceBranchTest {
    private val analysis: GrafanaConfigLookupPort = mock()
    private val proxyPort: GrafanaProxyPort = mock()
    private val metricsParser: GrafanaDashboardMetricsPort = mock()
    private val service = GrafanaProxyService(analysis, proxyPort, metricsParser, GrafanaDemoDashboardFixture())

    private fun config(
        baseUrl: String = "http://gf.example/grafana/",
        id: Long = 1L,
    ) = GrafanaConfigInfo(id = id, name = "g", baseUrl = baseUrl, username = "u", password = "p", apiToken = "t", orgId = 2L)

    @Test
    fun `dashboard metrics are parsed only for a successful upstream answer`() {
        whenever(analysis.findConfig(1L)).thenReturn(config())
        whenever(proxyPort.basicAuthHeader(any(), any())).thenReturn("Basic x")
        whenever(proxyPort.proxyRequest(any(), anyOrNull(), anyOrNull())).thenReturn(GrafanaResponse(200, "{}"))
        whenever(metricsParser.extractMetrics("{}")).thenReturn(listOf(mapOf("metric" to "cpu")))

        assertEquals(
            GrafanaMetricsOutcome.Parsed(listOf(mapOf("metric" to "cpu"))),
            service.dashboardMetrics(1L, "uid"),
        )
    }

    @Test
    fun `a missing config short circuits every endpoint`() {
        whenever(analysis.findConfig(any())).thenReturn(null)

        assertEquals(GrafanaMetricsOutcome.ConfigNotFound, service.dashboardMetrics(9L, "uid"))
        assertEquals(GrafanaProxyOutcome.ConfigNotFound, service.proxyApi(9L, "/api/x", null))
        assertEquals(GrafanaProxyOutcome.ConfigNotFound, service.homeDashboard(9L))
        verify(proxyPort, never()).proxyRequest(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `an error status from the upstream skips the metrics parser`() {
        whenever(analysis.findConfig(1L)).thenReturn(config())
        whenever(proxyPort.proxyRequest(any(), anyOrNull(), anyOrNull())).thenReturn(GrafanaResponse(500, "boom"))

        assertEquals(GrafanaMetricsOutcome.Upstream(500, "boom"), service.dashboardMetrics(1L, "uid"))
        verify(metricsParser, never()).extractMetrics(any())
    }

    @Test
    fun `proxy paths outside the api namespace and traversal segments are rejected`() {
        whenever(analysis.findConfig(1L)).thenReturn(config())

        assertEquals(
            GrafanaProxyOutcome.Rejected(message = GrafanaProxyService.INVALID_PATH_CHARACTERS),
            service.proxyApi(1L, "/api/grafana-analyse/proxy/1/api/../secret", null),
        )
        assertEquals(
            GrafanaProxyOutcome.Rejected(message = GrafanaProxyService.INVALID_PATH_CHARACTERS),
            service.proxyApi(1L, "/api/grafana-analyse/proxy/1/api/./x", null),
        )
        assertEquals(
            GrafanaProxyOutcome.Rejected(messageKey = GrafanaProxyService.KEY_ONLY_API_PATHS),
            service.proxyApi(1L, "/api/grafana-analyse/proxy/1/dashboards", null),
        )
    }

    @Test
    fun `a proxy call without a query string keeps the plain path`() {
        whenever(analysis.findConfig(1L)).thenReturn(config())
        whenever(proxyPort.proxyRequest(eq("http://gf.example/grafana/api/dashboards/home"), anyOrNull(), anyOrNull()))
            .thenReturn(GrafanaResponse(200, "{}"))

        assertEquals(
            GrafanaProxyOutcome.Upstream(200, "{}"),
            service.proxyApi(1L, "/api/grafana-analyse/proxy/1/api/dashboards/home", null),
        )
    }

    @Test
    fun `an already trimmed base url is not cut twice`() {
        whenever(analysis.findConfig(2L)).thenReturn(config(baseUrl = "http://gf.example", id = 2L))
        whenever(proxyPort.basicAuthHeader(any(), any())).thenReturn("Basic trimmed")
        whenever(proxyPort.proxyRequest(eq("http://gf.example/api/folders?"), eq("Basic trimmed"), eq(2L)))
            .thenReturn(GrafanaResponse(201, "created"))

        assertEquals(
            GrafanaProxyOutcome.Upstream(201, "created"),
            service.proxyApi(2L, "/api/grafana-analyse/proxy/2/api/folders", ""),
        )
    }

    @Test
    fun `a malformed grafana url is rejected before any upstream call`() {
        assertEquals(
            GrafanaProxyOutcome.Rejected(message = GrafanaProxyService.INVALID_URL),
            service.fetchDashboardJson("ht tp://bad url", "u", "p", "t"),
        )
        verify(proxyPort, never()).fetchGrafanaJson(any(), any(), any(), any())
    }

    @Test
    fun `a non http grafana url is rejected with the translation key`() {
        assertEquals(
            GrafanaProxyOutcome.Rejected(messageKey = GrafanaProxyService.KEY_ONLY_HTTP_URLS),
            service.fetchDashboardJson("ftp://gf.example/d/abc/x", "u", "p", "t"),
        )
    }

    @Test
    fun `a url without a path falls back to the home dashboard endpoint`() {
        whenever(proxyPort.fetchGrafanaJson(eq("http://gf.example/api/dashboards/home"), eq("u"), eq("p"), eq("t")))
            .thenReturn(GrafanaResponse(200, "[]"))

        assertEquals(
            GrafanaProxyOutcome.Upstream(200, "[]"),
            service.fetchDashboardJson("http://gf.example", "u", "p", "t"),
        )
    }

    @Test
    fun `a dashboard url with an uid is resolved through the uid endpoint`() {
        whenever(proxyPort.fetchGrafanaJson(eq("http://gf.example/api/dashboards/uid/abc"), any(), any(), any()))
            .thenReturn(GrafanaResponse(200, "{}"))

        assertEquals(
            GrafanaProxyOutcome.Upstream(200, "{}"),
            service.fetchDashboardJson("http://gf.example/d/abc/title?x=1", "u", "p", "t"),
        )
    }

    @Test
    fun `the home dashboard follows the redirect of a successful answer`() {
        whenever(analysis.findConfig(1L)).thenReturn(config())
        whenever(proxyPort.fetchGrafanaJsonWithRetry(any(), any(), any(), any()))
            .thenReturn(GrafanaResponse(200, """{"redirectUri":"/d/xyz/title"}"""))
        whenever(proxyPort.fetchGrafanaJson(eq("http://gf.example/grafana/api/dashboards/uid/xyz"), any(), any(), any()))
            .thenReturn(GrafanaResponse(200, "followed"))

        assertEquals(GrafanaProxyOutcome.Upstream(200, "followed"), service.homeDashboard(1L))
    }

    @Test
    fun `the home dashboard without a redirect returns the first answer`() {
        whenever(analysis.findConfig(1L)).thenReturn(config())
        whenever(proxyPort.fetchGrafanaJsonWithRetry(any(), any(), any(), any()))
            .thenReturn(GrafanaResponse(200, "no redirect here"))

        assertEquals(GrafanaProxyOutcome.Upstream(200, "no redirect here"), service.homeDashboard(1L))
    }

    @Test
    fun `a failing home dashboard answer is returned without a second call`() {
        whenever(analysis.findConfig(1L)).thenReturn(config())
        whenever(proxyPort.fetchGrafanaJsonWithRetry(any(), any(), any(), any()))
            .thenReturn(GrafanaResponse(404, "missing"))

        assertEquals(GrafanaProxyOutcome.Upstream(404, "missing"), service.homeDashboard(1L))
        verify(proxyPort, never()).fetchGrafanaJson(any(), any(), any(), any())
    }

    @Test
    fun `the prometheus endpoint rejects malformed and foreign hosts`() {
        assertEquals(
            GrafanaProxyOutcome.Rejected(message = GrafanaProxyService.INVALID_PROMETHEUS_URL),
            service.queryDemoPrometheus("up", null, "http://bad url"),
        )
        assertEquals(
            GrafanaProxyOutcome.Rejected(messageKey = GrafanaProxyService.KEY_ONLY_HTTP_URLS),
            service.queryDemoPrometheus("up", null, "https+kt://localhost:9090"),
        )
        assertEquals(
            GrafanaProxyOutcome.Rejected(messageKey = GrafanaProxyService.KEY_ONLY_LOCALHOST),
            service.queryDemoPrometheus("up", 1L, "http://elsewhere:9090"),
        )
    }

    @Test
    fun `the demo dashboard fixture comes from the application layer`() {
        assertTrue(service.demoDashboardJson().contains("demo-monitoring"))
    }

    private fun assertTrue(condition: Boolean) =
        org.junit.jupiter.api.Assertions
            .assertTrue(condition)
}
