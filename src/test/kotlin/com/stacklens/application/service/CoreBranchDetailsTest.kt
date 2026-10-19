package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.grafana.DashboardMetricsParser
import com.stacklens.application.port.inbound.grafana.GrafanaMetricsOutcome
import com.stacklens.application.port.inbound.grafana.GrafanaProxyOutcome
import com.stacklens.application.port.out.grafana.GrafanaConfigLookupPort
import com.stacklens.application.service.grafana.GrafanaUrlValidator
import com.stacklens.domain.docker.ComposeContainerEntry
import com.stacklens.domain.docker.ComposeContainerState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CoreBranchDetailsTest {
    @Test
    fun `grafana outcomes carry their default messages`() {
        assertEquals("", GrafanaProxyOutcome.Rejected().message)
        assertEquals("", GrafanaProxyOutcome.Rejected(messageKey = "key").message)
        assertEquals("key", GrafanaMetricsOutcome.Rejected("key").messageKey)
        assertEquals(200, GrafanaProxyOutcome.Upstream(200, "b").status)
        assertEquals("b", GrafanaProxyOutcome.Upstream(200, "b").body)
        assertEquals(0, GrafanaMetricsOutcome.Parsed(emptyList()).metrics.size)
    }

    @Test
    fun `a url without a scheme is rejected by the grafana url validator`() {
        val result = GrafanaUrlValidator.parseHttpUrl("example.com/path")

        assertTrue(result is GrafanaUrlValidator.HttpUrlResult.Err)
    }

    @Test
    fun `an exited container with a zero exit code is healthy but a dead one is not`() {
        assertTrue(!ComposeContainerState(listOf(ComposeContainerEntry("a", "exited", 0))).anyExitedNonZero)
        assertTrue(ComposeContainerState(listOf(ComposeContainerEntry("a", "dead", 0))).anyExitedNonZero)
        assertEquals(1, ComposeContainerState(listOf(ComposeContainerEntry("a", "running", 0))).running)
    }

    @Test
    fun `a panel target without any metric expression yields no entry`() {
        val json =
            """
            {"dashboard":{"panels":[{"id":1,"title":"empty","type":"timeseries",
            "targets":[{"refId":"A"}]}]}}
            """.trimIndent()

        assertEquals(emptyList<Map<String, Any?>>(), DashboardMetricsParser(ObjectMapper()).extractMetricsList(json))
    }
}

class FinalCoverageDetailsTest {
    @Test
    fun `the preferences service default argument is exercised by single argument construction`() {
        val port = mock<com.stacklens.application.port.out.UiPreferencesPort>()
        whenever(port.getAll()).thenReturn(mapOf("a" to "b"))

        assertEquals(
            "b",
            com.stacklens.application.service.config
                .UiPreferencesService(port, mock())
                .preference("a"),
        )
    }

    @Test
    fun `an opaque http url without a path still resolves to the home dashboard`() {
        val proxyPort = mock<com.stacklens.application.port.out.grafana.GrafanaProxyPort>()
        whenever(proxyPort.fetchGrafanaJson(any(), any(), any(), any()))
            .thenReturn(
                com.stacklens.application.port.out.grafana
                    .GrafanaResponse(200, "opaque"),
            )

        val outcome =
            com.stacklens.application.service.grafana
                .GrafanaProxyService(
                    mock(),
                    proxyPort,
                    mock(),
                    com.stacklens.adapter.out.grafana
                        .GrafanaDemoDashboardFixture(),
                ).fetchDashboardJson("http:gf.example", "u", "p", "t")

        assertEquals("opaque", (outcome as GrafanaProxyOutcome.Upstream).body)
    }

    @Test
    fun `statuses below and above the success band are both treated as upstream answers`() {
        val proxyPort = mock<com.stacklens.application.port.out.grafana.GrafanaProxyPort>()
        val analysis =
            mock<com.stacklens.application.port.out.grafana.GrafanaConfigLookupPort>()
        whenever(analysis.findConfig(1L))
            .thenReturn(
                com.stacklens.application.port.out.grafana.GrafanaConfigInfo(
                    1L,
                    "g",
                    "http://gf",
                    "u",
                    "p",
                    "t",
                    1L,
                ),
            )
        val service =
            com.stacklens.application.service.grafana.GrafanaProxyService(
                analysis,
                proxyPort,
                mock(),
                com.stacklens.adapter.out.grafana
                    .GrafanaDemoDashboardFixture(),
            )
        whenever(proxyPort.proxyRequest(any(), anyOrNull(), anyOrNull())).thenReturn(
            com.stacklens.application.port.out.grafana
                .GrafanaResponse(100, "continue"),
        )

        assertEquals(
            GrafanaMetricsOutcome.Upstream(100, "continue"),
            service.dashboardMetrics(1L, "uid"),
        )

        whenever(proxyPort.fetchGrafanaJsonWithRetry(any(), any(), any(), any()))
            .thenReturn(
                com.stacklens.application.port.out.grafana
                    .GrafanaResponse(300, "multiple choices"),
            )

        assertEquals(GrafanaProxyOutcome.Upstream(300, "multiple choices"), service.homeDashboard(1L))
    }
}
