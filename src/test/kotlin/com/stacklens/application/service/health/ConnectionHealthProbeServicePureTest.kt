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
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ConnectionHealthProbeServicePureTest {
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
    fun `first failed probe sets counter to 1`() {
        val c = db(active = true, consecutive = 0)
        dbWillReturn(failure("no connection"))
        captureOneDatabase(c)
        val captor = dbCaptor()
        assertEquals(1, captor.firstValue.consecutiveFailures)
        assertEquals(true, captor.firstValue.active)
    }

    @Test
    fun `second failed probe bumps counter to 2 and leaves active alone`() {
        val c = db(active = true, consecutive = 1)
        dbWillReturn(failure("still failing"))
        captureOneDatabase(c)
        val captor = dbCaptor()
        assertEquals(2, captor.firstValue.consecutiveFailures)
        assertEquals(true, captor.firstValue.active)
    }

    @Test
    fun `tenth failed probe still leaves active alone - no auto-disable, ever`() {
        val c = db(active = true, consecutive = 9)
        dbWillReturn(failure("still failing"))
        captureOneDatabase(c)
        val captor = dbCaptor()
        assertEquals(10, captor.firstValue.consecutiveFailures)
        assertEquals(true, captor.firstValue.active)
    }

    @Test
    fun `successful probe resets counter and clears last error fields`() {
        val c = db(active = true, consecutive = 1, lastErrorAt = java.time.LocalDateTime.now(), lastErrorMessage = "nope")
        dbWillReturn(ConnectionProbeResult.Ok())
        captureOneDatabase(c)
        val captor = dbCaptor()
        assertEquals(0, captor.firstValue.consecutiveFailures)
        assertNull(captor.firstValue.lastErrorAt)
        assertNull(captor.firstValue.lastErrorMessage)
        assertEquals(true, captor.firstValue.active)
    }

    @Test
    fun `failed probe on a user-disabled row leaves active=false and only records the error`() {
        val c = db(active = false, consecutive = 5, lastErrorMessage = "yesterday")
        dbWillReturn(failure("still down"))
        captureOneDatabase(c)
        val captor = dbCaptor()
        assertEquals(false, captor.firstValue.active)
        assertEquals(6, captor.firstValue.consecutiveFailures)
        assertEquals("still down", captor.firstValue.lastErrorMessage)
    }

    @Test
    fun `HEADLINE - the probe NEVER flips the user-controlled active flag`() {
        val original = db(active = true, consecutive = 0)
        dbWillReturn(failure("down"))
        whenever(dbRepo.findDatabaseConfigById(original.id)).thenReturn(original)
        whenever(dbRepo.saveDatabaseConfig(any())).thenAnswer { it.arguments[0] }
        val svc = newService()
        svc.probeOne(ConnectionKind.DATABASE, original.id)
        svc.probeOne(ConnectionKind.DATABASE, original.id)
        svc.probeOne(ConnectionKind.DATABASE, original.id)
        svc.probeOne(ConnectionKind.DATABASE, original.id)
        val cap = argumentCaptor<DatabaseConfig>()
        verify(dbRepo, times(4)).saveDatabaseConfig(cap.capture())
        val saved = cap.lastValue
        assertEquals(true, saved.active, "active must NEVER be flipped by the probe service")
        assertEquals(4, saved.consecutiveFailures, "counter is diagnostic only")
    }

    @Test
    fun `probeAll with empty database is a no-op`() {
        whenever(dbRepo.findAllDatabaseConfigs()).thenReturn(emptyList())
        whenever(jiraRepo.findAllJiraConfigs()).thenReturn(emptyList())
        whenever(confluenceRepo.findAllConfluenceConfigs()).thenReturn(emptyList())
        whenever(gitlabRepo.findAllGitlabConfigs()).thenReturn(emptyList())
        whenever(githubRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(grafanaRepo.findAllGrafanaConfigs()).thenReturn(emptyList())
        whenever(mcpRepo.findAllMcpServerConfigs()).thenReturn(emptyList())
        val svc = newService()
        svc.probeAll()
        assertTrue(tracker.snapshotAll().isEmpty(), "empty DB → no snapshots")
    }

    @Test
    fun `app-startup resilience - probeAll swallows exceptions per row`() {
        val a = db(active = true)
        val b = db(active = true)
        whenever(dbRepo.findAllDatabaseConfigs()).thenReturn(listOf(a, b))
        whenever(dbRepo.findDatabaseConfigById(a.id)).thenReturn(a)
        whenever(dbRepo.findDatabaseConfigById(b.id)).thenReturn(b)
        whenever(dbProbe.probe(a)).thenThrow(RuntimeException("boom"))
        whenever(dbProbe.probe(b)).thenReturn(ConnectionProbeResult.Ok())
        whenever(dbRepo.saveDatabaseConfig(any())).thenAnswer { it.arguments[0] }
        val svc = newService()
        svc.probeAll()
        val all = tracker.snapshotAll()
        assertNotNull(all["database:${b.id}"])
    }

    @Test
    fun `jira counter accumulates over many failures but never flips active`() {
        val c = jira(active = true)
        whenever(jiraRepo.findJiraConfigById(c.id)).thenReturn(c)
        whenever(jiraRepo.saveJiraConfig(any())).thenAnswer { it.arguments[0] }
        whenever(jiraProbe.probe(c)).thenReturn(failure("401"))
        val svc = newService()
        repeat(5) { svc.probeOne(ConnectionKind.JIRA, c.id) }
        val cap = argumentCaptor<JiraConfig>()
        verify(jiraRepo, times(5)).saveJiraConfig(cap.capture())
        val saved = cap.lastValue
        assertEquals(true, saved.active)
        assertEquals(5, saved.consecutiveFailures)
    }

    @Test
    fun `confluence counter accumulates but never flips active`() {
        val c = confluence(active = true)
        whenever(confluenceRepo.findConfluenceConfigById(c.id)).thenReturn(c)
        whenever(confluenceRepo.saveConfluenceConfig(any())).thenAnswer { it.arguments[0] }
        whenever(confluenceProbe.probe(c)).thenReturn(failure("ssl"))
        val svc = newService()
        repeat(5) { svc.probeOne(ConnectionKind.CONFLUENCE, c.id) }
        val cap = argumentCaptor<ConfluenceConfig>()
        verify(confluenceRepo, times(5)).saveConfluenceConfig(cap.capture())
        val saved = cap.lastValue
        assertEquals(true, saved.active)
        assertEquals(5, saved.consecutiveFailures)
    }

    @Test
    fun `gitlab counter accumulates but never flips active`() {
        val c = gitlab(active = true)
        whenever(gitlabRepo.findGitlabConfigById(c.id)).thenReturn(c)
        whenever(gitlabRepo.saveGitlabConfig(any())).thenAnswer { it.arguments[0] }
        whenever(gitlabProbe.probe(c)).thenReturn(failure("502"))
        val svc = newService()
        repeat(5) { svc.probeOne(ConnectionKind.GITLAB, c.id) }
        val cap = argumentCaptor<GitlabConfig>()
        verify(gitlabRepo, times(5)).saveGitlabConfig(cap.capture())
        val saved = cap.lastValue
        assertEquals(true, saved.active)
        assertEquals(5, saved.consecutiveFailures)
    }

    @Test
    fun `github counter accumulates but never flips active`() {
        val c = github(active = true)
        whenever(githubRepo.findGithubRepoConfigById(c.id)).thenReturn(c)
        whenever(githubRepo.saveGithubRepoConfig(any())).thenAnswer { it.arguments[0] }
        whenever(aiRepo.findAiConfig()).thenReturn(null)
        whenever(githubProbe.probe(any(), any(), anyOrNull(), any())).thenReturn(failure("403"))
        val svc = newService()
        repeat(5) { svc.probeOne(ConnectionKind.GITHUB, c.id) }
        val cap = argumentCaptor<GithubRepoConfig>()
        verify(githubRepo, times(5)).saveGithubRepoConfig(cap.capture())
        val saved = cap.lastValue
        assertEquals(true, saved.active)
        assertEquals(5, saved.consecutiveFailures)
    }

    @Test
    fun `grafana counter accumulates but never flips active`() {
        val c = grafana(active = true)
        whenever(grafanaRepo.findGrafanaConfigById(c.id)).thenReturn(c)
        whenever(grafanaRepo.saveGrafanaConfig(any())).thenAnswer { it.arguments[0] }
        whenever(grafanaProbe.probe(c)).thenReturn(failure("500"))
        val svc = newService()
        repeat(5) { svc.probeOne(ConnectionKind.GRAFANA, c.id) }
        val cap = argumentCaptor<GrafanaConfig>()
        verify(grafanaRepo, times(5)).saveGrafanaConfig(cap.capture())
        val saved = cap.lastValue
        assertEquals(true, saved.active)
        assertEquals(5, saved.consecutiveFailures)
    }

    @Test
    fun `mcp counter accumulates but never flips active`() {
        val c = mcp(active = true)
        whenever(mcpRepo.findMcpServerConfigById(c.id)).thenReturn(c)
        whenever(mcpRepo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] }
        whenever(mcpProbe.probe(c)).thenReturn(failure("connection refused"))
        val svc = newService()
        repeat(5) { svc.probeOne(ConnectionKind.MCP, c.id) }
        val cap = argumentCaptor<McpServerConfig>()
        verify(mcpRepo, times(5)).saveMcpServerConfig(cap.capture())
        val saved = cap.lastValue
        assertEquals(true, saved.active)
        assertEquals(5, saved.consecutiveFailures)
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

    private fun dbWillReturn(r: ConnectionProbeResult) {
        whenever(dbProbe.probe(any())).thenReturn(r)
    }

    private fun dbCaptor(): org.mockito.kotlin.KArgumentCaptor<DatabaseConfig> {
        val cap = argumentCaptor<DatabaseConfig>()
        verify(dbRepo, times(1)).saveDatabaseConfig(cap.capture())
        return cap
    }

    private fun captureOneDatabase(c: DatabaseConfig) {
        whenever(dbRepo.findDatabaseConfigById(c.id)).thenReturn(c)
        whenever(dbRepo.saveDatabaseConfig(any())).thenAnswer { it.arguments[0] }
        newService().probeOne(ConnectionKind.DATABASE, c.id)
    }

    private fun failure(msg: String): ConnectionProbeResult = ConnectionProbeResult.Failure(msg)
}
