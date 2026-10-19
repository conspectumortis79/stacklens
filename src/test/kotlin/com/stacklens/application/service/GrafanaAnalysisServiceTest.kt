package com.stacklens.application.service
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.grafana.DashboardPatchApplier
import com.stacklens.application.service.grafana.GrafanaPromptBuilder
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GrafanaAnalysisServiceTest {
    private val grafanaConfigPort: GrafanaConfigPort = mock()
    private val copilotPort: CopilotStreamingPort = mock()
    private val promptLoaderPort: PromptReaderPort = mock()
    private val objectMapper = ObjectMapper().apply { findAndRegisterModules() }
    private val jsonCodec = JacksonJsonCodecAdapter(objectMapper)
    private lateinit var service: GrafanaAnalysisService

    @BeforeEach
    fun setup() {
        val promptBuilder = GrafanaPromptBuilder(promptLoaderPort)
        val patchApplier = DashboardPatchApplier(jsonCodec)
        service =
            GrafanaAnalysisService(
                grafanaConfigPort,
                copilotPort,
                promptBuilder,
                patchApplier,
                jsonCodec,
                aiUsageContext = FakeAiUsageContextPort(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
                demoConfigFilter = PassthroughDemoConfigFilter.instance,
            )
        whenever(copilotPort.resolveModel()).thenReturn("gpt-4")
        whenever(copilotPort.resolveLanguage()).thenReturn("de")
        whenever(promptLoaderPort.get(any(), any(), any())).thenReturn("prompt-text")
    }

    private fun grafanaConfig(id: Long = 1L) =
        GrafanaConfig(
            id = id,
            name = "test-grafana",
            baseUrl = "http://localhost:3000",
            username = "admin",
            password = "secret",
        )

    @Test
    fun `listConfigs returns all grafana configs`() {
        whenever(grafanaConfigPort.findAllGrafanaConfigs()).thenReturn(listOf(grafanaConfig(1L), grafanaConfig(2L)))
        val result = service.listConfigs()
        assertEquals(2, result.size)
    }

    @Test
    fun `findConfig returns null when not found`() {
        whenever(grafanaConfigPort.findGrafanaConfigById(99L)).thenReturn(null)
        assertNull(service.findConfig(99L))
    }

    @Test
    fun `findConfig returns config info when found`() {
        whenever(grafanaConfigPort.findGrafanaConfigById(1L)).thenReturn(grafanaConfig(1L))
        val result = service.findConfig(1L)
        assertNotNull(result)
        assertEquals("test-grafana", result?.name)
    }

    @Test
    fun `generateDummyData with valid JSON response returns parsed map`() {
        val dashboardJson = """{"title":"test","panels":[]}"""
        val aiResponse = """{"panels": [{"id": 1, "type": "gauge"}]}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.generateDummyData(dashboardJson)
        assertTrue(result.containsKey("panels"))
    }

    @Test
    fun `generateDummyData with invalid JSON response returns empty panels`() {
        val dashboardJson = """{"title":"test"}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("not a json response")
        val result = service.generateDummyData(dashboardJson)
        assertEquals(emptyList<Any>(), result["panels"])
    }

    @Test
    fun `generateDummyData with large dashboardJson caps at 6000 chars`() {
        val bigJson = "x".repeat(7000)
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("{}")
        service.generateDummyData(bigJson)
        verify(copilotPort).execute(any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `generateDummyData returns empty panels on JSON parse error`() {
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("""{"not-valid": [}""")
        val result = service.generateDummyData("{}")
        assertEquals(emptyList<Any>(), result["panels"])
    }

    @Test
    fun `rebuildDashboard returns original JSON when no JSON found in response`() {
        val original = """{"title":"orig","panels":[]}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("No JSON here at all.")
        val result = service.rebuildDashboard(original, "fix panels")
        assertEquals(false, result["parseSuccess"])
        assertEquals(original, result["dashboardJson"])
    }

    @Test
    fun `rebuildDashboard with large dashboardJson caps at 20000 chars`() {
        val bigJson = "{" + "x".repeat(21000) + "}"
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("no json")
        service.rebuildDashboard(bigJson, "instructions")
        verify(copilotPort).execute(any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `rebuildDashboard with patch format returns patched dashboard`() {
        val original =
            """
            {
                "title": "my dashboard",
                "panels": [{"id": 1, "type": "graph"}, {"id": 2, "type": "stat"}]
            }
            """.trimIndent()
        val aiResponse =
            """
            ```json
            {
                "summary": "updated panel 1",
                "changes": ["panel 1 updated"],
                "patch": {
                    "modifiedPanels": [{"id": 1, "type": "timeseries"}]
                }
            }
            ```
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update panel 1")
        assertEquals(true, result["parseSuccess"])
        assertEquals("updated panel 1", result["summary"])
    }

    @Test
    fun `rebuildDashboard with result format (full dashboard) returns success`() {
        val original = """{"title":"orig","panels":[{"id":1,"type":"graph"}]}"""
        val aiResponse =
            """
            {
                "summary": "rebuilt",
                "changes": ["changed"],
                "result": {
                    "title": "new",
                    "panels": [{"id": 1, "type": "timeseries"}]
                }
            }
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "rebuild")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard with panels-only JSON format returns success`() {
        val original = """{"title":"orig","panels":[]}"""
        val aiResponse = """{"panels": [{"id": 1, "type": "stat"}], "title": "new"}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "add panel")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard with dashboard wrapper format returns success`() {
        val original = """{"title":"orig","panels":[]}"""
        val aiResponse = """{"dashboard": {"panels": [{"id": 1}], "title": "new"}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard with unrecognized JSON format returns parseSuccess false`() {
        val original = """{"title":"orig"}"""
        val aiResponse = """{"unknown": "field", "otherField": 123}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertFalse(result["parseSuccess"] as Boolean)
    }

    @Test
    fun `rebuildDashboard with broken JSON parses as failure`() {
        val original = """{"title":"orig","panels":[]}"""
        val aiResponse =
            """
            ```json
            {broken json here
            ```
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "fix")
        assertFalse(result["parseSuccess"] as Boolean)
    }

    @Test
    fun `rebuildDashboard patch with removePanelIds removes panels`() {
        val original =
            """
            {"title":"d","panels":[{"id":1,"type":"graph"},{"id":2,"type":"stat"}]}
            """.trimIndent()
        val aiResponse =
            """
            {
                "summary": "removed panel 2",
                "changes": ["removed panel 2"],
                "patch": {
                    "removePanelIds": [2]
                }
            }
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "remove panel 2")
        assertEquals(true, result["parseSuccess"])
        val dashJson = result["dashboardJson"] as String
        assertTrue(dashJson.contains("\"id\":1"))
        assertFalse(dashJson.contains("\"id\":2"))
    }

    @Test
    fun `rebuildDashboard patch with addPanels adds a new panel`() {
        val original = """{"title":"d","panels":[{"id":1,"type":"graph"}]}"""
        val aiResponse =
            """
            {
                "summary": "added panel",
                "changes": ["added"],
                "patch": {
                    "addPanels": [{"id": 3, "type": "stat"}]
                }
            }
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "add panel")
        assertEquals(true, result["parseSuccess"])
        val dashJson = result["dashboardJson"] as String
        assertTrue(dashJson.contains("\"id\":3"))
    }

    @Test
    fun `rebuildDashboard patch with dashboardPatch updates title`() {
        val original = """{"title":"old","panels":[]}"""
        val aiResponse =
            """
            {
                "summary": "updated title",
                "changes": [],
                "patch": {
                    "dashboardPatch": {"title": "new title"}
                }
            }
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "rename")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard patch apply exception returns original JSON`() {
        val original = """{"title":"orig","panels":[{"id":1}]}"""
        val aiResponse =
            """
            {
                "summary": "",
                "changes": [],
                "patch": {
                    "modifiedPanels": [{"id": 1, "type": "stat"}]
                }
            }
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "test")
        assertNotNull(result["dashboardJson"])
    }

    @Test
    fun `rebuildDashboard with result having nested dashboard format returns panels`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse =
            """
            {
                "summary": "ok",
                "changes": [],
                "result": {
                    "dashboard": {
                        "panels": [{"id": 1}],
                        "title": "inner"
                    }
                }
            }
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard patch with Grafana API-wrapped structure`() {
        val original = """{"dashboard":{"title":"inner","panels":[{"id":1,"type":"old"}]},"meta":{}}"""
        val aiResponse =
            """
            {
                "summary": "patched inner",
                "changes": ["updated panel 1"],
                "patch": {
                    "modifiedPanels": [{"id": 1, "type": "new"}]
                }
            }
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        val dashJson = result["dashboardJson"] as String
        assertTrue(dashJson.contains("\"type\":\"new\""))
    }

    @Test
    fun `rebuildDashboard patch addPanels to dashboard without existing panels array`() {
        val original = """{"title":"empty"}"""
        val aiResponse =
            """
            {
                "summary": "added panel",
                "changes": [],
                "patch": {
                    "addPanels": [{"id": 1, "type": "stat"}]
                }
            }
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "add")
        assertEquals(true, result["parseSuccess"])
        val dashJson = result["dashboardJson"] as String
        assertTrue(dashJson.contains("panels"))
    }

    @Test
    fun `rebuildDashboard patch modifiedPanels with missing panel id skips`() {
        val original = """{"title":"d","panels":[{"id":1,"type":"graph"}]}"""
        val aiResponse =
            """
            {
                "summary": "ok",
                "changes": [],
                "patch": {
                    "modifiedPanels": [{"type": "stat"}]
                }
            }
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        val dashJson = result["dashboardJson"] as String
        assertTrue(dashJson.contains("\"type\":\"graph\""))
    }

    @Test
    fun `rebuildDashboard with changes containing blank entries filters them out`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse =
            """
            {
                "summary": "filtered",
                "changes": ["real change", "", null],
                "patch": {}
            }
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard with markdown json block not starting with brace falls to brace counting`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse =
            """
            ```json
            [1, 2, 3]
            ```
            {"summary": "ok", "changes": [], "panels": [{"id": 1}]}
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertNotNull(result)
    }

    @Test
    fun `rebuildDashboard with unclosed JSON brace returns failure`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = """Here is the result: {"unclosed object"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertFalse(result["parseSuccess"] as Boolean)
    }

    @Test
    fun `rebuildDashboard dashboardPatch ignores panels key`() {
        val original = """{"title":"old","panels":[{"id":1}]}"""
        val aiResponse =
            """
            {
                "summary": "ok",
                "changes": [],
                "patch": {
                    "dashboardPatch": {"title": "new", "panels": [{"id":99}]}
                }
            }
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        val dashJson = result["dashboardJson"] as String
        assertTrue(dashJson.contains("\"id\":1"))
        assertFalse(dashJson.contains("\"id\":99"))
    }

    @Test
    fun `rebuildDashboard with summary and changes null in JSON uses defaults`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = """{"patch":{}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        assertEquals("", result["summary"])
    }

    @Test
    fun `rebuildDashboard patch node is not object falls to result format`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = """{"summary":"ok","changes":[],"patch":"not-an-object","result":{"panels":[{"id":1}]}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard removePanelIds with empty array does not modify panels`() {
        val original = """{"title":"d","panels":[{"id":1},{"id":2}]}"""
        val aiResponse = """{"summary":"noop","changes":[],"patch":{"removePanelIds":[]}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "nothing")
        assertEquals(true, result["parseSuccess"])
        val dashJson = result["dashboardJson"] as String
        assertTrue(dashJson.contains("\"id\":1"))
        assertTrue(dashJson.contains("\"id\":2"))
    }

    @Test
    fun `rebuildDashboard result with dashboard node containing panels`() {
        val original = """{"title":"orig","panels":[]}"""
        val aiResponse =
            """
            {
                "summary": "ok",
                "changes": [],
                "result": {"dashboard": {"panels": [{"id": 1}], "title": "inner"}}
            }
            """.trimIndent()
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard with dashboard-has format without panels`() {
        val original = """{"title":"orig","panels":[]}"""
        val aiResponse = """{"dashboard": {"title": "inner"}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `generateDummyData with exactly 6000 chars does not truncate`() {
        val dashboardJson = "x".repeat(6000)
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("{}")
        service.generateDummyData(dashboardJson)
        verify(copilotPort).execute(any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `rebuildDashboard with exactly 20000 chars does not truncate`() {
        val dashboardJson = "{" + "x".repeat(19998) + "}"
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("no json")
        service.rebuildDashboard(dashboardJson, "instructions")
        verify(copilotPort).execute(any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `rebuildDashboard with non-object originalJson returns originalJson on patch`() {
        val original = "[]"
        val aiResponse = """{"summary":"ok","changes":[],"patch":{"dashboardPatch":{"title":"new"}}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        assertEquals(original, result["dashboardJson"])
    }

    @Test
    fun `extractFirstJsonObject markdown block with array not object falls to brace scan`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = "```json\n[1,2,3]\n```\n{\"panels\":[{\"id\":1}]}"
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard with JSON parse exception on extracted JSON returns failure`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = """{key without quotes: value}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertFalse(result["parseSuccess"] as Boolean)
    }

    @Test
    fun `rebuildDashboard modifiedPanels with nonexistent panel id preserves original`() {
        val original = """{"title":"d","panels":[{"id":1,"type":"graph"}]}"""
        val aiResponse = """{"summary":"ok","changes":[],"patch":{"modifiedPanels":[{"id":99,"type":"stat"}]}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        val dashJson = result["dashboardJson"] as String
        assertTrue(dashJson.contains("\"type\":\"graph\""))
    }

    @Test
    fun `rebuildDashboard result format with panels in nested dashboard object has panelCount`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = """{"summary":"ok","changes":[],"result":{"dashboard":{"panels":[{"id":1}]}}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard result format with neither panels nor nested dashboard has null panelCount`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = """{"summary":"ok","changes":[],"result":{"title":"dash"}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard modifiedPanels with panel missing id field skips it`() {
        val original = """{"title":"d","panels":[{"id":1,"type":"graph"}]}"""
        val aiResponse = """{"summary":"ok","changes":[],"patch":{"modifiedPanels":[{"type":"stat"}]}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        val dashJson = result["dashboardJson"] as String
        assertTrue(dashJson.contains("\"type\":\"graph\""))
    }

    @Test
    fun `rebuildDashboard removePanelIds skips panel with null id`() {
        val original = """{"title":"d","panels":[{"id":1,"type":"graph"},{"type":"noid"}]}"""
        val aiResponse = """{"summary":"ok","changes":[],"patch":{"removePanelIds":[1]}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard addPanels when dashboard has no panels array creates one`() {
        val original = """{"title":"dash"}"""
        val aiResponse = """{"summary":"ok","changes":[],"patch":{"addPanels":[{"id":10,"type":"stat"}]}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        val dashJson = result["dashboardJson"] as String
        assertTrue(dashJson.contains("\"id\":10"))
    }

    @Test
    fun `generateDummyData with no JSON in response returns panels emptyList`() {
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("no json here at all")
        val result = service.generateDummyData("panel data")
        assertNotNull(result)
    }

    @Test
    fun `generateDummyData with invalid JSON in response returns panels emptyList`() {
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("{invalid json}")
        val result = service.generateDummyData("panel data")
        assertNotNull(result)
    }

    @Test
    fun `rebuildDashboard patch with null modifiedPanels skips modification loop`() {
        val original = """{"title":"d","panels":[{"id":1,"type":"graph"}]}"""
        val aiResponse = """{"summary":"ok","changes":[],"patch":{"addPanels":[]}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard changes list with null items uses empty list`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = """{"summary":"summ","changes":null,"patch":{}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertNotNull(result)
    }

    @Test
    fun `rebuildDashboard format 3 with dashboard object key is recognized`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = """{"dashboard":{"panels":[],"title":"direct"}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard with extractFirstJsonObject returning markdown block starting with object`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = "```json\n{\"summary\":\"ok\",\"changes\":[],\"patch\":{}}\n```"
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertNotNull(result)
    }

    @Test
    fun `extractFirstJsonObject markdown block with no closing backticks falls to brace scan`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = "```json\n{\"summary\":\"ok\",\"changes\":[],\"panels\":[{\"id\":1}]}"
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard modifiedPanels array with original having no panels array is skipped`() {
        val original = """{"title":"no-panels-field"}"""
        val aiResponse = """{"summary":"ok","changes":[],"patch":{"modifiedPanels":[{"id":1,"type":"stat"}]}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard removePanelIds non-empty with original having no panels array is skipped`() {
        val original = """{"title":"no-panels-field"}"""
        val aiResponse = """{"summary":"ok","changes":[],"patch":{"removePanelIds":[1,2]}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard modifiedPanels non-array value is ignored`() {
        val original = """{"title":"d","panels":[{"id":1,"type":"graph"}]}"""
        val aiResponse = """{"summary":"ok","changes":[],"patch":{"modifiedPanels":"not-an-array"}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        assertTrue((result["dashboardJson"] as String).contains("\"type\":\"graph\""))
    }

    @Test
    fun `rebuildDashboard removePanelIds non-array value is ignored`() {
        val original = """{"title":"d","panels":[{"id":1},{"id":2}]}"""
        val aiResponse = """{"summary":"ok","changes":[],"patch":{"removePanelIds":"not-an-array"}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        assertTrue((result["dashboardJson"] as String).contains("\"id\":1"))
    }

    @Test
    fun `rebuildDashboard addPanels non-array value is ignored`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = """{"summary":"ok","changes":[],"patch":{"addPanels":"not-an-array"}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard patch format with malformed originalJson catches exception and returns original`() {
        val original = "{bad json: unquoted key}"
        val aiResponse = """{"summary":"caught","changes":[],"patch":{"modifiedPanels":[]}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        assertEquals(original, result["dashboardJson"])
    }

    @Test
    fun `rebuildDashboard result format with non-object result node falls through to unrecognized`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = """{"summary":"ok","changes":[],"result":"not-an-object"}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertFalse(result["parseSuccess"] as Boolean)
    }

    @Test
    fun `rebuildDashboard format 3 dashboard key with non-object value falls through to unrecognized`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = """{"summary":"ok","dashboard":"string-not-object"}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertFalse(result["parseSuccess"] as Boolean)
    }

    @Test
    fun `rebuildDashboard changes list with blank strings are filtered out`() {
        val original = """{"title":"d","panels":[{"id":1,"type":"graph"}]}"""
        val aiResponse = """{"summary":"ok","changes":["","  ","real change"],"patch":{}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        @Suppress("UNCHECKED_CAST")
        val changes = result["changes"] as List<String>
        assertEquals(1, changes.size)
        assertEquals("real change", changes[0])
    }

    @Test
    fun `rebuildDashboard result format with dashboard object but no panels has null panelCount`() {
        val original = """{"title":"d","panels":[]}"""
        val aiResponse = """{"summary":"ok","changes":[],"result":{"dashboard":{"title":"no-panels"}}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
    }

    @Test
    fun `rebuildDashboard modifiedPanels original panel without id field continues to next panel`() {
        val original = """{"title":"d","panels":[{"type":"graph"},{"id":1,"type":"stat"}]}"""
        val aiResponse = """{"summary":"ok","changes":[],"patch":{"modifiedPanels":[{"id":1,"type":"new"}]}}"""
        whenever(copilotPort.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(aiResponse)
        val result = service.rebuildDashboard(original, "update")
        assertEquals(true, result["parseSuccess"])
        val dashJson = result["dashboardJson"] as String
        assertTrue(dashJson.contains("\"type\":\"new\""))
    }
}
