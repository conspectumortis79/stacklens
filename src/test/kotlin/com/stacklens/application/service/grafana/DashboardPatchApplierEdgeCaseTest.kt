package com.stacklens.application.service.grafana

import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardPatchApplierEdgeCaseTest {
    private val codec = TestJsonCodec.create()
    private val applier = DashboardPatchApplier(codec)

    private fun panelsOf(patched: String): List<*> {
        val dashboard = codec.decodeMap(patched)["dashboard"]
        @Suppress("UNCHECKED_CAST")
        return (dashboard as Map<String, Any?>).getValue("panels") as List<*>
    }

    @Test
    fun `patch entries that are no objects or carry no usable id are skipped`() {
        val original =
            """
            {"dashboard":{"title":"Ops","panels":["plain",{"title":"no id"},{"id":1,"title":"one"},{"id":2,"title":"two"}]}}
            """.trimIndent()
        val patch =
            """
            {"modifiedPanels":["junk",{"id":"not-a-number"},{"noId":true},{"id":2,"title":"replaced"}],
             "removePanelIds":[null,77]}
            """.trimIndent()

        val result = applier.applyPatch(original, patch)
        val panels = panelsOf(result)

        assertEquals(4, panels.size, result)
        assertEquals("plain", panels[0])
        assertEquals(1, (panels[2] as Map<*, *>)["id"])
        assertEquals("replaced", (panels[3] as Map<*, *>)["title"])
    }

    @Test
    fun `a panel listed for removal is dropped even when other entries are not panels`() {
        val original = """{"dashboard":{"panels":["scalar",{"id":5,"title":"keep"},{"id":6,"title":"drop"}]}}"""
        val patch = """{"removePanelIds":["not-a-number",6,null]}"""

        val panels = panelsOf(applier.applyPatch(original, patch))

        assertEquals(2, panels.size)
        assertEquals("scalar", panels[0])
        assertEquals(5, (panels[1] as Map<*, *>)["id"])
    }

    @Test
    fun `an empty removal list leaves every panel in place`() {
        val original = """{"dashboard":{"panels":[{"id":5}]}}"""

        val panels = panelsOf(applier.applyPatch(original, """{"removePanelIds":[]}"""))

        assertEquals(1, panels.size)
    }

    @Test
    fun `panels replaced by id keep their position in the list`() {
        val original = """{"dashboard":{"panels":[{"id":1},{"id":2},{"id":3}]}}"""
        val patch = """{"modifiedPanels":[{"id":3,"title":"last"},{"id":1,"title":"first"}]}"""

        val panels = panelsOf(applier.applyPatch(original, patch))

        assertEquals(listOf("first", null, "last"), panels.map { (it as Map<*, *>)["title"] })
    }

    @Test
    fun `a numeric string id matches the panel it names`() {
        val original = """{"dashboard":{"panels":[{"id":8,"title":"old"}]}}"""

        val panels = panelsOf(applier.applyPatch(original, """{"modifiedPanels":[{"id":"8","title":"new"}]}"""))

        assertEquals("new", (panels.single() as Map<*, *>)["title"])
    }

    @Test
    fun `a nested dashboard object is replaced by its mutable copy`() {
        val original = """{"dashboard":{"title":"Ops","panels":[{"id":1,"title":"one"}]}}"""
        val patch = """{"dashboardPatch":{"title":"Renamed"},"modifiedPanels":[{"id":1,"title":"changed"}]}"""

        val result = applier.applyPatch(original, patch)

        assertTrue(result.contains("Renamed"), result)
        assertTrue(result.contains("changed"), result)
        assertFalse(result.contains("\"one\""), result)
    }

    @Test
    fun `a scalar dashboard value falls back to patching the root document`() {
        val original = """{"dashboard":"not-an-object","title":"Ops"}"""

        val result = applier.applyPatch(original, """{"dashboardPatch":{"title":"Patched"}}""")

        assertTrue(result.contains("Patched"), result)
    }

    @Test
    fun `a scalar panels value is ignored by the panel operations`() {
        val original = """{"dashboard":{"panels":"not-a-list"}}"""

        val result =
            applier.applyPatch(
                original,
                """{"modifiedPanels":[{"id":1,"title":"x"}],"removePanelIds":[1]}""",
            )

        assertEquals(original, result)
    }
}
