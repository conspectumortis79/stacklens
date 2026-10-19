package com.stacklens.application.service.grafana

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardPatchApplierMoreTest {
    private val applier = DashboardPatchApplier(JacksonJsonCodecAdapter(ObjectMapper().registerModule(KotlinModule.Builder().build())))

    private val flatOriginal = """{"panels":[{"id":1,"title":"A"},{"id":2,"title":"B"}],"title":"Dash"}"""
    private val wrappedOriginal = """{"dashboard":{"panels":[{"id":1,"title":"A"}]},"meta":{"slug":"x"}}"""

    @Test
    fun `applyPatch handles wrapped structure with dashboardPatch and modifiedPanels`() {
        val out =
            applier.applyPatch(
                wrappedOriginal,
                """{"dashboardPatch":{"title":"Modified"},"modifiedPanels":[{"id":1,"title":"Mod"}]}""",
            )
        assertTrue(out.contains("Modified"))
        assertTrue(out.contains("Mod"))
    }

    @Test
    fun `applyPatch with both flat and wrapped panel structures`() {
        val out =
            applier.applyPatch(
                flatOriginal,
                """{"modifiedPanels":[{"id":1,"title":"X"}],"addPanels":[{"id":3,"title":"Z"}]}""",
            )
        assertTrue(out.contains("X"))
        assertTrue(out.contains("B"))
        assertTrue(out.contains("Z"))
    }

    @Test
    fun `applyPatch removes panels that don't exist in original`() {
        val out = applier.applyPatch(flatOriginal, """{"removePanelIds":[99]}""")
        assertTrue(out.contains("A"))
        assertTrue(out.contains("B"))
    }

    @Test
    fun `applyPatch with dashboardPatch that has only panels key does not modify panels`() {
        val out =
            applier.applyPatch(
                flatOriginal,
                """{"dashboardPatch":{"panels":[{"id":99,"title":"ShouldNotApply"}]}}""",
            )
        assertFalse(out.contains("ShouldNotApply"))
        assertTrue(out.contains("A"))
    }

    @Test
    fun `applyPatch replaces all panels when modifiedPanels has all ids`() {
        val out =
            applier.applyPatch(
                flatOriginal,
                """{"modifiedPanels":[{"id":1,"title":"X"},{"id":2,"title":"Y"}]}""",
            )
        assertTrue(out.contains("X"))
        assertTrue(out.contains("Y"))
        assertFalse(out.contains("\"title\":\"A\""))
    }

    @Test
    fun `applyPatch with empty addPanels does nothing`() {
        val out = applier.applyPatch(flatOriginal, """{"addPanels":[]}""")
        assertTrue(out.contains("A"))
        assertTrue(out.contains("B"))
    }

    @Test
    fun `applyPatch handles addPanels with multiple panels`() {
        val out =
            applier.applyPatch(
                flatOriginal,
                """{"addPanels":[{"id":3,"title":"C"},{"id":4,"title":"D"}]}""",
            )
        assertTrue(out.contains("C"))
        assertTrue(out.contains("D"))
    }
}
