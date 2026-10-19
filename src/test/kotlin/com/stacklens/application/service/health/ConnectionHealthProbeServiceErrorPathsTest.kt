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
import com.stacklens.application.port.out.health.DatabaseConnectionProbePort
import com.stacklens.application.port.out.health.GithubConnectionProbePort
import com.stacklens.application.port.out.health.GitlabConnectionProbePort
import com.stacklens.application.port.out.health.GrafanaConnectionProbePort
import com.stacklens.application.port.out.health.JiraConnectionProbePort
import com.stacklens.application.port.out.health.McpConnectionProbePort
import com.stacklens.domain.enums.ConnectionKind
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.ConnectionEntity
import com.stacklens.domain.model.ConnectionHealthSnapshot
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.testsupport.ImmediateStartupTaskExecutor
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.Executors

class ConnectionHealthProbeServiceErrorPathsTest {
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
    fun `probeOne jira swallows probe exceptions`() {
        val c = JiraConfig(name = "j", baseUrl = "https://jira", apiToken = "t")
        whenever(jiraRepo.findJiraConfigById(c.id)).thenReturn(c)
        whenever(jiraProbe.probe(c)).thenThrow(RuntimeException("boom"))
        assertNull(newService().probeOne(ConnectionKind.JIRA, c.id))
    }

    @Test
    fun `probeOne confluence swallows probe exceptions`() {
        val c = ConfluenceConfig(name = "c", baseUrl = "u", username = "u", apiToken = "t")
        whenever(confluenceRepo.findConfluenceConfigById(c.id)).thenReturn(c)
        whenever(confluenceProbe.probe(c)).thenThrow(RuntimeException("boom"))
        assertNull(newService().probeOne(ConnectionKind.CONFLUENCE, c.id))
    }

    @Test
    fun `probeOne gitlab swallows probe exceptions`() {
        val c = GitlabConfig(name = "g", baseUrl = "u", accessToken = "t")
        whenever(gitlabRepo.findGitlabConfigById(c.id)).thenReturn(c)
        whenever(gitlabProbe.probe(c)).thenThrow(RuntimeException("boom"))
        assertNull(newService().probeOne(ConnectionKind.GITLAB, c.id))
    }

    @Test
    fun `probeOne grafana swallows probe exceptions`() {
        val c = GrafanaConfig(name = "g", baseUrl = "u", username = "u", password = "p")
        whenever(grafanaRepo.findGrafanaConfigById(c.id)).thenReturn(c)
        whenever(grafanaProbe.probe(c)).thenThrow(RuntimeException("boom"))
        assertNull(newService().probeOne(ConnectionKind.GRAFANA, c.id))
    }

    @Test
    fun `probeOne mcp swallows probe exceptions`() {
        val c = McpServerConfig(name = "m", baseUrl = "u")
        whenever(mcpRepo.findMcpServerConfigById(c.id)).thenReturn(c)
        whenever(mcpProbe.probe(c)).thenThrow(RuntimeException("boom"))
        assertNull(newService().probeOne(ConnectionKind.MCP, c.id))
    }

    @Test
    fun `probeOne github swallows probe exceptions`() {
        val c = GithubRepoConfig(name = "g", owner = "o", repo = "r", accessToken = "t")
        whenever(githubRepo.findGithubRepoConfigById(c.id)).thenReturn(c)
        whenever(githubProbe.probe("o", "r", "t", "https://api.github.com")).thenThrow(RuntimeException("boom"))
        assertNull(newService().probeOne(ConnectionKind.GITHUB, c.id))
    }

    @Test
    fun `probeOne unknown kind returns null`() {
        assertNull(newService().probeOne(ConnectionKind.DATABASE, 1L))
    }

    @Test
    fun `probeAll with failing adapter completes without rethrowing`() {
        val service =
            ConnectionHealthProbeService(
                adapters = listOf(BoomAdapter()),
                probeExecutor = Executors.newSingleThreadExecutor(),
                startupTasks = ImmediateStartupTaskExecutor,
            )
        service.probeAll()
        assertTrue(true)
    }

    private class BoomAdapter : ConnectionProbeAdapter<ConnectionEntity> {
        override val kind: ConnectionKind = ConnectionKind.JIRA

        override fun probeOne(id: Long): ConnectionHealthSnapshot? = throw RuntimeException("abort")

        override fun probeAll(): Unit = throw RuntimeException("abort")
    }
}
