package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.inbound.rest.config.McpServerConfigApiController
import com.stacklens.adapter.inbound.rest.grafana.GrafanaFileBrowserController
import com.stacklens.application.port.inbound.mcp.McpProcessStatusView
import com.stacklens.application.port.inbound.mcp.McpServerManagementUseCase
import com.stacklens.application.port.inbound.platform.BrowserOpenResult
import com.stacklens.application.port.inbound.platform.ExternalBrowserUseCase
import com.stacklens.application.port.inbound.prompt.AgentPromptContent
import com.stacklens.application.port.inbound.prompt.AgentPromptUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AdapterConstructorAndDefaultArgumentTest {
    @Test
    fun `the agent controller can be built with an explicit i18n collaborator`() {
        val agents: AgentPromptUseCase = mock()
        whenever(agents.getAgent("nora")).thenReturn(AgentPromptContent("nora", "body", isCustom = false, defaultContent = "body"))

        val response =
            AgentFileApiController(
                agents,
                com.stacklens.application.service
                    .FakeI18n(),
            ).getAgent("nora")

        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `the desktop controller can be built with an explicit i18n collaborator`() {
        val browser: ExternalBrowserUseCase = mock()
        whenever(browser.openUrl(any())).thenReturn(BrowserOpenResult(opened = false, errorMessage = "no host browser"))

        val response =
            DesktopIntegrationController(
                browser,
                com.stacklens.application.service
                    .FakeI18n(),
            ).openUrl(mapOf("url" to "https://example.com"))

        assertEquals(500, response.statusCode.value())
        assertEquals("no host browser", response.body?.get("error"))
    }

    @Test
    fun `the mcp controller can be built with an explicit i18n collaborator`() {
        val management: McpServerManagementUseCase = mock()
        whenever(management.restartServer()).thenReturn(null)

        val response =
            McpServerConfigApiController(
                management,
                management,
                com.stacklens.application.service
                    .FakeI18n(),
            ).processRestart()

        assertEquals(400, response.statusCode.value())
        assertEquals("mcp.be_no_config", response.body?.get("error"))
    }

    @Test
    fun `the mcp controller answers a running process without timestamps`() {
        val management: McpServerManagementUseCase = mock()
        whenever(management.stopServer()).thenReturn(
            McpProcessStatusView(
                running = false,
                pid = null,
                startedAt = null,
                stoppedAt = null,
                exitCode = null,
                message = "idle",
                logs = emptyList(),
            ),
        )

        val body =
            McpServerConfigApiController(
                management,
                management,
                com.stacklens.application.service
                    .FakeI18n(),
            ).processStop()

        assertEquals("idle", body["message"])
        assertEquals(null, body["startedAt"])
    }

    @Test
    fun `the dashboard json request exposes its default payload`() {
        val request = GrafanaFileBrowserController.SaveDashboardJsonRequest()

        assertEquals("", request.content)
        assertEquals("", request.path)
        assertEquals("", request.filename)
    }
}
