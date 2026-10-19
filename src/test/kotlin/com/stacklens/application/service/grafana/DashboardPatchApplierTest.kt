package com.stacklens.application.service.grafana

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardPatchApplierTest {
    private val applier = DashboardPatchApplier(JacksonJsonCodecAdapter(ObjectMapper().registerModule(KotlinModule.Builder().build())))

    private val flatOriginal = """{"panels":[{"id":1,"title":"A"},{"id":2,"title":"B"}],"title":"Dash"}"""
    private val wrappedOriginal = """{"dashboard":{"panels":[{"id":1,"title":"A"}]},"meta":{"slug":"x"}}"""

    @Test
    fun `flat structure - dashboardPatch applies non-panels fields`() {
        val out = applier.applyPatch(flatOriginal, """{"dashboardPatch":{"title":"New","version":5}}""")
        assertTrue(out.contains("\"title\":\"New\""))
        assertTrue(out.contains("\"version\":5"))
    }

    @Test
    fun `flat structure - dashboardPatch ignores panels key`() {
        val out = applier.applyPatch(flatOriginal, """{"dashboardPatch":{"panels":[{"id":99}]}}""")
        assertTrue(out.contains("\"id\":1"))
        assertTrue(out.contains("\"id\":2"))
    }

    @Test
    fun `wrapped structure - patch applies to dashboard node`() {
        val out = applier.applyPatch(wrappedOriginal, """{"dashboardPatch":{"title":"Z"}}""")
        assertTrue(out.contains("\"title\":\"Z\""))
        assertTrue(out.contains("\"dashboard\""))
    }

    @Test
    fun `modifiedPanels replaces panel by numeric id`() {
        val out = applier.applyPatch(flatOriginal, """{"modifiedPanels":[{"id":1,"title":"A2"}]}""")
        assertTrue(out.contains("\"title\":\"A2\""))
        assertTrue(out.contains("\"title\":\"B\""))
    }

    @Test
    fun `modifiedPanels replaces panel by string id`() {
        val out = applier.applyPatch(flatOriginal, """{"modifiedPanels":[{"id":"2","title":"B2"}]}""")
        assertTrue(out.contains("\"title\":\"B2\""))
    }

    @Test
    fun `modifiedPanels with no matching id is skipped`() {
        val out = applier.applyPatch(flatOriginal, """{"modifiedPanels":[{"id":99,"title":"X"}]}""")
        assertTrue(out.contains("\"title\":\"A\""))
    }

    @Test
    fun `modifiedPanels entry without id is skipped`() {
        val out = applier.applyPatch(flatOriginal, """{"modifiedPanels":[{"title":"X"}]}""")
        assertTrue(out.contains("\"title\":\"A\""))
    }

    @Test
    fun `removePanelIds removes matching numeric ids`() {
        val out = applier.applyPatch(flatOriginal, """{"removePanelIds":[1]}""")
        assertTrue(!out.contains("\"title\":\"A\""))
        assertTrue(out.contains("\"title\":\"B\""))
    }

    @Test
    fun `removePanelIds empty list does nothing`() {
        val out = applier.applyPatch(flatOriginal, """{"removePanelIds":[]}""")
        assertTrue(out.contains("\"title\":\"A\""))
    }

    @Test
    fun `addPanels appends new panels to existing list`() {
        val out = applier.applyPatch(flatOriginal, """{"addPanels":[{"id":3,"title":"C"}]}""")
        assertTrue(out.contains("\"title\":\"C\""))
    }

    @Test
    fun `addPanels creates panels list when absent`() {
        val out = applier.applyPatch("""{"title":"Dash"}""", """{"addPanels":[{"id":3,"title":"C"}]}""")
        assertTrue(out.contains("\"title\":\"C\""))
    }

    @Test
    fun `patch without any operation returns original structure`() {
        val out = applier.applyPatch(flatOriginal, """{}""")
        assertTrue(out.contains("\"title\":\"A\""))
    }

    @Test
    fun `removePanelIds with string ids removes matching`() {
        val out = applier.applyPatch(flatOriginal, """{"removePanelIds":["2"]}""")
        assertTrue(!out.contains("\"title\":\"B\""))
        assertTrue(out.contains("\"title\":\"A\""))
    }

    @Test
    fun `wrapped structure converts immutable dashboard map to mutable`() {
        val out = applier.applyPatch(wrappedOriginal, """{"dashboardPatch":{"title":"Y"}}""")
        assertTrue(out.contains("\"title\":\"Y\""))
    }

    @Test
    fun `dashboardPatch with empty patch iterates nothing`() {
        val out = applier.applyPatch(flatOriginal, """{"dashboardPatch":{}}""")
        assertTrue(out.contains("\"title\":\"A\""))
    }

    @Test
    fun `dashboardPatch with multiple non-panel keys applies all`() {
        val out = applier.applyPatch(flatOriginal, """{"dashboardPatch":{"title":"X","version":7,"refresh":"30s"}}""")
        assertTrue(out.contains("\"title\":\"X\""))
        assertTrue(out.contains("\"version\":7"))
        assertTrue(out.contains("\"refresh\":\"30s\""))
    }

    @Test
    fun `modifiedPanels iterates panelsList indices even when first panel does not match`() {
        val out = applier.applyPatch(flatOriginal, """{"modifiedPanels":[{"id":1,"title":"A2"},{"id":99,"title":"X"}]}""")
        assertTrue(out.contains("\"title\":\"A2\""))
        assertTrue(out.contains("\"title\":\"B\""))
    }

    @Test
    fun `removePanelIds with mixed numeric and string entries coerces to set`() {
        val out = applier.applyPatch(flatOriginal, """{"removePanelIds":[1,"2",3]}""")
        assertTrue(!out.contains("\"title\":\"A\""))
        assertTrue(!out.contains("\"title\":\"B\""))
    }

    @Test
    fun `removePanelIds with entries that have null id are skipped`() {
        val original = """{"panels":[{"id":1,"title":"A"},{"title":"NoId"}]}"""
        val out = applier.applyPatch(original, """{"removePanelIds":[1]}""")
        assertTrue(!out.contains("\"title\":\"A\""))
        assertTrue(out.contains("\"title\":\"NoId\""))
    }

    @Test
    fun `idAsInt handles non-numeric string and non-string non-number`() {
        val out = applier.applyPatch(flatOriginal, """{"modifiedPanels":[{"id":[1,2,3],"title":"X"}]}""")
        assertTrue(out.contains("\"title\":\"A\""))
    }

    @Test
    fun `idAsInt returns null for non-convertible string`() {
        val out = applier.applyPatch(flatOriginal, """{"removePanelIds":["abc"]}""")
        assertTrue(out.contains("\"title\":\"A\""))
        assertTrue(out.contains("\"title\":\"B\""))
    }

    @Test
    fun `wrapped structure - modifiedPanels replaces panel inside dashboard wrapper`() {
        val out = applier.applyPatch(wrappedOriginal, """{"modifiedPanels":[{"id":1,"title":"Modified"}]}""")
        assertTrue(out.contains("\"title\":\"Modified\""))
    }

    @Test
    fun `wrapped structure - removePanelIds removes panel from dashboard wrapper`() {
        val out = applier.applyPatch(wrappedOriginal, """{"removePanelIds":[1]}""")
        assertTrue(!out.contains("\"title\":\"A\""))
    }

    @Test
    fun `wrapped structure - addPanels creates panels inside dashboard wrapper when absent`() {
        val wrappedNoPanels = """{"dashboard":{"title":"X"},"meta":{}}"""
        val out = applier.applyPatch(wrappedNoPanels, """{"addPanels":[{"id":1,"title":"New"}]}""")
        assertTrue(out.contains("\"title\":\"New\""))
    }

    @Test
    fun `wrapped structure - addPanels appends to existing panels`() {
        val out = applier.applyPatch(wrappedOriginal, """{"addPanels":[{"id":2,"title":"B"}]}""")
        assertTrue(out.contains("\"title\":\"B\""))
    }

    @Test
    fun `modifiedPanels with multiple non-matching ids leaves all panels untouched`() {
        val out = applier.applyPatch(flatOriginal, """{"modifiedPanels":[{"id":99,"title":"X"},{"id":100,"title":"Y"}]}""")
        assertTrue(out.contains("\"title\":\"A\""))
        assertTrue(out.contains("\"title\":\"B\""))
    }

    @Test
    fun `removePanelIds with all-null entries removes nothing`() {
        val out = applier.applyPatch(flatOriginal, """{"removePanelIds":[null,null]}""")
        assertTrue(out.contains("\"title\":\"A\""))
        assertTrue(out.contains("\"title\":\"B\""))
    }

    @Test
    fun `patch with only dashboardPatch ignores panels even when both are present in flat structure`() {
        val out = applier.applyPatch(flatOriginal, """{"dashboardPatch":{"title":"Z","panels":[{"id":99,"title":"BAD"}]}}""")
        assertTrue(out.contains("\"title\":\"Z\""))
        assertTrue(out.contains("\"id\":1"))
        assertTrue(out.contains("\"id\":2"))
        assertTrue(!out.contains("\"id\":99"))
    }

    @Test
    fun `addPanels with empty list does not create a panels key`() {
        val out = applier.applyPatch("""{"title":"X"}""", """{"addPanels":[]}""")
        assertTrue(!out.contains("\"panels\""))
    }

    @Test
    fun `removePanelIds with empty list does not create an iterator call`() {
        val out = applier.applyPatch(flatOriginal, """{"removePanelIds":[]}""")
        assertTrue(out.contains("\"title\":\"A\""))
        assertTrue(out.contains("\"title\":\"B\""))
    }
}
