package com.stacklens.application.service.mcp

import com.stacklens.application.port.inbound.mcp.McpServerConfigChanges
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpPingResult
import com.stacklens.application.port.out.McpProcessControlPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolDescriptor
import com.stacklens.application.port.out.mcp.McpSyncPort
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class McpServerManagementServiceTest {
    private val repo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val processControl: McpProcessControlPort = mock()
    private val sleeper: SleeperPort = SleeperPort { true }

    private val syncUseCase: McpSyncPort = mock()

    private val service = McpServerManagementService(repo, gateway, processControl, syncUseCase, sleeper)

    private fun changes(
        name: String = "mcp",
        baseUrl: String = "http://mcp",
        authToken: String = "",
        jiraApiToken: String = "",
        confluenceApiToken: String = "",
        gitlabAccessToken: String = "",
        githubToken: String = "",
        grafanaApiKey: String = "",
        autoStartManaged: Boolean = false,
    ) = McpServerConfigChanges(
        name = name,
        baseUrl = baseUrl,
        authToken = authToken,
        active = true,
        routeJira = true,
        routeConfluence = true,
        routeGitlab = true,
        routeGithub = true,
        routeGrafana = true,
        routeDatabase = true,
        jiraBaseUrl = "",
        jiraUsername = "",
        jiraApiToken = jiraApiToken,
        jiraCloudMode = true,
        confluenceBaseUrl = "",
        confluenceUsername = "",
        confluenceApiToken = confluenceApiToken,
        confluenceCloudMode = true,
        gitlabBaseUrl = "https://gitlab.com",
        gitlabAccessToken = gitlabAccessToken,
        githubApiBase = "https://api.github.com",
        githubToken = githubToken,
        grafanaBaseUrl = "",
        grafanaApiKey = grafanaApiKey,
        autoStartManaged = autoStartManaged,
    )

    private fun status(
        running: Boolean = true,
        message: String = "ok",
    ) = McpProcessControlPort.Status(
        running = running,
        pid = 4242L,
        startedAt = Instant.ofEpochMilli(1_700_000_000_000L),
        stoppedAt = null,
        exitCode = null,
        message = message,
        logs = listOf("line"),
    )

    @Test
    fun `a new config is created when no id is given`() {
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] as McpServerConfig }

        val view = service.saveServerConfig(null, changes(authToken = "secret"))

        assertTrue(view.secrets.authTokenSet)
        assertEquals("mcp", view.name)
    }

    @Test
    fun `an existing config keeps a secret when the change carries a blank one`() {
        val existing = McpServerConfig(id = 7L, name = "old", baseUrl = "http://old", authToken = "kept-token")
        whenever(repo.findMcpServerConfigById(7L)).thenReturn(existing)
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] as McpServerConfig }

        val view = service.saveServerConfig(7L, changes(name = "renamed"))

        assertEquals("kept-token", existing.authToken)
        assertEquals("renamed", view.name)
    }

    @Test
    fun `an existing config overwrites a secret when the change carries one`() {
        val existing = McpServerConfig(id = 7L, authToken = "old-token", jiraApiToken = "old-jira")
        whenever(repo.findMcpServerConfigById(7L)).thenReturn(existing)
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] as McpServerConfig }

        val view = service.saveServerConfig(7L, changes(authToken = "new-token", jiraApiToken = "new-jira"))

        assertEquals("new-token", existing.authToken)
        assertEquals("new-jira", existing.jiraApiToken)
        assertTrue(view.secrets.jiraApiTokenSet)
    }

    @Test
    fun `list hides token values but reports their presence`() {
        whenever(repo.findAllMcpServerConfigs()).thenReturn(
            listOf(
                McpServerConfig(id = 1L, name = "a", baseUrl = "u", authToken = "t", githubToken = ""),
            ),
        )

        val view = service.listServerConfigs().single()

        assertTrue(view.secrets.authTokenSet)
        assertFalse(view.secrets.githubTokenSet)
    }

    @Test
    fun `delete removes an existing config and reports a missing one`() {
        whenever(repo.findMcpServerConfigById(1L)).thenReturn(McpServerConfig(id = 1L, name = "x"))

        assertTrue(service.deleteServerConfig(1L))
        verify(repo).deleteMcpServerConfigById(1L)
        assertFalse(service.deleteServerConfig(99L))
    }

    @Test
    fun `the connection test lists tools only after a successful ping`() {
        val cfg = McpServerConfig(id = 1L, name = "a", baseUrl = "u")
        whenever(repo.findMcpServerConfigById(1L)).thenReturn(cfg)
        whenever(gateway.ping(cfg)).thenReturn(McpPingResult(ok = true, message = "up", toolCount = 2))
        whenever(gateway.listTools(cfg)).thenReturn(listOf(McpToolDescriptor(name = "tool-a", description = "desc")))

        val result = service.testServerConnection(1L)

        assertEquals(1, result?.tools?.size)
        assertEquals("tool-a", result?.tools?.single()?.name)
    }

    @Test
    fun `the connection test skips tool listing when the ping fails`() {
        val cfg = McpServerConfig(id = 1L)
        whenever(repo.findMcpServerConfigById(1L)).thenReturn(cfg)
        whenever(gateway.ping(cfg)).thenReturn(McpPingResult(ok = false, message = "down"))

        val result = service.testServerConnection(1L)

        assertTrue(result?.tools?.isEmpty() == true)
        verify(gateway, never()).listTools(any())
    }

    @Test
    fun `the connection test reports a missing config as null`() {
        whenever(repo.findMcpServerConfigById(99L)).thenReturn(null)

        assertNull(service.testServerConnection(99L))
    }

    @Test
    fun `starting a known config delegates to the process control`() {
        val cfg = McpServerConfig(id = 1L)
        whenever(repo.findMcpServerConfigById(1L)).thenReturn(cfg)
        whenever(processControl.start(cfg)).thenReturn(status())

        val view = service.startServer(1L)

        assertEquals(4242L, view?.pid)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L).toString(), view?.startedAt)
    }

    @Test
    fun `the automatic start prefers the active config and falls back to the first one`() {
        val active = McpServerConfig(id = 1L)
        val fallback = McpServerConfig(id = 2L)
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(active)
        whenever(processControl.start(active)).thenReturn(status())

        assertEquals("ok", service.startFirstAvailableServer()?.message)

        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(repo.findAllMcpServerConfigs()).thenReturn(emptyList())
        assertNull(service.startFirstAvailableServer())

        whenever(repo.findAllMcpServerConfigs()).thenReturn(listOf(fallback))
        whenever(processControl.start(fallback)).thenReturn(status())
        assertEquals("ok", service.startFirstAvailableServer()?.message)
    }

    @Test
    fun `restart settles between stop and start`() {
        val cfg = McpServerConfig(id = 1L)
        var slept = false
        val sleepingService =
            McpServerManagementService(
                repo,
                gateway,
                processControl,
                syncUseCase,
                object : SleeperPort {
                    override fun sleep(millis: Long): Boolean {
                        slept = true
                        return true
                    }
                },
            )
        whenever(processControl.stop()).thenReturn(status())
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(processControl.start(cfg)).thenReturn(status())

        sleepingService.restartServer()

        assertTrue(slept)
        verify(processControl).stop()
        verify(processControl).start(cfg)
    }

    @Test
    fun `restart continues after a failing stop`() {
        val cfg = McpServerConfig(id = 1L)
        whenever(processControl.stop()).thenThrow(RuntimeException("boom"))
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(processControl.start(cfg)).thenReturn(status())

        assertEquals("ok", service.restartServer()?.message)
    }

    @Test
    fun `status and stop expose the current process state`() {
        whenever(processControl.status()).thenReturn(status(running = false, message = "stopped"))
        assertEquals("stopped", service.serverProcessStatus().message)

        whenever(processControl.stop()).thenReturn(status(message = "stopped"))
        assertEquals("stopped", service.stopServer().message)
    }

    @Test
    fun `sync delegates to the sync use case`() {
        whenever(syncUseCase.syncFromStacklens()).thenReturn(McpServerConfig(id = 3L, name = "synced"))

        assertEquals("synced", service.syncFromStacklens().name)
        verify(syncUseCase).syncFromStacklens()
    }

    @Test
    fun `every secret field keeps its stored value when the change leaves it blank`() {
        val existing =
            McpServerConfig(
                id = 7L,
                authToken = "auth",
                jiraApiToken = "jira",
                confluenceApiToken = "wiki",
                gitlabAccessToken = "gl",
                githubToken = "gh",
                grafanaApiKey = "gf",
            )
        whenever(repo.findMcpServerConfigById(7L)).thenReturn(existing)
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] }

        service.saveServerConfig(7L, changes())

        assertEquals("auth", existing.authToken)
        assertEquals("jira", existing.jiraApiToken)
        assertEquals("wiki", existing.confluenceApiToken)
        assertEquals("gl", existing.gitlabAccessToken)
        assertEquals("gh", existing.githubToken)
        assertEquals("gf", existing.grafanaApiKey)
    }

    @Test
    fun `every secret field is replaced when the change carries a value`() {
        val existing = McpServerConfig(id = 7L)
        whenever(repo.findMcpServerConfigById(7L)).thenReturn(existing)
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] }

        val view =
            service.saveServerConfig(
                7L,
                changes(
                    authToken = "a",
                    jiraApiToken = "j",
                    confluenceApiToken = "c",
                    gitlabAccessToken = "g",
                    githubToken = "h",
                    grafanaApiKey = "f",
                ),
            )

        assertEquals("a", existing.authToken)
        assertTrue(view.secrets.authTokenSet)
        assertTrue(view.secrets.jiraApiTokenSet)
        assertTrue(view.secrets.confluenceApiTokenSet)
        assertTrue(view.secrets.gitlabAccessTokenSet)
        assertTrue(view.secrets.githubTokenSet)
        assertTrue(view.secrets.grafanaApiKeySet)
    }

    @Test
    fun `blank text columns are reported as unset secrets`() {
        whenever(repo.findAllMcpServerConfigs()).thenReturn(listOf(McpServerConfig(id = 2L, name = "", baseUrl = "")))

        val view = service.listServerConfigs().single()

        assertEquals("", view.name)
        assertEquals("https://gitlab.com", view.gitlabBaseUrl)
        assertEquals("https://api.github.com", view.githubApiBase)
        assertEquals("", view.grafanaBaseUrl)
        assertFalse(view.secrets.authTokenSet)
    }

    @Test
    fun `a process status without timestamps renders both as null`() {
        whenever(repo.findMcpServerConfigById(1L)).thenReturn(McpServerConfig(id = 1L))
        whenever(processControl.start(any())).thenReturn(
            McpProcessControlPort.Status(
                running = false,
                pid = null,
                startedAt = null,
                stoppedAt = null,
                exitCode = 1,
                message = "exited",
                logs = emptyList(),
            ),
        )

        val view = requireNotNull(service.startServer(1L))

        assertEquals(null, view.startedAt)
        assertEquals(null, view.stoppedAt)
        assertEquals(1, view.exitCode)
    }

    @Test
    fun `starting an unknown config answers as missing`() {
        whenever(repo.findMcpServerConfigById(42L)).thenReturn(null)

        assertNull(service.startServer(42L))
    }

    @Test
    fun `a config id of zero is treated as a new record`() {
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] }

        service.saveServerConfig(0L, changes(name = "fresh"))

        verify(repo, org.mockito.kotlin.never()).findMcpServerConfigById(0L)
    }

    @Test
    fun `a stored secret survives an id lookup that returns the previous record`() {
        val stored = McpServerConfig(id = 3L, githubToken = "keep-me")
        whenever(repo.findMcpServerConfigById(3L)).thenReturn(stored)
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] }

        service.saveServerConfig(3L, changes(githubToken = "  "))

        assertEquals("keep-me", stored.githubToken)
    }
}
