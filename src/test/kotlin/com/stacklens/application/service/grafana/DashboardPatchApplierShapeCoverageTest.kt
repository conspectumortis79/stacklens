package com.stacklens.application.service.grafana

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DashboardPatchApplierShapeCoverageTest {
    private val codec = JacksonJsonCodecAdapter(ObjectMapper())
    private val applier = DashboardPatchApplier(codec)

    @Test
    fun `a dashboard object is patched and the matching panel is replaced`() {
        val original =
            """{"dashboard":{"title":"old","panels":[{"id":1,"a":0},{"id":2,"a":0}]}}"""
        val patch =
            """{"dashboardPatch":{"title":"new"},"modifiedPanels":[{"id":2,"a":9}]}"""

        val result = codec.decodeMap(applier.applyPatch(original, patch))
        val dashboard = result["dashboard"] as Map<*, *>

        assertEquals("new", dashboard["title"])
        assertEquals(listOf(mapOf("id" to 1, "a" to 0), mapOf("id" to 2, "a" to 9)), dashboard["panels"])
    }

    @Test
    fun `a payload without a dashboard object is patched at the top level`() {
        val original = """{"title":"old"}"""
        val patch = """{"dashboardPatch":{"title":"new"}}"""

        val result = codec.decodeMap(applier.applyPatch(original, patch))

        assertEquals("new", result["title"])
    }

    @Test
    fun `a panel patch for an unknown panel identifier leaves the panels untouched`() {
        val original = """{"dashboard":{"panels":[{"id":1,"a":0}]}}"""
        val patch = """{"modifiedPanels":[{"id":42,"a":9}]}"""

        val result = codec.decodeMap(applier.applyPatch(original, patch))
        val dashboard = result["dashboard"] as Map<*, *>

        assertEquals(listOf(mapOf("id" to 1, "a" to 0)), dashboard["panels"])
    }

    @Test
    fun `a panel entry that is no object is skipped while patching`() {
        val original = """{"dashboard":{"panels":["not an object",{"id":2}]}}"""
        val patch = """{"modifiedPanels":[{"id":2,"a":9}]}"""

        val result = codec.decodeMap(applier.applyPatch(original, patch))
        val dashboard = result["dashboard"] as Map<*, *>
        val panels = dashboard["panels"] as List<*>

        assertEquals("not an object", panels.first())
        assertEquals(mapOf("id" to 2, "a" to 9), panels[1])
    }
}
