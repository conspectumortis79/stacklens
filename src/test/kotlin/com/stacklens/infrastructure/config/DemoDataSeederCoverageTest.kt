package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.ConfluenceDemoDataSeeder
import com.stacklens.adapter.out.demo.DemoDataSeeder
import com.stacklens.adapter.out.demo.DemoDataSeederStrategy
import com.stacklens.adapter.out.demo.DemoGrafanaEndpointResolver
import com.stacklens.adapter.out.demo.DemoHostResolver
import com.stacklens.adapter.out.demo.GithubDemoDataSeeder
import com.stacklens.adapter.out.demo.GitlabDemoDataSeeder
import com.stacklens.adapter.out.demo.GrafanaDemoDataSeeder
import com.stacklens.adapter.out.demo.JiraDemoDataSeeder
import com.stacklens.adapter.out.demo.McpDemoDataSeeder
import com.stacklens.adapter.out.demo.MysqlDemoDataSeeder
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.platform.DemoRuntimeHostPort
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DemoDataSeederCoverageTest {
    private val hostResolver = mock<DemoRuntimeHostPort>()

    private fun identityResolver() {
        whenever(hostResolver.alignedServiceUrl(any())).thenAnswer { invocation -> invocation.arguments[0] }
        whenever(hostResolver.alignedMcpServerUrl(any())).thenAnswer { invocation -> invocation.arguments[0] }
        whenever(hostResolver.serviceHost()).thenReturn(DemoHostResolver.LOCAL_HOST)
        whenever(hostResolver.mcpServiceBaseUrl()).thenReturn("http://localhost:7456")
    }

    @Test
    fun `reseeding runs every registered strategy in order`() {
        val seeded = mutableListOf<String>()
        val strategies =
            listOf(
                object : DemoDataSeederStrategy {
                    override fun seed() {
                        seeded += "one"
                    }
                },
                object : DemoDataSeederStrategy {
                    override fun seed() {
                        seeded += "two"
                    }
                },
            )
        val seeder = DemoDataSeeder(strategies)

        seeder.reseed()

        assertEquals(listOf("one", "two"), seeded)
    }

    @Test
    fun `an unrelated confluence config does not stop the demo entry from being created`() {
        identityResolver()
        val repo = mock<ConfluenceConfigPort>()
        whenever(repo.findAllConfluenceConfigs()).thenReturn(
            listOf(ConfluenceConfig(name = "Custom Wiki", baseUrl = "https://wiki.example", username = "u", apiToken = "t")),
        )

        ConfluenceDemoDataSeeder(repo, hostResolver).seed()

        verify(repo).saveConfluenceConfig(argThat { name == "Demo Confluence" })
    }

    @Test
    fun `an existing confluence demo entry is refreshed in place`() {
        whenever(hostResolver.alignedServiceUrl(any())).thenReturn("http://moved:8081")
        val existing = ConfluenceConfig(name = "Demo Confluence", baseUrl = "http://old:8081", username = "u", apiToken = "t")
        val repo = mock<ConfluenceConfigPort>()
        whenever(repo.findAllConfluenceConfigs()).thenReturn(listOf(existing))

        ConfluenceDemoDataSeeder(repo, hostResolver).seed()

        verify(repo).saveConfluenceConfig(existing)
    }

    @Test
    fun `an unrelated gitlab config does not stop the demo entry from being created`() {
        identityResolver()
        val repo = mock<GitlabConfigPort>()
        whenever(repo.findAllGitlabConfigs()).thenReturn(
            listOf(GitlabConfig(name = "Custom GitLab", baseUrl = "https://gitlab.example", accessToken = "t")),
        )

        GitlabDemoDataSeeder(repo, hostResolver).seed()

        verify(repo).saveGitlabConfig(argThat { name == "Demo GitLab" })
    }

    @Test
    fun `an existing gitlab demo entry keeps its url when nothing changes`() {
        identityResolver()
        val existing = GitlabConfig(name = "Demo GitLab", baseUrl = "http://localhost:3080", accessToken = "t")
        val repo = mock<GitlabConfigPort>()
        whenever(repo.findAllGitlabConfigs()).thenReturn(listOf(existing))

        GitlabDemoDataSeeder(repo, hostResolver).seed()

        verify(repo, never()).saveGitlabConfig(any())
    }

    @Test
    fun `an unrelated grafana config does not stop the demo entry from being created`() {
        identityResolver()
        val repo = mock<GrafanaConfigPort>()
        whenever(repo.findAllGrafanaConfigs()).thenReturn(
            listOf(GrafanaConfig(name = "Custom Grafana", baseUrl = "https://grafana.example", username = "u", password = "p")),
        )

        GrafanaDemoDataSeeder(repo, DemoGrafanaEndpointResolver(hostResolver) { null }).seed()

        verify(repo).saveGrafanaConfig(argThat { name == "Demo Grafana" })
    }

    @Test
    fun `an existing grafana demo entry keeps its url when nothing changes`() {
        identityResolver()
        val existing = GrafanaConfig(name = "Demo Grafana", baseUrl = "http://localhost:3000", username = "u", password = "p")
        val repo = mock<GrafanaConfigPort>()
        whenever(repo.findAllGrafanaConfigs()).thenReturn(listOf(existing))

        GrafanaDemoDataSeeder(repo, DemoGrafanaEndpointResolver(hostResolver) { null }).seed()

        verify(repo, never()).saveGrafanaConfig(any())
    }

    @Test
    fun `an existing grafana demo entry is moved to the configured demo port`() {
        identityResolver()
        val existing = GrafanaConfig(name = "Demo Grafana", baseUrl = "http://localhost:3000", username = "u", password = "p")
        val repo = mock<GrafanaConfigPort>()
        whenever(repo.findAllGrafanaConfigs()).thenReturn(listOf(existing))

        GrafanaDemoDataSeeder(repo, DemoGrafanaEndpointResolver(hostResolver) { "3001" }).seed()

        assertEquals("http://localhost:3001", existing.baseUrl)
        verify(repo).saveGrafanaConfig(existing)
    }

    @Test
    fun `a missing grafana demo entry is created on the configured demo port`() {
        identityResolver()
        val repo = mock<GrafanaConfigPort>()
        val created = GrafanaConfig(name = "Demo Grafana", baseUrl = "http://localhost:3001", username = "u", password = "p")
        whenever(repo.findAllGrafanaConfigs()).thenReturn(emptyList(), listOf(created))

        GrafanaDemoDataSeeder(repo, DemoGrafanaEndpointResolver(hostResolver) { "3001" }).seed()

        verify(repo).saveGrafanaConfig(argThat { baseUrl == "http://localhost:3001" })
    }

    @Test
    fun `an unrelated jira config does not stop the demo entry from being created`() {
        identityResolver()
        val repo = mock<JiraConfigPort>()
        whenever(repo.findAllJiraConfigs()).thenReturn(
            listOf(JiraConfig(name = "Custom Jira", baseUrl = "https://jira.example", username = "u", apiToken = "t")),
        )

        JiraDemoDataSeeder(repo, hostResolver).seed()

        verify(repo).saveJiraConfig(argThat { name == "Demo Jira" })
    }

    @Test
    fun `an existing jira demo entry keeps its url when nothing changes`() {
        identityResolver()
        val existing = JiraConfig(name = "Demo Jira", baseUrl = "http://localhost:8081", username = "u", apiToken = "t")
        val repo = mock<JiraConfigPort>()
        whenever(repo.findAllJiraConfigs()).thenReturn(listOf(existing))

        JiraDemoDataSeeder(repo, hostResolver).seed()

        verify(repo, never()).saveJiraConfig(any())
    }

    @Test
    fun `the mcp seeder leaves a fully aligned demo config alone`() {
        identityResolver()
        val repo = mock<McpServerConfigPort>()
        val aligned = demoMcp("http://localhost:7456")
        whenever(repo.findAllMcpServerConfigs()).thenReturn(listOf(aligned))

        McpDemoDataSeeder(repo, hostResolver).seed()

        verify(repo, never()).saveMcpServerConfig(any())
        assertEquals("http://localhost:7456", aligned.baseUrl)
    }

    @Test
    fun `the mcp seeder refreshes the first stale url it finds`() {
        identityResolver()
        whenever(hostResolver.alignedMcpServerUrl("http://stacklens-mcp:7456")).thenReturn("http://localhost:7456")
        val repo = mock<McpServerConfigPort>()
        val stale = demoMcp("http://stacklens-mcp:7456")
        whenever(repo.findAllMcpServerConfigs()).thenReturn(listOf(stale))

        McpDemoDataSeeder(repo, hostResolver).seed()

        assertEquals("http://localhost:7456", stale.baseUrl)
        verify(repo).saveMcpServerConfig(stale)
    }

    @Test
    fun `the mcp seeder refreshes stale per service urls one by one`() {
        listOf("jira", "confluence", "gitlab", "github", "grafana").forEach { field ->
            identityResolver()
            val repo = mock<McpServerConfigPort>()
            val config = demoMcp("http://localhost:7456")
            val staleValue = "http://stacklens-$field:9999"
            when (field) {
                "jira" -> config.jiraBaseUrl = staleValue
                "confluence" -> config.confluenceBaseUrl = staleValue
                "gitlab" -> config.gitlabBaseUrl = staleValue
                "github" -> config.githubApiBase = staleValue
                else -> config.grafanaBaseUrl = staleValue
            }
            whenever(hostResolver.alignedServiceUrl(staleValue)).thenReturn("http://localhost:9999")
            whenever(repo.findAllMcpServerConfigs()).thenReturn(listOf(config))

            McpDemoDataSeeder(repo, hostResolver).seed()

            verify(repo).saveMcpServerConfig(config)
        }
    }

    private fun demoMcp(baseUrl: String): McpServerConfig =
        McpServerConfig(
            name = "StackLens MCP",
            baseUrl = baseUrl,
            jiraBaseUrl = "http://localhost:8081",
            confluenceBaseUrl = "http://localhost:8090",
            gitlabBaseUrl = "http://localhost:3080",
            githubApiBase = "http://localhost:3001",
            grafanaBaseUrl = "http://localhost:3000",
        )

    @Test
    fun `property defaults come from the shared companion constants`() {
        val properties = OpenAiCompatibleProperties()

        assertEquals(OpenAiCompatibleProperties.DEFAULT_BASE_URL, properties.defaultBaseUrl)
        assertEquals(OpenAiCompatibleProperties.DEFAULT_API_KEY_ENV_VARS, properties.apiKeyEnvVars)
        assertTrue(properties.httpVersion.name.isNotEmpty())
    }

    @Test
    fun `the github seeder refreshes a stale demo api base`() {
        identityResolver()
        whenever(hostResolver.alignedServiceUrl("http://old:8082")).thenReturn("http://localhost:8082")
        val repo = mock<com.stacklens.application.port.out.GithubRepoConfigPort>()
        val stale =
            com.stacklens.domain.model.GithubRepoConfig(
                name = "Demo GitHub – backend-api",
                owner = "demo-org",
                repo = "backend-api",
                apiBase = "http://old:8082",
            )
        whenever(repo.findAllGithubRepoConfigs()).thenReturn(listOf(stale))

        GithubDemoDataSeeder(repo, hostResolver).seed()

        assertEquals("http://localhost:8082", stale.apiBase)
        verify(repo, org.mockito.kotlin.atLeastOnce()).saveGithubRepoConfig(stale)
    }

    @Test
    fun `the github seeder leaves an api base without a value alone`() {
        identityResolver()
        val repo = mock<com.stacklens.application.port.out.GithubRepoConfigPort>()
        val withoutBase =
            com.stacklens.domain.model.GithubRepoConfig(
                name = "Demo GitHub – frontend-app",
                owner = "demo-org",
                repo = "frontend-app",
                apiBase = null,
            )
        whenever(repo.findAllGithubRepoConfigs()).thenReturn(listOf(withoutBase))

        GithubDemoDataSeeder(repo, hostResolver).seed()

        assertEquals(null, withoutBase.apiBase)
        verify(repo, never()).saveGithubRepoConfig(withoutBase)
    }

    @Test
    fun `the mysql seeder moves a demo database to the current host`() {
        identityResolver()
        val repo = mock<com.stacklens.application.port.out.DatabaseConfigPort>()
        val stale =
            com.stacklens.domain.model.DatabaseConfig(
                name = "Demo MySQL – ecommerce",
                dbType = com.stacklens.domain.enums.DatabaseType.MYSQL,
                host = "another-host",
                port = 3306,
                databaseName = "ecommerce",
                username = "stacklens",
                password = "stacklens",
            )
        whenever(repo.findAllDatabaseConfigs()).thenReturn(listOf(stale))

        MysqlDemoDataSeeder(repo, hostResolver).seed()

        assertEquals(DemoHostResolver.LOCAL_HOST, stale.host)
        verify(repo).saveDatabaseConfig(stale)
    }

    @Test
    fun `the mysql seeder keeps a demo database on the current host`() {
        identityResolver()
        val repo = mock<com.stacklens.application.port.out.DatabaseConfigPort>()
        val current =
            com.stacklens.domain.model.DatabaseConfig(
                name = "Demo MySQL – auth_db",
                dbType = com.stacklens.domain.enums.DatabaseType.MYSQL,
                host = DemoHostResolver.LOCAL_HOST,
                port = 3306,
                databaseName = "auth_db",
                username = "stacklens",
                password = "stacklens",
            )
        whenever(repo.findAllDatabaseConfigs()).thenReturn(listOf(current))

        MysqlDemoDataSeeder(repo, hostResolver).seed()

        assertEquals(DemoHostResolver.LOCAL_HOST, current.host)
        verify(repo, never()).saveDatabaseConfig(current)
    }
}
