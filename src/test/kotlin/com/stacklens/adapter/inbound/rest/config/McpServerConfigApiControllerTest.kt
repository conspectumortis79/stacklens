package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.McpServerConfigDto
import com.stacklens.application.port.inbound.mcp.McpConnectionTestView
import com.stacklens.application.port.inbound.mcp.McpProcessStatusView
import com.stacklens.application.port.inbound.mcp.McpSecretPresence
import com.stacklens.application.port.inbound.mcp.McpServerConfigView
import com.stacklens.application.port.inbound.mcp.McpServerManagementUseCase
import com.stacklens.application.port.inbound.mcp.McpToolView
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class McpServerConfigApiControllerTest {
    private val management: McpServerManagementUseCase = mock()
    private val i18n: I18nUseCase = mock()
    private val controller = McpServerConfigApiController(management, management, i18n)

    private val noSecrets =
        McpSecretPresence(
            authTokenSet = false,
            jiraApiTokenSet = false,
            confluenceApiTokenSet = false,
            gitlabAccessTokenSet = false,
            githubTokenSet = false,
            grafanaApiKeySet = false,
        )

    private fun view(
        id: Long = 1L,
        name: String = "mcp",
        secrets: McpSecretPresence = noSecrets,
    ) = McpServerConfigView(
        id = id,
        name = name,
        baseUrl = "http://mcp",
        secrets = secrets,
        active = true,
        routeJira = true,
        routeConfluence = true,
        routeGitlab = true,
        routeGithub = true,
        routeGrafana = true,
        routeDatabase = true,
        jiraBaseUrl = "",
        jiraUsername = "",
        jiraCloudMode = true,
        confluenceBaseUrl = "",
        confluenceUsername = "",
        confluenceCloudMode = true,
        gitlabBaseUrl = "https://gitlab.com",
        githubApiBase = "https://api.github.com",
        grafanaBaseUrl = "",
        autoStartManaged = false,
    )

    private fun processView(message: String = "ok") =
        McpProcessStatusView(
            running = true,
            pid = 4242L,
            startedAt = Instant.ofEpochMilli(1_700_000_000_000L).toString(),
            stoppedAt = null,
            exitCode = null,
            message = message,
            logs = listOf("line"),
        )

    @Test
    fun `list maps every view to a dto and never exposes token values`() {
        whenever(management.listServerConfigs()).thenReturn(
            listOf(view(secrets = noSecrets.copy(authTokenSet = true, githubTokenSet = true))),
        )

        val dto = controller.list().single()

        assertEquals("mcp", dto.name)
        assertEquals("", dto.authToken)
        assertTrue(dto.authTokenSet)
        assertTrue(dto.githubTokenSet)
    }

    @Test
    fun `save forwards the dto fields to the use case and returns the mapped view`() {
        whenever(management.saveServerConfig(any(), any())).thenReturn(view(name = "saved"))

        val dto =
            controller.save(
                McpServerConfigDto(
                    id = 7L,
                    name = "typed",
                    baseUrl = "http://typed",
                    authToken = "secret",
                    grafanaApiKey = "gf-key",
                    autoStartManaged = true,
                ),
            )

        assertEquals("saved", dto.name)
        verify(management).saveServerConfig(
            eq(7L),
            argThat { changes ->
                changes.name == "typed" &&
                    changes.baseUrl == "http://typed" &&
                    changes.authToken == "secret" &&
                    changes.grafanaApiKey == "gf-key" &&
                    changes.autoStartManaged
            },
        )
    }

    @Test
    fun `delete maps the use case outcome to 204 and 404`() {
        whenever(management.deleteServerConfig(1L)).thenReturn(true)
        assertEquals(204, controller.delete(1L).statusCode.value())

        whenever(management.deleteServerConfig(99L)).thenReturn(false)
        assertEquals(404, controller.delete(99L).statusCode.value())
    }

    @Test
    fun `the connection test renders the view as the response payload`() {
        whenever(management.testServerConnection(1L)).thenReturn(null)
        assertEquals(404, controller.test(1L).statusCode.value())

        whenever(management.testServerConnection(2L)).thenReturn(
            McpConnectionTestView(
                ok = true,
                message = "up",
                toolCount = 1,
                tools = listOf(McpToolView(name = "tool-a", description = "desc")),
            ),
        )

        val body = requireNotNull(controller.test(2L).body)

        assertEquals(true, body["ok"])
        assertEquals("up", body["message"])
        assertEquals(1, body["toolCount"])
        assertEquals(mapOf("name" to "tool-a", "description" to "desc"), (body["tools"] as List<*>).single())
    }

    @Test
    fun `sync delegates and maps the resulting view`() {
        whenever(management.syncFromStacklens()).thenReturn(view(name = "synced"))

        assertEquals("synced", controller.syncFromStacklens().name)
        verify(management).syncFromStacklens()
    }

    @Test
    fun `process endpoints render the status view and map missing configs`() {
        whenever(management.startServer(99L)).thenReturn(null)
        assertEquals(404, controller.processStart(99L).statusCode.value())

        whenever(management.startServer(1L)).thenReturn(processView())
        val started = requireNotNull(controller.processStart(1L).body)
        assertEquals(true, started["running"])
        assertEquals(4242L, started["pid"])
        assertEquals(listOf("line"), started["logs"])
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L).toString(), started["startedAt"])

        whenever(management.stopServer()).thenReturn(processView(message = "stopped"))
        assertEquals("stopped", controller.processStop()["message"])

        whenever(management.serverProcessStatus()).thenReturn(processView(message = "status"))
        assertEquals("status", controller.processStatus()["message"])
    }

    @Test
    fun `the automatic start answers with the missing config error when no server exists`() {
        whenever(management.startFirstAvailableServer()).thenReturn(null)
        whenever(i18n.t("mcp.be_no_config")).thenReturn("keine Konfiguration")

        val response = controller.processStartAuto()

        assertEquals(400, response.statusCode.value())
        assertEquals("keine Konfiguration", response.body?.get("error"))

        whenever(management.startFirstAvailableServer()).thenReturn(processView())
        assertEquals(200, controller.processStartAuto().statusCode.value())
    }

    @Test
    fun `restart delegates and keeps the missing config answer`() {
        whenever(management.restartServer()).thenReturn(processView(message = "restarted"))
        assertEquals("restarted", controller.processRestart().body?.get("message"))

        whenever(management.restartServer()).thenReturn(null)
        assertEquals(400, controller.processRestart().statusCode.value())
    }

    @Test
    fun `the dto defaults an absent id to a new config`() {
        whenever(management.saveServerConfig(anyOrNull(), any())).thenReturn(view())

        controller.save(McpServerConfigDto(name = "fresh"))

        verify(management).saveServerConfig(anyOrNull(), argThat { changes -> changes.name == "fresh" })
    }
}
