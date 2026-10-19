package com.stacklens.application.service

import com.stacklens.adapter.out.mcp.McpCircuitBreaker
import com.stacklens.adapter.out.mcp.McpCircuitStateAdapter
import com.stacklens.application.port.inbound.mcp.McpToolCallRequest
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpProcessControlPort
import com.stacklens.application.port.out.McpProcessControlPort.Status
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolDescriptor
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.time.Instant

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpManagementServiceTest {
    @Mock private lateinit var repo: McpServerConfigPort

    @Mock private lateinit var gateway: McpGatewayPort

    @Mock private lateinit var processControl: McpProcessControlPort

    @Mock private lateinit var databaseConfigs: DatabaseConfigPort

    private val breaker = McpCircuitBreaker()

    private fun newService() =
        McpManagementService(
            repo,
            gateway,
            processControl,
            databaseConfigs,
            McpCircuitStateAdapter(breaker),
            com.stacklens.application.service
                .FakeI18n(),
        )

    private fun newConfig(
        id: Long = 1L,
        active: Boolean = true,
        routeJira: Boolean = true,
        routeConfluence: Boolean = true,
        routeGitlab: Boolean = true,
        routeGithub: Boolean = true,
        routeGrafana: Boolean = true,
        routeDatabase: Boolean = true,
        jiraBaseUrl: String = "",
        jiraApiToken: String = "",
        confluenceBaseUrl: String = "",
        confluenceApiToken: String = "",
        gitlabAccessToken: String = "",
        githubToken: String = "",
        grafanaBaseUrl: String = "",
    ): McpServerConfig =
        McpServerConfig(
            id = id,
            name = "test-mcp",
            baseUrl = "http://localhost:7456",
            active = active,
            routeJira = routeJira,
            routeConfluence = routeConfluence,
            routeGitlab = routeGitlab,
            routeGithub = routeGithub,
            routeGrafana = routeGrafana,
            routeDatabase = routeDatabase,
            jiraBaseUrl = jiraBaseUrl,
            jiraApiToken = jiraApiToken,
            confluenceBaseUrl = confluenceBaseUrl,
            confluenceApiToken = confluenceApiToken,
            gitlabAccessToken = gitlabAccessToken,
            githubToken = githubToken,
            grafanaBaseUrl = grafanaBaseUrl,
        )

    @Test
    fun `listTools returns null when no config can be resolved`() {
        val service = newService()
        assertNull(service.listTools(null))
    }

    @Test
    fun `listTools uses the explicit config when an id is given`() {
        val cfg = newConfig(id = 7L)
        whenever(repo.findMcpServerConfigById(7L)).thenReturn(cfg)
        whenever(gateway.listTools(any())).thenReturn(listOf(McpToolDescriptor("foo", "foo desc")))
        val result = newService().listTools(7L)
        assertNotNull(result)
        assertEquals(7L, result!!.mcpServerId)
        assertEquals("test-mcp", result.mcpServerName)
        assertEquals(1, result.tools.size)
        assertEquals("foo", result.tools.first().name)
        assertEquals("foo desc", result.tools.first().description)
    }

    @Test
    fun `listTools uses the active config when id is null`() {
        val cfg = newConfig()
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(gateway.listTools(any())).thenReturn(emptyList())
        val result = newService().listTools(null)
        assertNotNull(result)
        assertTrue(result!!.tools.isEmpty())
    }

    @Test
    fun `callTool returns error result when config cannot be resolved`() {
        val service = newService()
        val r = service.callTool(McpToolCallRequest(mcpServerId = null, tool = "x", args = emptyMap<String, Any?>()))
        assertFalse(r.ok)
        assertEquals("x", r.tool)
        assertTrue(r.text.contains("no_active_server"))
    }

    @Test
    fun `callTool returns ok=true when gateway result has no error`() {
        val cfg = newConfig()
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = false, text = "OK"))
        val r = newService().callTool(McpToolCallRequest(mcpServerId = null, tool = "t", args = mapOf("k" to "v" as Any?)))
        assertTrue(r.ok)
        assertEquals("OK", r.text)
    }

    @Test
    fun `callTool returns ok=false when gateway reports error`() {
        val cfg = newConfig()
        whenever(repo.findMcpServerConfigById(2L)).thenReturn(cfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "fail"))
        val r = newService().callTool(McpToolCallRequest(mcpServerId = 2L, tool = "t", args = emptyMap<String, Any?>()))
        assertFalse(r.ok)
        assertEquals("fail", r.text)
    }

    @Test
    fun `getStatus returns inactive when no active config exists`() {
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.active)
        assertNull(s.mcpServerName)
        assertFalse(s.routes["jira"]!!)
        assertFalse(s.routes["confluence"]!!)
        assertFalse(s.routes["gitlab"]!!)
        assertFalse(s.routes["github"]!!)
        assertFalse(s.routes["grafana"]!!)
        assertFalse(s.routes["database"]!!)
    }

    @Test
    fun `getStatus reports active config with all routes enabled`() {
        val cfg =
            newConfig(
                jiraBaseUrl = "https://jira.example.com",
                jiraApiToken = "tok",
                confluenceBaseUrl = "https://conf.example.com",
                confluenceApiToken = "tok",
                gitlabAccessToken = "glpat",
                githubToken = "gh",
                grafanaBaseUrl = "https://grafana.example.com",
            )
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(
            databaseConfigs.findByActiveTrue(),
        ).thenReturn(
            listOf(
                com.stacklens.domain.model.DatabaseConfig(
                    name = "test-db",
                    dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                    host = "h",
                    port = 5432,
                    databaseName = "d",
                    username = "u",
                    password = "p",
                ),
            ),
        )
        whenever(processControl.status()).thenReturn(Status(true, 42L, Instant.now(), null, null, "running", emptyList()))
        val s = newService().getStatus()
        assertTrue(s.active)
        assertEquals("test-mcp", s.mcpServerName)
        assertTrue(s.routes["jira"]!!)
        assertTrue(s.routes["confluence"]!!)
        assertTrue(s.routes["gitlab"]!!)
        assertTrue(s.routes["github"]!!)
        assertTrue(s.routes["grafana"]!!)
        assertTrue(s.routes["database"]!!)
        assertTrue(s.processRunning)
    }

    @Test
    fun `getStatus disables jira when jiraApiToken blank`() {
        val cfg =
            newConfig(
                jiraBaseUrl = "https://jira.example.com",
                jiraApiToken = "",
            )
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["jira"]!!)
    }

    @Test
    fun `getStatus disables jira when jiraBaseUrl blank`() {
        val cfg =
            newConfig(
                jiraBaseUrl = "",
                jiraApiToken = "tok",
            )
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["jira"]!!)
    }

    @Test
    fun `getStatus disables jira when routeJira is false`() {
        val cfg = newConfig(routeJira = false, jiraBaseUrl = "https://jira.example.com", jiraApiToken = "tok")
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["jira"]!!)
    }

    @Test
    fun `getStatus disables confluence when routeConfluence is false`() {
        val cfg =
            newConfig(
                routeConfluence = false,
                confluenceBaseUrl = "https://conf.example.com",
                confluenceApiToken = "tok",
            )
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["confluence"]!!)
    }

    @Test
    fun `getStatus disables confluence when token blank`() {
        val cfg = newConfig(confluenceBaseUrl = "https://conf.example.com", confluenceApiToken = "")
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["confluence"]!!)
    }

    @Test
    fun `getStatus disables confluence when baseUrl blank`() {
        val cfg = newConfig(confluenceBaseUrl = "", confluenceApiToken = "tok")
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["confluence"]!!)
    }

    @Test
    fun `getStatus disables gitlab when token blank`() {
        val cfg = newConfig(gitlabAccessToken = "")
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["gitlab"]!!)
    }

    @Test
    fun `getStatus disables gitlab when routeGitlab is false`() {
        val cfg = newConfig(routeGitlab = false, gitlabAccessToken = "tok")
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["gitlab"]!!)
    }

    @Test
    fun `getStatus disables github when token blank`() {
        val cfg = newConfig(githubToken = "")
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["github"]!!)
    }

    @Test
    fun `getStatus disables github when routeGithub is false`() {
        val cfg = newConfig(routeGithub = false, githubToken = "tok")
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["github"]!!)
    }

    @Test
    fun `getStatus disables grafana when routeGrafana is false`() {
        val cfg = newConfig(routeGrafana = false, grafanaBaseUrl = "https://grafana.example.com")
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["grafana"]!!)
    }

    @Test
    fun `getStatus disables grafana when baseUrl blank`() {
        val cfg = newConfig(grafanaBaseUrl = "")
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["grafana"]!!)
    }

    @Test
    fun `getStatus disables database when no db config exists`() {
        val cfg = newConfig(routeDatabase = true)
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["database"]!!)
    }

    @Test
    fun `getStatus disables database when routeDatabase is false`() {
        val cfg = newConfig(routeDatabase = false)
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(
            databaseConfigs.findByActiveTrue(),
        ).thenReturn(
            listOf(
                com.stacklens.domain.model.DatabaseConfig(
                    name = "test-db",
                    dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                    host = "h",
                    port = 5432,
                    databaseName = "d",
                    username = "u",
                    password = "p",
                ),
            ),
        )
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["database"]!!)
    }

    @Test
    fun `getStatus handles inactive config (active=false) gracefully`() {
        val cfg =
            newConfig(
                active = false,
                jiraBaseUrl = "u",
                jiraApiToken = "t",
                confluenceBaseUrl = "u",
                confluenceApiToken = "t",
                gitlabAccessToken = "t",
                githubToken = "t",
                grafanaBaseUrl = "u",
            )
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(cfg)
        whenever(
            databaseConfigs.findByActiveTrue(),
        ).thenReturn(
            listOf(
                com.stacklens.domain.model.DatabaseConfig(
                    name = "test-db",
                    dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                    host = "h",
                    port = 5432,
                    databaseName = "d",
                    username = "u",
                    password = "p",
                ),
            ),
        )
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.active)
        assertFalse(s.routes["jira"]!!)
        assertFalse(s.routes["confluence"]!!)
        assertFalse(s.routes["gitlab"]!!)
        assertFalse(s.routes["github"]!!)
        assertFalse(s.routes["grafana"]!!)
        assertFalse(s.routes["database"]!!)
    }

    @Test
    fun `getStatus swallows exceptions from repo`() {
        whenever(repo.findFirstActiveMcpServerConfig()).thenThrow(RuntimeException("db boom"))
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.active)
    }

    @Test
    fun `getStatus swallows exceptions from databaseConfigs`() {
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(databaseConfigs.findByActiveTrue()).thenThrow(RuntimeException("db boom"))
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertFalse(s.routes["database"]!!)
    }

    @Test
    fun `getStatus exposes an empty circuits map when the circuit breaker is idle`() {
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(newConfig())
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        val s = newService().getStatus()
        assertNotNull(s.circuits)
        assertTrue(s.circuits.isEmpty(), "no circuits must be reported when the breaker is idle")
    }

    @Test
    fun `getStatus reflects a closed circuit with a non-zero failure count`() {
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(newConfig(id = 7L))
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        repeat(2) { breaker.recordFailure("7:jira") }
        val s = newService().getStatus()
        val entry = s.circuits.getValue("7:jira")
        assertEquals(7L, entry.mcpConfigId)
        assertEquals("jira", entry.provider)
        assertFalse(entry.isOpen)
        assertEquals(2, entry.failures)
    }

    @Test
    fun `getStatus reflects an open circuit so the UI can show the fallback banner`() {
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(newConfig(id = 7L))
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        repeat(3) { breaker.recordFailure("7:gitlab") }
        val s = newService().getStatus()
        val entry = s.circuits.getValue("7:gitlab")
        assertEquals("gitlab", entry.provider)
        assertTrue(entry.isOpen, "open circuits must be reported as isOpen so the banner can render")
        assertEquals(3, entry.failures)
        assertTrue(entry.openedAt > 0L)
    }

    @Test
    fun `getStatus surfaces circuits from multiple providers simultaneously`() {
        whenever(repo.findFirstActiveMcpServerConfig()).thenReturn(newConfig(id = 1L))
        whenever(databaseConfigs.findByActiveTrue()).thenReturn(emptyList())
        whenever(processControl.status()).thenReturn(Status(false, null, null, null, null, "idle", emptyList()))
        repeat(3) { breaker.recordFailure("1:jira") }
        repeat(2) { breaker.recordFailure("1:gitlab") }
        val s = newService().getStatus()
        assertEquals(setOf("1:jira", "1:gitlab"), s.circuits.keys)
        assertTrue(s.circuits.getValue("1:jira").isOpen)
        assertFalse(s.circuits.getValue("1:gitlab").isOpen)
    }
}
