package com.stacklens.application.service.health

import com.stacklens.adapter.out.health.InMemoryConnectionHealthTracker
import com.stacklens.application.port.out.health.ConfluenceConnectionProbePort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.health.DatabaseConnectionProbePort
import com.stacklens.application.port.out.health.GithubConnectionProbePort
import com.stacklens.application.port.out.health.GitlabConnectionProbePort
import com.stacklens.application.port.out.health.GrafanaConnectionProbePort
import com.stacklens.application.port.out.health.JiraConnectionProbePort
import com.stacklens.application.port.out.health.McpConnectionProbePort
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.DatabaseConfig
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConnectionHealthProbeServiceStartupResilienceTest {
    private val dbRepo = mock<com.stacklens.application.port.out.DatabaseConfigPort>()
    private val jiraRepo = mock<com.stacklens.application.port.out.JiraConfigPort>()
    private val confluenceRepo = mock<com.stacklens.application.port.out.ConfluenceConfigPort>()
    private val gitlabRepo = mock<com.stacklens.application.port.out.GitlabConfigPort>()
    private val githubRepo = mock<com.stacklens.application.port.out.GithubRepoConfigPort>()
    private val grafanaRepo = mock<com.stacklens.application.port.out.GrafanaConfigPort>()
    private val mcpRepo = mock<com.stacklens.application.port.out.McpServerConfigPort>()
    private val aiRepo = mock<com.stacklens.application.port.out.AiConfigPort>()
    private val dbProbe = mock<DatabaseConnectionProbePort>()
    private val jiraProbe = mock<JiraConnectionProbePort>()
    private val confluenceProbe = mock<ConfluenceConnectionProbePort>()
    private val gitlabProbe = mock<GitlabConnectionProbePort>()
    private val githubProbe = mock<GithubConnectionProbePort>()
    private val grafanaProbe = mock<GrafanaConnectionProbePort>()
    private val mcpProbe = mock<McpConnectionProbePort>()
    private val tracker = InMemoryConnectionHealthTracker()

    private fun newService() =
        ConnectionProbeTestSupport.buildService(
            dbRepo = dbRepo,
            jiraRepo = jiraRepo,
            confluenceRepo = confluenceRepo,
            gitlabRepo = gitlabRepo,
            githubRepo = githubRepo,
            grafanaRepo = grafanaRepo,
            aiRepo = aiRepo,
            mcpRepo = mcpRepo,
            dbProbe = dbProbe,
            jiraProbe = jiraProbe,
            confluenceProbe = confluenceProbe,
            gitlabProbe = gitlabProbe,
            githubProbe = githubProbe,
            grafanaProbe = grafanaProbe,
            mcpProbe = mcpProbe,
            tracker = tracker,
        )

    @Test
    fun `probeAll with empty database returns without throwing`() {
        whenever(dbRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(jiraRepo.findAllJiraConfigs()).thenReturn(emptyList())
        whenever(confluenceRepo.findAllConfluenceConfigs()).thenReturn(emptyList())
        whenever(gitlabRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(githubRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(grafanaRepo.findAllGrafanaConfigs()).thenReturn(emptyList())
        whenever(mcpRepo.findAllMcpServerConfigs()).thenReturn(emptyList())
        assertDoesNotThrow { newService().probeAll() }
    }

    @Test
    fun `probeAll swallows every probe throwing at once - app would still boot`() {
        val dbs = listOf(makeDb(1L), makeDb(2L))
        whenever(dbRepo.findAllDatabaseConfigs()).thenReturn(dbs)
        whenever(jiraRepo.findAllJiraConfigs()).thenThrow(RuntimeException("db down"))
        whenever(confluenceRepo.findAllConfluenceConfigs()).thenThrow(RuntimeException("db down"))
        whenever(gitlabRepo.findAllGitlabConfigs()).thenThrow(RuntimeException("db down"))
        whenever(githubRepo.findAllGithubRepoConfigs()).thenThrow(RuntimeException("db down"))
        whenever(grafanaRepo.findAllGrafanaConfigs()).thenThrow(RuntimeException("db down"))
        whenever(mcpRepo.findAllMcpServerConfigs()).thenThrow(RuntimeException("db down"))
        whenever(dbProbe.probe(any())).thenAnswer { throw IllegalStateException("nope") }
        assertDoesNotThrow { newService().probeAll() }
    }

    @Test
    fun `probeOne on a deleted row returns null without throwing`() {
        whenever(dbRepo.findDatabaseConfigById(404L)).thenReturn(null)
        val snap = newService().probeOne(ConnectionKind.DATABASE, 404L)
        assertNull(snap)
    }

    @Test
    fun `probeOne wrapping a thrown probe returns null instead of bubbling`() {
        val c = makeDb(1L)
        whenever(dbRepo.findDatabaseConfigById(1L)).thenReturn(c)
        whenever(dbRepo.saveDatabaseConfig(any())).thenAnswer { it.arguments[0] }
        whenever(dbProbe.probe(c)).thenThrow(RuntimeException("kaboom"))
        val snap = newService().probeOne(ConnectionKind.DATABASE, c.id)
        assertNull(snap, "a thrown probe must produce null, not propagate")
    }

    @Test
    fun `probeAll of a connection that has been failing for a long time never auto-disables it`() {
        val c = makeDb(1L)
        whenever(dbRepo.findAllDatabaseConfigs()).thenReturn(listOf(c))
        whenever(dbRepo.findDatabaseConfigById(1L)).thenReturn(c)
        whenever(dbProbe.probe(c)).thenReturn(ConnectionProbeResult.Failure("down"))
        whenever(dbRepo.saveDatabaseConfig(any())).thenAnswer { it.arguments[0] }
        newService().apply {
            repeat(10) { probeAll() }
            assertTrue(c.active, "active must stay true after 10 failed probes")
            assertTrue(c.consecutiveFailures >= 10, "counter must reflect all failures")
        }
    }

    private fun makeDb(id: Long): DatabaseConfig =
        DatabaseConfig(
            id = id,
            name = "db-$id",
            dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
            host = "127.0.0.1",
            port = 1,
            databaseName = "x",
            username = "u",
            password = "p",
        )
}
