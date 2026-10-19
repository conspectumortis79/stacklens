package com.stacklens.adapter.out.grafana

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrafanaClientDashboardFilterCoverageTest {
    private val client = GrafanaClient()

    private fun dash(
        uid: String = "abc123",
        title: String = "Umsatz",
        url: String = "/d/abc123/umsatz",
    ) = GrafanaClient.DashboardSearchItem(id = 1L, uid = uid, title = title, url = url)

    @Test
    fun `a filter that equals the uid matches`() {
        assertTrue(client.matchesDashboardFilter(dash(uid = "xy77", title = "Vertrieb", url = "/d/other/vertrieb"), listOf("XY77")))
    }

    @Test
    fun `a filter that matches only the title matches`() {
        assertTrue(client.matchesDashboardFilter(dash(title = "umsatz-report", url = "/d/abc123/x"), listOf("UMSATZ-REPORT")))
    }

    @Test
    fun `a filter embedding the uid between slashes matches`() {
        assertTrue(client.matchesDashboardFilter(dash(), listOf("http://grafana.local/d/abc123/view")))
    }

    @Test
    fun `a filter ending on the uid matches`() {
        assertTrue(client.matchesDashboardFilter(dash(), listOf("http://grafana.local/d/abc123")))
    }

    @Test
    fun `an unrelated filter does not match`() {
        assertFalse(client.matchesDashboardFilter(dash(), listOf("zz9999")))
    }
}
