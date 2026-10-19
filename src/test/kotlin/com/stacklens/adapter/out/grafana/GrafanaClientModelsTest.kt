package com.stacklens.adapter.out.grafana

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GrafanaClientModelsTest {
    @Test
    fun `DashboardSearchItem default constructor`() {
        val i = GrafanaClient.DashboardSearchItem()
        assertEquals(0, i.id)
        assertEquals("", i.uid)
        assertEquals("", i.title)
        assertEquals("", i.type)
        assertEquals("", i.url)
        assertNull(i.folderTitle)
    }

    @Test
    fun `DashboardWrapper default constructor`() {
        val w = GrafanaClient.DashboardWrapper()
        assertNull(w.dashboard)
        assertNull(w.meta)
    }

    @Test
    fun `DashboardMeta default constructor`() {
        val m = GrafanaClient.DashboardMeta()
        assertNull(m.url)
        assertNull(m.folderTitle)
    }

    @Test
    fun `DashboardSearchItem with values`() {
        val i =
            GrafanaClient.DashboardSearchItem(
                id = 1,
                uid = "u",
                title = "t",
                type = "dash-db",
                url = "/d/u/t",
                folderTitle = "General",
            )
        assertEquals(1, i.id)
        assertEquals("u", i.uid)
        assertEquals("General", i.folderTitle)
    }
}
