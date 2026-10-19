package com.stacklens.application.service.grafana

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.application.port.out.JsonCodecPort
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DashboardPatchApplierBranchTest {
    private val mapper = jacksonObjectMapper()
    private val jsonCodec = mock<JsonCodecPort>()
    private val applier = DashboardPatchApplier(jsonCodec)

    @BeforeEach
    fun setUp() {
        @Suppress("UNCHECKED_CAST")
        whenever(jsonCodec.decodeMap(org.mockito.kotlin.any<String>())).thenAnswer { inv ->
            mapper.readValue(inv.arguments[0] as String, Map::class.java) as Map<String, Any?>
        }
        whenever(jsonCodec.encode(org.mockito.kotlin.any<Any>())).thenAnswer { inv ->
            mapper.writeValueAsString(inv.arguments[0])
        }
    }

    @Test
    fun `applyPatch handles flat structure`() {
        val original = """{"panels":[{"id":1,"title":"A"}]}"""
        val patch = """{"modifiedPanels":[{"id":1,"title":"B"}]}"""
        val result = applier.applyPatch(original, patch)
        assertTrue(result.contains("B"))
    }

    @Test
    fun `applyPatch handles dashboard-wrapped structure`() {
        val original = """{"dashboard":{"panels":[{"id":1,"title":"A"}]},"meta":{}}"""
        val patch = """{"modifiedPanels":[{"id":1,"title":"B"}]}"""
        val result = applier.applyPatch(original, patch)
        assertTrue(result.contains("B"))
    }

    @Test
    fun `applyPatch removes panels by id`() {
        val original = """{"panels":[{"id":1,"title":"A"},{"id":2,"title":"B"}]}"""
        val patch = """{"removePanelIds":[1]}"""
        val result = applier.applyPatch(original, patch)
        assertTrue(!result.contains("\"id\":1"))
        assertTrue(result.contains("\"id\":2"))
    }

    @Test
    fun `applyPatch adds new panels`() {
        val original = """{"panels":[]}"""
        val patch = """{"addPanels":[{"id":99,"title":"New"}]}"""
        val result = applier.applyPatch(original, patch)
        assertTrue(result.contains("\"id\":99"))
    }

    @Test
    fun `applyPatch applies dashboard-level fields`() {
        val original = """{"title":"Old","panels":[]}"""
        val patch = """{"dashboardPatch":{"title":"New","refresh":"5s"}}"""
        val result = applier.applyPatch(original, patch)
        assertTrue(result.contains("New"))
        assertTrue(result.contains("5s"))
    }
}
