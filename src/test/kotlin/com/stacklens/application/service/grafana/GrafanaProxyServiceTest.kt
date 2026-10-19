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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GrafanaProxyServiceTest {
    private val analysis: GrafanaConfigLookupPort = mock()
    private val proxyPort: GrafanaProxyPort = mock()
    private val metricsParser: GrafanaDashboardMetricsPort = mock()

    private val service = GrafanaProxyService(analysis, proxyPort, metricsParser, GrafanaDemoDashboardFixture())

    private val config =
        GrafanaConfigInfo(
            id = 1L,
            name = "local",
            baseUrl = "http://localhost:3000/grafana/",
            username = "admin",
            password = "secret",
            apiToken = "token",
            orgId = 2L,
        )

    private val metrics = listOf(mapOf<String, Any?>("metric" to "cpu"))

    @Test
    fun `dashboard metrics are parsed out of a successful upstream response`() {
        whenever(analysis.findConfig(1L)).thenReturn(config)
        whenever(proxyPort.basicAuthHeader("admin", "secret")).thenReturn("Basic abc")
        whenever(
            proxyPort.proxyRequest(
                eq("http://localhost:3000/grafana/api/dashboards/uid/abc123"),
                eq("Basic abc"),
                eq(2L),
            ),
        ).thenReturn(GrafanaResponse(200, """{"dashboard":{}}"""))
        whenever(metricsParser.extractMetrics("""{"dashboard":{}}""")).thenReturn(metrics)

        assertEquals(GrafanaMetricsOutcome.Parsed(metrics), service.dashboardMetrics(1L, "abc123"))
    }

    @Test
    fun `an unknown config id answers as not found`() {
        whenever(analysis.findConfig(99L)).thenReturn(null)

        assertEquals(GrafanaMetricsOutcome.ConfigNotFound, service.dashboardMetrics(99L, "uid"))
        assertEquals(GrafanaProxyOutcome.ConfigNotFound, service.proxyApi(99L, "/api/x", null))
        assertEquals(GrafanaProxyOutcome.ConfigNotFound, service.homeDashboard(99L))
    }

    @Test
    fun `a blank dashboard uid is rejected with the translation key`() {
        whenever(analysis.findConfig(1L)).thenReturn(config)

        assertEquals(
            GrafanaMetricsOutcome.Rejected(messageKey = GrafanaProxyService.KEY_DASHBOARD_UID_EMPTY),
            service.dashboardMetrics(1L, "  "),
        )
    }

    @Test
    fun `an upstream error is passed through with its own status and body`() {
        whenever(analysis.findConfig(1L)).thenReturn(config)
        whenever(proxyPort.basicAuthHeader(any(), any())).thenReturn("Basic abc")
        whenever(proxyPort.proxyRequest(any(), any(), any())).thenReturn(GrafanaResponse(401, """{"message":"nope"}"""))

        assertEquals(GrafanaMetricsOutcome.Upstream(401, """{"message":"nope"}"""), service.dashboardMetrics(1L, "uid"))
    }

    @Test
    fun `only grafana api paths may be proxied`() {
        whenever(analysis.findConfig(1L)).thenReturn(config)

        val rejected = service.proxyApi(1L, "/api/grafana-analyse/proxy/1/public/metrics", null)

        assertEquals(GrafanaProxyOutcome.Rejected(messageKey = GrafanaProxyService.KEY_ONLY_API_PATHS), rejected)
    }

    @Test
    fun `path traversal attempts are rejected without an upstream call`() {
        whenever(analysis.findConfig(1L)).thenReturn(config)

        val rejected = service.proxyApi(1L, "/api/grafana-analyse/proxy/1/api/../secret", null)

        assertEquals(GrafanaProxyOutcome.Rejected(message = GrafanaProxyService.INVALID_PATH_CHARACTERS), rejected)
        verify(proxyPort, never()).proxyRequest(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `the proxy keeps query strings and the trailing slash free base url`() {
        whenever(analysis.findConfig(1L)).thenReturn(config)
        whenever(proxyPort.basicAuthHeader(any(), any())).thenReturn("Basic abc")
        whenever(proxyPort.proxyRequest(eq("http://localhost:3000/grafana/api/dashboards/home?orgId=2"), eq("Basic abc"), eq(2L)))
            .thenReturn(GrafanaResponse(200, "{}"))

        val outcome = service.proxyApi(1L, "/api/grafana-analyse/proxy/1/api/dashboards/home", "orgId=2")

        assertEquals(GrafanaProxyOutcome.Upstream(200, "{}"), outcome)
    }

    @Test
    fun `the demo prometheus query is restricted to loopback http endpoints`() {
        whenever(proxyPort.proxyRequest(any(), isNull(), isNull())).thenReturn(GrafanaResponse(200, "{}"))

        assertEquals(GrafanaProxyOutcome.Upstream(200, "{}"), service.queryDemoPrometheus("up", null, "http://localhost:9090"))
        assertEquals(
            GrafanaProxyOutcome.Rejected(messageKey = GrafanaProxyService.KEY_ONLY_LOCALHOST),
            service.queryDemoPrometheus("up", null, "http://example.com:9090"),
        )
        assertEquals(
            GrafanaProxyOutcome.Rejected(messageKey = GrafanaProxyService.KEY_ONLY_HTTP_URLS),
            service.queryDemoPrometheus("up", null, "ftp://localhost:9090"),
        )
        assertEquals(
            GrafanaProxyOutcome.Rejected(message = GrafanaProxyService.INVALID_PROMETHEUS_URL),
            service.queryDemoPrometheus("up", null, "not a url"),
        )
    }

    @Test
    fun `the prometheus query encodes the expression and the optional time`() {
        whenever(proxyPort.proxyRequest(eq("http://localhost:9090/api/v1/query?query=rate%28x%7Bj%3D1%7D%5B5m%5D%29&time=1700000000"), isNull(), isNull()))
            .thenReturn(GrafanaResponse(200, "{}"))

        val outcome = service.queryDemoPrometheus("rate(x{j=1}[5m])", 1700000000L, "http://localhost:9090")

        assertTrue(outcome is GrafanaProxyOutcome.Upstream)
    }

    @Test
    fun `fetching a dashboard json derives the api path from the url`() {
        whenever(proxyPort.fetchGrafanaJson(eq("http://gf.example/api/dashboards/uid/abc"), eq("u"), eq("p"), eq("t")))
            .thenReturn(GrafanaResponse(200, "{}"))

        assertEquals(
            GrafanaProxyOutcome.Upstream(200, "{}"),
            service.fetchDashboardJson("http://gf.example/d/abc/my-dashboard", "u", "p", "t"),
        )
    }

    @Test
    fun `a dashboard url without an uid falls back to the home dashboard`() {
        whenever(proxyPort.fetchGrafanaJson(eq("http://gf.example/api/dashboards/home"), any(), any(), any()))
            .thenReturn(GrafanaResponse(200, "[]"))

        assertEquals(
            GrafanaProxyOutcome.Upstream(200, "[]"),
            service.fetchDashboardJson("http://gf.example/dashboard", "u", "p", "t"),
        )
    }

    @Test
    fun `stored credentials fill a fetch request that sends none`() {
        whenever(analysis.findConfig(1L)).thenReturn(config)
        whenever(
            proxyPort.fetchGrafanaJson(
                eq("http://localhost:3000/api/dashboards/uid/abc"),
                eq("admin"),
                eq("secret"),
                eq("token"),
            ),
        ).thenReturn(GrafanaResponse(200, "{}"))

        assertEquals(
            GrafanaProxyOutcome.Upstream(200, "{}"),
            service.fetchDashboardJson("http://localhost:3000/d/abc/my-dashboard", "", "", "", configId = 1L),
        )
    }

    @Test
    fun `entered credentials win over the stored ones`() {
        whenever(analysis.findConfig(1L)).thenReturn(config)
        whenever(
            proxyPort.fetchGrafanaJson(
                eq("http://localhost:3000/api/dashboards/uid/abc"),
                eq("other"),
                eq("pw"),
                eq("tok"),
            ),
        ).thenReturn(GrafanaResponse(200, "{}"))

        assertEquals(
            GrafanaProxyOutcome.Upstream(200, "{}"),
            service.fetchDashboardJson("http://localhost:3000/d/abc/my-dashboard", "other", "pw", "tok", configId = 1L),
        )
    }

    @Test
    fun `an unknown config id for a dashboard url answers as not found`() {
        whenever(analysis.findConfig(99L)).thenReturn(null)

        assertEquals(
            GrafanaProxyOutcome.ConfigNotFound,
            service.fetchDashboardJson("http://localhost:3000/d/abc/x", "", "", "", configId = 99L),
        )
        verify(proxyPort, never()).fetchGrafanaJson(any(), any(), any(), any())
    }

    @Test
    fun `an empty grafana url is rejected with the translation key`() {
        assertEquals(
            GrafanaProxyOutcome.Rejected(messageKey = GrafanaProxyService.KEY_ENTER_GRAFANA_URL),
            service.fetchDashboardJson("  ", "u", "p", "t"),
        )
    }

    @Test
    fun `a fetch without a stored config keeps missing credentials empty`() {
        whenever(
            proxyPort.fetchGrafanaJson(
                eq("http://localhost:3000/api/dashboards/uid/abc"),
                eq(""),
                eq(""),
                eq(""),
            ),
        ).thenReturn(GrafanaResponse(200, "{}"))

        assertEquals(
            GrafanaProxyOutcome.Upstream(200, "{}"),
            service.fetchDashboardJson("http://localhost:3000/d/abc/my-dashboard", "", "", "", configId = null),
        )
        verify(analysis, never()).findConfig(any())
    }

    @Test
    fun `the home dashboard follows a redirect uri of the rendered page`() {
        whenever(analysis.findConfig(1L)).thenReturn(config)
        whenever(proxyPort.fetchGrafanaJsonWithRetry(eq("http://gf/api/dashboards/home"), eq("u"), eq("p"), eq("t")))
            .thenReturn(GrafanaResponse(200, """{"redirectUri":"/d/xyz/title"}"""))
        whenever(proxyPort.fetchGrafanaJson(eq("http://gf/api/dashboards/uid/xyz"), eq("u"), eq("p"), eq("t")))
            .thenReturn(GrafanaResponse(200, """{"dashboard":{}}"""))

        val stored = config.copy(baseUrl = "http://gf", username = "u", password = "p", apiToken = "t")
        whenever(analysis.findConfig(1L)).thenReturn(stored)

        assertEquals(GrafanaProxyOutcome.Upstream(200, """{"dashboard":{}}"""), service.homeDashboard(1L))
    }

    @Test
    fun `the home dashboard answers with the original payload when no redirect is present`() {
        val stored = config.copy(baseUrl = "http://gf")
        whenever(analysis.findConfig(1L)).thenReturn(stored)
        whenever(proxyPort.fetchGrafanaJsonWithRetry(any(), any(), any(), any())).thenReturn(GrafanaResponse(200, "{}"))

        assertEquals(GrafanaProxyOutcome.Upstream(200, "{}"), service.homeDashboard(1L))
    }

    @Test
    fun `the bundled demo dashboard json is served from the application`() {
        assertTrue(service.demoDashboardJson().contains("Demo Monitoring Dashboard"))
    }
}
