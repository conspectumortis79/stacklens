package com.stacklens.application.service.grafana

import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardPatchShapeCoverageTest {
    private val applier = DashboardPatchApplier(TestJsonCodec.create())

    @Test
    fun `a patch of a document without a dashboard node edits the root`() {
        val result = applier.applyPatch("""{"title":"root"}""", """{"dashboardPatch":{"title":"deep"}}""")

        assertEquals("deep", TestJsonCodec.create().decodeMap(result)["title"])
    }

    @Test
    fun `panels without usable ids are left alone`() {
        val original =
            """{"dashboard":{"panels":["not-a-map",{"title":"no id"},{"id":"abc"},{"id":2}]}}"""
        val patch =
            """{"modifiedPanels":[{"id":2,"title":"replaced"},{"title":"no id"},{"id":"abc"}],""" +
                """"removePanelIds":["junk",2],"addPanels":[{"id":9}]}"""

        val result = applier.applyPatch(original, patch)
        val decoded = TestJsonCodec.create().decodeMap(result)

        @Suppress("UNCHECKED_CAST")
        val panels = ((decoded["dashboard"] as Map<String, Any?>)["panels"]) as List<Any?>
        val ids = panels.filterIsInstance<Map<String, Any?>>().map { it["id"] }

        assertEquals(listOf(null, "abc", 9), ids)
        assertFalse(result.contains("replaced"), result)
    }

    @Test
    fun `a removed panel disappears and a kept panel stays`() {
        val original = """{"dashboard":{"panels":[{"id":1},{"id":2}]}}"""

        val result = applier.applyPatch(original, """{"removePanelIds":[1]}""")
        val decoded = TestJsonCodec.create().decodeMap(result)

        @Suppress("UNCHECKED_CAST")
        val panels = (decoded["dashboard"] as Map<String, Any?>)["panels"] as List<Map<String, Any?>>

        assertEquals(1, panels.size)
        assertEquals(2, panels.single()["id"])
        assertFalse(result.contains("\"id\":1"))
        assertTrue(result.contains("\"id\":2"))
    }
}
