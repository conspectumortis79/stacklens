package com.stacklens.application.service.health

import com.stacklens.adapter.out.health.InMemoryConnectionHealthTracker
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.health.ConfluenceConnectionProbePort
import com.stacklens.application.port.out.health.ConnectionProbeResult
import com.stacklens.application.port.out.health.DatabaseConnectionProbePort
import com.stacklens.application.port.out.health.GithubConnectionProbePort
import com.stacklens.application.port.out.health.GitlabConnectionProbePort
import com.stacklens.application.port.out.health.GrafanaConnectionProbePort
import com.stacklens.application.port.out.health.JiraConnectionProbePort
import com.stacklens.application.port.out.health.McpConnectionProbePort
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConnectionHealthProbeServiceExhaustiveTest {
    private val dbRepo = mock<DatabaseConfigPort>()
    private val jiraRepo = mock<JiraConfigPort>()
    private val confluenceRepo = mock<ConfluenceConfigPort>()
    private val gitlabRepo = mock<GitlabConfigPort>()
    private val githubRepo = mock<GithubRepoConfigPort>()
    private val grafanaRepo = mock<GrafanaConfigPort>()
    private val aiRepo = mock<AiConfigPort>()
    private val mcpRepo = mock<McpServerConfigPort>()
    private val dbProbe = mock<DatabaseConnectionProbePort>()
    private val jiraProbe = mock<JiraConnectionProbePort>()
    private val confluenceProbe = mock<ConfluenceConnectionProbePort>()
    private val gitlabProbe = mock<GitlabConnectionProbePort>()
    private val githubProbe = mock<GithubConnectionProbePort>()
    private val grafanaProbe = mock<GrafanaConnectionProbePort>()
    private val mcpProbe = mock<McpConnectionProbePort>()
    private val tracker = InMemoryConnectionHealthTracker()

    private fun newService(): ConnectionHealthProbeService =
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
    fun `probeOne with ConnectionKind DATABASE delegates to probeOneDatabase and returns snapshot`() {
        val c = db(active = true, consecutive = 0)
        whenever(dbRepo.findDatabaseConfigById(c.id)).thenReturn(c)
        whenever(dbProbe.probe(c)).thenReturn(ConnectionProbeResult.Ok())
        whenever(dbRepo.saveDatabaseConfig(any())).thenAnswer { it.arguments[0] }
        val snap = newService().probeOne(ConnectionKind.DATABASE, c.id)
        assertNotNull(snap)
        assertEquals(ConnectionKind.DATABASE.id, snap!!.kind)
        assertEquals(true, snap.healthy)
    }

    @Test
    fun `probeOne with ConnectionKind JIRA delegates to probeOneJira and returns snapshot`() {
        val c = jira(active = true)
        whenever(jiraRepo.findJiraConfigById(c.id)).thenReturn(c)
        whenever(jiraProbe.probe(c)).thenReturn(ConnectionProbeResult.Ok())
        whenever(jiraRepo.saveJiraConfig(any())).thenAnswer { it.arguments[0] }
        val snap = newService().probeOne(ConnectionKind.JIRA, c.id)
        assertNotNull(snap)
        assertEquals(ConnectionKind.JIRA.id, snap!!.kind)
    }

    @Test
    fun `probeOne with ConnectionKind CONFLUENCE delegates to probeOneConfluence and returns snapshot`() {
        val c = confluence(active = true)
        whenever(confluenceRepo.findConfluenceConfigById(c.id)).thenReturn(c)
        whenever(confluenceProbe.probe(c)).thenReturn(ConnectionProbeResult.Ok())
        whenever(confluenceRepo.saveConfluenceConfig(any())).thenAnswer { it.arguments[0] }
        val snap = newService().probeOne(ConnectionKind.CONFLUENCE, c.id)
        assertNotNull(snap)
        assertEquals(ConnectionKind.CONFLUENCE.id, snap!!.kind)
    }

    @Test
    fun `probeOne with ConnectionKind GITLAB delegates to probeOneGitlab and returns snapshot`() {
        val c = gitlab(active = true)
        whenever(gitlabRepo.findGitlabConfigById(c.id)).thenReturn(c)
        whenever(gitlabProbe.probe(c)).thenReturn(ConnectionProbeResult.Ok())
        whenever(gitlabRepo.saveGitlabConfig(any())).thenAnswer { it.arguments[0] }
        val snap = newService().probeOne(ConnectionKind.GITLAB, c.id)
        assertNotNull(snap)
        assertEquals(ConnectionKind.GITLAB.id, snap!!.kind)
    }

    @Test
    fun `probeOne with ConnectionKind GITHUB delegates to probeOneGithub and returns snapshot`() {
        val c = github(active = true)
        whenever(githubRepo.findGithubRepoConfigById(c.id)).thenReturn(c)
        whenever(aiRepo.findAiConfig()).thenReturn(null)
        whenever(githubProbe.probe(eq("me"), eq("repo"), isNull(), any())).thenReturn(ConnectionProbeResult.Ok())
        whenever(githubRepo.saveGithubRepoConfig(any())).thenAnswer { it.arguments[0] }
        val snap = newService().probeOne(ConnectionKind.GITHUB, c.id)
        assertNotNull(snap)
        assertEquals(ConnectionKind.GITHUB.id, snap!!.kind)
    }

    @Test
    fun `probeOne with ConnectionKind GRAFANA delegates to probeOneGrafana and returns snapshot`() {
        val c = grafana(active = true)
        whenever(grafanaRepo.findGrafanaConfigById(c.id)).thenReturn(c)
        whenever(grafanaProbe.probe(c)).thenReturn(ConnectionProbeResult.Ok())
        whenever(grafanaRepo.saveGrafanaConfig(any())).thenAnswer { it.arguments[0] }
        val snap = newService().probeOne(ConnectionKind.GRAFANA, c.id)
        assertNotNull(snap)
        assertEquals(ConnectionKind.GRAFANA.id, snap!!.kind)
    }

    @Test
    fun `probeOne with ConnectionKind MCP delegates to probeOneMcp and returns snapshot`() {
        val c = mcp(active = true)
        whenever(mcpRepo.findMcpServerConfigById(c.id)).thenReturn(c)
        whenever(mcpProbe.probe(c)).thenReturn(ConnectionProbeResult.Ok())
        whenever(mcpRepo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] }
        val snap = newService().probeOne(ConnectionKind.MCP, c.id)
        assertNotNull(snap)
        assertEquals(ConnectionKind.MCP.id, snap!!.kind)
    }

    @Test
    fun `probeOne swallows exceptions thrown by per-kind probe and returns null`() {
        whenever(dbRepo.findDatabaseConfigById(123L)).thenThrow(RuntimeException("boom"))
        val snap = newService().probeOne(ConnectionKind.DATABASE, 123L)
        assertNull(snap)
    }

    @Test
    fun `probeAll iterates every database and saves snapshots in tracker`() {
        val a = db(active = true, consecutive = 0)
        val b =
            DatabaseConfig(
                id = 2L,
                name = "db2",
                dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "x",
                username = "u",
                password = "p",
            )
        b.active = true
        whenever(dbRepo.findAllDatabaseConfigs()).thenReturn(listOf(a, b))
        whenever(dbProbe.probe(a)).thenReturn(ConnectionProbeResult.Ok())
        whenever(dbProbe.probe(b)).thenReturn(ConnectionProbeResult.Failure("nope"))
        whenever(dbRepo.saveDatabaseConfig(any())).thenAnswer { it.arguments[0] }
        newService().probeAll()
        val snaps = tracker.snapshotAll()
        assertEquals(2, snaps.size)
    }

    @Test
    fun `probeAll iterates every jira and saves snapshots in tracker`() {
        val a = jira(active = true)
        whenever(jiraRepo.findAllJiraConfigs()).thenReturn(listOf(a))
        whenever(jiraRepo.findJiraConfigById(a.id)).thenReturn(a)
        whenever(jiraProbe.probe(a)).thenReturn(ConnectionProbeResult.Ok())
        whenever(jiraRepo.saveJiraConfig(any())).thenAnswer { it.arguments[0] }
        newService().probeAll()
        assertNotNull(tracker.snapshotAll()["jira:${a.id}"])
    }

    @Test
    fun `probeAll iterates every confluence and saves snapshots in tracker`() {
        val a = confluence(active = true)
        whenever(confluenceRepo.findAllConfluenceConfigs()).thenReturn(listOf(a))
        whenever(confluenceRepo.findConfluenceConfigById(a.id)).thenReturn(a)
        whenever(confluenceProbe.probe(a)).thenReturn(ConnectionProbeResult.Ok())
        whenever(confluenceRepo.saveConfluenceConfig(any())).thenAnswer { it.arguments[0] }
        newService().probeAll()
        assertNotNull(tracker.snapshotAll()["confluence:${a.id}"])
    }

    @Test
    fun `probeAll iterates every gitlab and saves snapshots in tracker`() {
        val a = gitlab(active = true)
        whenever(gitlabRepo.findAllGitlabConfigs()).thenReturn(listOf(a))
        whenever(gitlabRepo.findGitlabConfigById(a.id)).thenReturn(a)
        whenever(gitlabProbe.probe(a)).thenReturn(ConnectionProbeResult.Ok())
        whenever(gitlabRepo.saveGitlabConfig(any())).thenAnswer { it.arguments[0] }
        newService().probeAll()
        assertNotNull(tracker.snapshotAll()["gitlab:${a.id}"])
    }

    @Test
    fun `probeAll iterates every grafana and saves snapshots in tracker`() {
        val a = grafana(active = true)
        whenever(grafanaRepo.findAllGrafanaConfigs()).thenReturn(listOf(a))
        whenever(grafanaRepo.findGrafanaConfigById(a.id)).thenReturn(a)
        whenever(grafanaProbe.probe(a)).thenReturn(ConnectionProbeResult.Ok())
        whenever(grafanaRepo.saveGrafanaConfig(any())).thenAnswer { it.arguments[0] }
        newService().probeAll()
        assertNotNull(tracker.snapshotAll()["grafana:${a.id}"])
    }

    @Test
    fun `probeAll iterates every mcp and saves snapshots in tracker`() {
        val a = mcp(active = true)
        whenever(mcpRepo.findAllMcpServerConfigs()).thenReturn(listOf(a))
        whenever(mcpRepo.findMcpServerConfigById(a.id)).thenReturn(a)
        whenever(mcpProbe.probe(a)).thenReturn(ConnectionProbeResult.Ok())
        whenever(mcpRepo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] }
        newService().probeAll()
        assertNotNull(tracker.snapshotAll()["mcp:${a.id}"])
    }

    @Test
    fun `probeAllGithub uses per-row token when present and skips AI fallback`() {
        val c =
            github(active = true).apply {
                accessToken = "rowToken"
                apiBase = "https://ghe.example/api/v3"
            }
        whenever(aiRepo.findAiConfig()).thenReturn(null)
        whenever(githubRepo.findAllGithubRepoConfigs()).thenReturn(listOf(c))
        whenever(githubRepo.findGithubRepoConfigById(c.id)).thenReturn(c)
        whenever(githubProbe.probe(eq("me"), eq("repo"), eq("rowToken"), eq("https://ghe.example/api/v3")))
            .thenReturn(ConnectionProbeResult.Ok())
        whenever(githubRepo.saveGithubRepoConfig(any())).thenAnswer { it.arguments[0] }
        newService().probeAll()
        assertNotNull(tracker.snapshotAll()["github:${c.id}"])
    }

    @Test
    fun `probeAllGithub uses AI config githubToken as fallback when row token blank`() {
        val c = github(active = true).apply { accessToken = "" }
        val ai = AiConfig(id = 1, githubToken = "aiToken")
        whenever(aiRepo.findAiConfig()).thenReturn(ai)
        whenever(githubRepo.findAllGithubRepoConfigs()).thenReturn(listOf(c))
        whenever(githubRepo.findGithubRepoConfigById(c.id)).thenReturn(c)
        whenever(githubProbe.probe(eq("me"), eq("repo"), eq("aiToken"), any()))
            .thenReturn(ConnectionProbeResult.Ok())
        whenever(githubRepo.saveGithubRepoConfig(any())).thenAnswer { it.arguments[0] }
        newService().probeAll()
        assertNotNull(tracker.snapshotAll()["github:${c.id}"])
    }

    @Test
    fun `probeAllGithub uses default apiBase when both row and ai are blank`() {
        val c =
            github(active = true).apply {
                accessToken = "rowToken"
                apiBase = null
            }
        whenever(aiRepo.findAiConfig()).thenReturn(null)
        whenever(githubRepo.findAllGithubRepoConfigs()).thenReturn(listOf(c))
        whenever(githubRepo.findGithubRepoConfigById(c.id)).thenReturn(c)
        whenever(githubProbe.probe(eq("me"), eq("repo"), eq("rowToken"), eq("https://api.github.com")))
            .thenReturn(ConnectionProbeResult.Ok())
        whenever(githubRepo.saveGithubRepoConfig(any())).thenAnswer { it.arguments[0] }
        newService().probeAll()
        assertNotNull(tracker.snapshotAll()["github:${c.id}"])
    }

    @Test
    fun `probeAllGithub swallows per-row exceptions and continues`() {
        val a = github(active = true)
        val b = github(id = 2L)
        whenever(aiRepo.findAiConfig()).thenReturn(null)
        whenever(githubRepo.findAllGithubRepoConfigs()).thenReturn(listOf(a, b))
        whenever(githubProbe.probe(eq("me"), eq("repo"), isNull(), any()))
            .thenThrow(RuntimeException("boom"))
            .thenReturn(ConnectionProbeResult.Ok())
        whenever(githubRepo.saveGithubRepoConfig(any())).thenAnswer { it.arguments[0] }
        newService().probeAll()
        assertNotNull(tracker.snapshotAll()["github:2"])
    }

    @Test
    fun `probeOneGithub with null accessToken and null aiConfig passes null token to probe`() {
        val c =
            github(active = true).apply {
                accessToken = null
                apiBase = null
            }
        whenever(githubRepo.findGithubRepoConfigById(c.id)).thenReturn(c)
        whenever(aiRepo.findAiConfig()).thenReturn(null)
        whenever(githubProbe.probe(eq("me"), eq("repo"), isNull(), eq("https://api.github.com")))
            .thenReturn(ConnectionProbeResult.Ok())
        whenever(githubRepo.saveGithubRepoConfig(any())).thenAnswer { it.arguments[0] }
        assertNotNull(newService().probeOne(ConnectionKind.GITHUB, c.id))
    }

    @Test
    fun `probeOneGithub with blank accessToken falls back to ai config githubToken`() {
        val c = github(active = true).apply { accessToken = "   " }
        val ai = AiConfig(id = 1, githubToken = "fallback")
        whenever(githubRepo.findGithubRepoConfigById(c.id)).thenReturn(c)
        whenever(aiRepo.findAiConfig()).thenReturn(ai)
        whenever(githubProbe.probe(eq("me"), eq("repo"), eq("fallback"), any()))
            .thenReturn(ConnectionProbeResult.Ok())
        whenever(githubRepo.saveGithubRepoConfig(any())).thenAnswer { it.arguments[0] }
        assertNotNull(newService().probeOne(ConnectionKind.GITHUB, c.id))
    }

    @Test
    fun `probeOneGithub with non-blank apiBase uses it directly`() {
        val c = github(active = true).apply { apiBase = "https://ghe.example/api/v3" }
        whenever(githubRepo.findGithubRepoConfigById(c.id)).thenReturn(c)
        whenever(aiRepo.findAiConfig()).thenReturn(null)
        whenever(githubProbe.probe(eq("me"), eq("repo"), isNull(), eq("https://ghe.example/api/v3")))
            .thenReturn(ConnectionProbeResult.Ok())
        whenever(githubRepo.saveGithubRepoConfig(any())).thenAnswer { it.arguments[0] }
        assertNotNull(newService().probeOne(ConnectionKind.GITHUB, c.id))
    }

    @Test
    fun `probeOneGithub returns null when row not found`() {
        whenever(githubRepo.findGithubRepoConfigById(99L)).thenReturn(null)
        assertNull(newService().probeOne(ConnectionKind.GITHUB, 99L))
    }

    @Test
    fun `probeOneJira returns null when row not found`() {
        whenever(jiraRepo.findJiraConfigById(99L)).thenReturn(null)
        assertNull(newService().probeOne(ConnectionKind.JIRA, 99L))
    }

    @Test
    fun `probeOneConfluence returns null when row not found`() {
        whenever(confluenceRepo.findConfluenceConfigById(99L)).thenReturn(null)
        assertNull(newService().probeOne(ConnectionKind.CONFLUENCE, 99L))
    }

    @Test
    fun `probeOneGitlab returns null when row not found`() {
        whenever(gitlabRepo.findGitlabConfigById(99L)).thenReturn(null)
        assertNull(newService().probeOne(ConnectionKind.GITLAB, 99L))
    }

    @Test
    fun `probeOneGrafana returns null when row not found`() {
        whenever(grafanaRepo.findGrafanaConfigById(99L)).thenReturn(null)
        assertNull(newService().probeOne(ConnectionKind.GRAFANA, 99L))
    }

    @Test
    fun `probeOneMcp returns null when row not found`() {
        whenever(mcpRepo.findMcpServerConfigById(99L)).thenReturn(null)
        assertNull(newService().probeOne(ConnectionKind.MCP, 99L))
    }

    @Test
    fun `probeOneDatabase returns null when row not found`() {
        whenever(dbRepo.findDatabaseConfigById(99L)).thenReturn(null)
        assertNull(newService().probeOne(ConnectionKind.DATABASE, 99L))
    }

    @Test
    fun `probeAll per-kind per-row exception does not abort the whole batch`() {
        val a = jira(active = true)
        val b = jira(active = true)
        whenever(jiraRepo.findAllJiraConfigs()).thenReturn(listOf(a, b))
        whenever(jiraProbe.probe(a)).thenThrow(RuntimeException("probe boom"))
        whenever(jiraProbe.probe(b)).thenReturn(ConnectionProbeResult.Ok())
        whenever(jiraRepo.saveJiraConfig(any())).thenAnswer { it.arguments[0] }
        newService().probeAll()
        assertNotNull(tracker.snapshotAll()["jira:${b.id}"])
    }

    @Test
    fun `probeAll confluence per-row exception is caught and logged`() {
        val a = confluence(active = true)
        whenever(confluenceRepo.findAllConfluenceConfigs()).thenReturn(listOf(a))
        whenever(confluenceProbe.probe(a)).thenThrow(RuntimeException("boom"))
        newService().probeAll()
        assertNull(tracker.snapshotAll()["confluence:${a.id}"])
    }

    @Test
    fun `probeAll gitlab per-row exception is caught and logged`() {
        val a = gitlab(active = true)
        whenever(gitlabRepo.findAllGitlabConfigs()).thenReturn(listOf(a))
        whenever(gitlabProbe.probe(a)).thenThrow(RuntimeException("boom"))
        newService().probeAll()
        assertNull(tracker.snapshotAll()["gitlab:${a.id}"])
    }

    @Test
    fun `probeAll grafana per-row exception is caught and logged`() {
        val a = grafana(active = true)
        whenever(grafanaRepo.findAllGrafanaConfigs()).thenReturn(listOf(a))
        whenever(grafanaProbe.probe(a)).thenThrow(RuntimeException("boom"))
        newService().probeAll()
        assertNull(tracker.snapshotAll()["grafana:${a.id}"])
    }

    @Test
    fun `probeAll mcp per-row exception is caught and logged`() {
        val a = mcp(active = true)
        whenever(mcpRepo.findAllMcpServerConfigs()).thenReturn(listOf(a))
        whenever(mcpProbe.probe(a)).thenThrow(RuntimeException("boom"))
        newService().probeAll()
        assertNull(tracker.snapshotAll()["mcp:${a.id}"])
    }

    @Test
    fun `probeAll catches and logs when the entire probeAll() body throws`() {
        whenever(dbRepo.findAllDatabaseConfigs()).thenThrow(RuntimeException("catastrophic"))
        newService().probeAll()
    }

    @Test
    fun `probeOneDatabase snapshot does not expose autoDisabled or inactiveSince`() {
        val c = db(active = true, consecutive = 0)
        whenever(dbRepo.findDatabaseConfigById(c.id)).thenReturn(c)
        whenever(dbProbe.probe(c)).thenReturn(ConnectionProbeResult.Failure("nope"))
        whenever(dbRepo.saveDatabaseConfig(any())).thenAnswer { it.arguments[0] }
        val snap = newService().probeOne(ConnectionKind.DATABASE, c.id)!!
        assertFalse(hasProperty(snap, "autoDisabled"), "autoDisabled must not be on the snapshot")
        assertFalse(hasProperty(snap, "inactiveSince"), "inactiveSince must not be on the snapshot")
    }

    @Test
    fun `failed probe keeps the user-controlled active flag intact on the snapshot`() {
        val c = db(active = false, consecutive = 3)
        whenever(dbRepo.findDatabaseConfigById(c.id)).thenReturn(c)
        whenever(dbProbe.probe(c)).thenReturn(ConnectionProbeResult.Failure("down"))
        whenever(dbRepo.saveDatabaseConfig(any())).thenAnswer { it.arguments[0] }
        val snap = newService().probeOne(ConnectionKind.DATABASE, c.id)!!
        assertEquals(false, snap.active, "user-disabled stays user-disabled")
        assertEquals(false, snap.healthy)
        assertEquals(4, snap.consecutiveFailures)
    }

    private fun db(
        active: Boolean,
        consecutive: Int = 0,
        lastErrorAt: java.time.LocalDateTime? = null,
        lastErrorMessage: String? = null,
    ): DatabaseConfig {
        val c =
            DatabaseConfig(
                id = 1L,
                name = "db-$consecutive",
                dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "x",
                username = "u",
                password = "p",
            )
        c.active = active
        c.consecutiveFailures = consecutive
        c.lastErrorAt = lastErrorAt
        c.lastErrorMessage = lastErrorMessage
        return c
    }

    private fun jira(active: Boolean): JiraConfig {
        val c = JiraConfig(id = 1L, name = "j", baseUrl = "https://jira", projectKey = "X", apiToken = "t")
        c.active = active
        return c
    }

    private fun confluence(active: Boolean): ConfluenceConfig {
        val c = ConfluenceConfig(id = 1L, name = "c", baseUrl = "https://cf", username = "u", apiToken = "t")
        c.active = active
        return c
    }

    private fun gitlab(active: Boolean): GitlabConfig {
        val c = GitlabConfig(id = 1L, name = "g", baseUrl = "https://gl", accessToken = "t")
        c.active = active
        return c
    }

    private fun github(active: Boolean): GithubRepoConfig {
        val c = GithubRepoConfig(id = 1L, name = "gh", owner = "me", repo = "repo")
        c.active = active
        return c
    }

    private fun github(id: Long): GithubRepoConfig {
        val c = GithubRepoConfig(id = id, name = "gh", owner = "me", repo = "repo")
        c.active = true
        return c
    }

    private fun grafana(active: Boolean): GrafanaConfig {
        val c = GrafanaConfig(id = 1L, name = "gr", baseUrl = "https://gr", username = "u", password = "p")
        c.active = active
        return c
    }

    private fun mcp(active: Boolean): McpServerConfig {
        val c = McpServerConfig(id = 1L, name = "m", baseUrl = "http://localhost:9999")
        c.active = active
        return c
    }

    private fun hasProperty(
        target: Any,
        propertyName: String,
    ): Boolean {
        val beanInfo = java.beans.Introspector.getBeanInfo(target.javaClass)
        return beanInfo.propertyDescriptors.any { it.name == propertyName }
    }
}
