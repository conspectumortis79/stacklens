package com.stacklens.application.service.config

import com.stacklens.adapter.out.demo.DemoGrafanaEndpointResolver
import com.stacklens.adapter.out.demo.DemoHostResolver
import com.stacklens.adapter.out.demo.DemoRuntimeMode
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DemoUrlAlignmentServiceTest {
    private val jiraRepo: JiraConfigPort = mock()
    private val gitlabRepo: GitlabConfigPort = mock()
    private val confluenceRepo: ConfluenceConfigPort = mock()
    private val grafanaRepo: GrafanaConfigPort = mock()
    private val githubRepo: GithubRepoConfigPort = mock()
    private val databaseRepo: DatabaseConfigPort = mock()
    private val mcpRepo: McpServerConfigPort = mock()

    private var previousDockerFlag: Boolean = false

    private val service =
        DemoUrlAlignmentService(
            listOf(
                JiraDemoUrlAlignment(jiraRepo, DemoUrlAligner(DemoHostResolver())),
                GitlabDemoUrlAlignment(gitlabRepo, DemoUrlAligner(DemoHostResolver())),
                ConfluenceDemoUrlAlignment(confluenceRepo, DemoUrlAligner(DemoHostResolver())),
                GrafanaDemoUrlAlignment(
                    grafanaRepo,
                    DemoUrlAligner(DemoHostResolver()),
                    DemoGrafanaEndpointResolver(DemoHostResolver()) { null },
                ),
                GithubRepoDemoUrlAlignment(githubRepo, DemoUrlAligner(DemoHostResolver())),
                DatabaseDemoUrlAlignment(databaseRepo, DemoUrlAligner(DemoHostResolver())),
                McpDemoUrlAlignment(
                    mcpRepo,
                    DemoUrlAligner(DemoHostResolver()),
                    DemoGrafanaEndpointResolver(DemoHostResolver()) { null },
                ),
            ),
        )

    @BeforeEach
    fun captureAmbientDockerFlag() {
        previousDockerFlag = java.lang.Boolean.getBoolean(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
    }

    @AfterEach
    fun tearDown() {
        if (previousDockerFlag) {
            System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "true")
        } else {
            System.clearProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
        }
    }

    private fun demoGitlab(baseUrl: String) = GitlabConfig(id = 2, name = "Demo GitLab", baseUrl = baseUrl, accessToken = "demo", active = true)

    private fun demoJira(baseUrl: String) =
        JiraConfig(
            id = 1,
            name = "Demo Jira",
            baseUrl = baseUrl,
            username = "demo",
            apiToken = "demo",
            active = true,
        )

    private fun demoMcp(baseUrl: String): McpServerConfig =
        McpServerConfig(
            id = 5,
            name = "Demo MCP Server",
            baseUrl = baseUrl,
            active = true,
            jiraBaseUrl = "http://localhost:8081",
            confluenceBaseUrl = "http://localhost:8081",
            gitlabBaseUrl = "http://localhost:3080",
            githubApiBase = "http://localhost:8082",
            grafanaBaseUrl = "http://localhost:3000",
        )

    private fun stubAll(
        jira: List<JiraConfig> = emptyList(),
        gitlab: List<GitlabConfig> = emptyList(),
        confluence: List<ConfluenceConfig> = emptyList(),
        grafana: List<GrafanaConfig> = emptyList(),
        github: List<GithubRepoConfig> = emptyList(),
        databases: List<DatabaseConfig> = emptyList(),
        mcp: List<McpServerConfig> = emptyList(),
    ) {
        whenever(jiraRepo.findAllJiraConfigs()).doReturn(jira)
        whenever(gitlabRepo.findAllGitlabConfigs()).doReturn(gitlab)
        whenever(confluenceRepo.findAllConfluenceConfigs()).doReturn(confluence)
        whenever(grafanaRepo.findAllGrafanaConfigs()).doReturn(grafana)
        whenever(githubRepo.findAllGithubRepoConfigs()).doReturn(github)
        whenever(databaseRepo.findAllDatabaseConfigs()).doReturn(databases)
        whenever(mcpRepo.findAllMcpServerConfigs()).doReturn(mcp)
        whenever(jiraRepo.saveJiraConfig(any())).thenAnswer { it.arguments[0] as JiraConfig }
        whenever(gitlabRepo.saveGitlabConfig(any())).thenAnswer { it.arguments[0] as GitlabConfig }
        whenever(confluenceRepo.saveConfluenceConfig(any())).thenAnswer { it.arguments[0] as ConfluenceConfig }
        whenever(grafanaRepo.saveGrafanaConfig(any())).thenAnswer { it.arguments[0] as GrafanaConfig }
        whenever(githubRepo.saveGithubRepoConfig(any())).thenAnswer { it.arguments[0] as GithubRepoConfig }
        whenever(databaseRepo.saveDatabaseConfig(any())).thenAnswer { it.arguments[0] as DatabaseConfig }
        whenever(mcpRepo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] as McpServerConfig }
    }

    @Test
    fun `rewrites localhost demo urls to host docker internal inside a container`() {
        val gitlab = demoGitlab("http://localhost:3080")
        val database =
            DatabaseConfig(
                id = 7,
                name = "Demo MySQL – ecommerce",
                dbType = DatabaseType.MYSQL,
                host = "localhost",
                port = 3306,
                databaseName = "ecommerce",
                username = "stacklens",
                password = "stacklens",
                active = true,
            )
        val github =
            GithubRepoConfig(
                id = 8,
                name = "Demo GitHub – backend-api",
                owner = "demo-org",
                repo = "backend-api",
                apiBase = "http://localhost:8082",
                active = true,
            )
        stubAll(
            jira = listOf(demoJira("http://localhost:8081")),
            gitlab = listOf(gitlab),
            github = listOf(github),
            databases = listOf(database),
            mcp = listOf(demoMcp("http://localhost:7456")),
        )

        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "true")
        val changes = service.alignAll()

        val gitlabCaptor = argumentCaptor<GitlabConfig>()
        verify(gitlabRepo, times(1)).saveGitlabConfig(gitlabCaptor.capture())
        assertEquals("http://host.docker.internal:3080", gitlabCaptor.firstValue.baseUrl)

        val databaseCaptor = argumentCaptor<DatabaseConfig>()
        verify(databaseRepo, times(1)).saveDatabaseConfig(databaseCaptor.capture())
        assertEquals("host.docker.internal", databaseCaptor.firstValue.host)

        val githubCaptor = argumentCaptor<GithubRepoConfig>()
        verify(githubRepo, times(1)).saveGithubRepoConfig(githubCaptor.capture())
        assertEquals("http://host.docker.internal:8082", githubCaptor.firstValue.apiBase)

        val mcpCaptor = argumentCaptor<McpServerConfig>()
        verify(mcpRepo, times(1)).saveMcpServerConfig(mcpCaptor.capture())
        assertEquals("http://stacklens-mcp:7456", mcpCaptor.firstValue.baseUrl)
        assertEquals("http://host.docker.internal:3080", mcpCaptor.firstValue.gitlabBaseUrl)

        assertTrue(changes.contains("GitLab:${gitlab.name}→http://host.docker.internal:3080"))
        assertTrue(changes.contains("Database:Demo MySQL – ecommerce→host.docker.internal"))
    }

    @Test
    fun `rewrites docker demo urls back to localhost when running natively`() {
        val gitlab = demoGitlab("http://host.docker.internal:3080")
        val mcp = demoMcp("http://stacklens-mcp:7456")
        stubAll(gitlab = listOf(gitlab), mcp = listOf(mcp))

        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "false")
        service.alignAll()

        val gitlabCaptor = argumentCaptor<GitlabConfig>()
        verify(gitlabRepo, times(1)).saveGitlabConfig(gitlabCaptor.capture())
        assertEquals("http://localhost:3080", gitlabCaptor.firstValue.baseUrl)

        val mcpCaptor = argumentCaptor<McpServerConfig>()
        verify(mcpRepo, times(1)).saveMcpServerConfig(mcpCaptor.capture())
        assertEquals("http://localhost:7456", mcpCaptor.firstValue.baseUrl)
        assertEquals("http://localhost:3080", mcpCaptor.firstValue.gitlabBaseUrl)
    }

    @Test
    fun `reports no changes when every url already matches the runtime`() {
        stubAll(
            gitlab = listOf(demoGitlab("http://localhost:3080")),
            mcp = listOf(demoMcp("http://localhost:7456")),
        )

        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "false")

        assertTrue(service.alignAll().isEmpty())
        verify(gitlabRepo, never()).saveGitlabConfig(any())
        verify(mcpRepo, never()).saveMcpServerConfig(any())
    }

    @Test
    fun `leaves user-customised hosts untouched in both directions`() {
        stubAll(
            gitlab = listOf(demoGitlab("https://gitlab.mycompany.com")),
            mcp =
                listOf(
                    McpServerConfig(
                        id = 6,
                        name = "Team MCP Server",
                        baseUrl = "http://my-mcp.lan:7456",
                        active = true,
                        jiraBaseUrl = "https://jira.mycompany.com",
                        confluenceBaseUrl = "https://wiki.mycompany.com",
                        gitlabBaseUrl = "https://gitlab.mycompany.com",
                        githubApiBase = "https://api.github.com",
                        grafanaBaseUrl = "https://grafana.mycompany.com",
                    ),
                ),
        )

        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "false")
        assertTrue(service.alignAll().isEmpty())
        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "true")
        assertTrue(service.alignAll().isEmpty())

        verify(gitlabRepo, never()).saveGitlabConfig(any())
        verify(mcpRepo, never()).saveMcpServerConfig(any())
    }

    @Test
    fun `never touches connections that are not part of the demo environment`() {
        stubAll(
            jira =
                listOf(
                    JiraConfig(
                        id = 11,
                        name = "Team Jira",
                        baseUrl = "http://localhost:8081",
                        username = "user",
                        apiToken = "token",
                        active = true,
                    ),
                ),
            gitlab = listOf(GitlabConfig(id = 3, name = "Production GitLab", baseUrl = "http://localhost:3080", accessToken = "t", active = true)),
            databases =
                listOf(
                    DatabaseConfig(
                        id = 4,
                        name = "Warehouse",
                        dbType = DatabaseType.POSTGRESQL,
                        host = "localhost",
                        port = 5432,
                        databaseName = "dwh",
                        username = "u",
                        password = "p",
                        active = true,
                    ),
                ),
        )

        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "true")
        assertTrue(service.alignAll().isEmpty())

        verify(gitlabRepo, never()).saveGitlabConfig(any())
        verify(databaseRepo, never()).saveDatabaseConfig(any())
    }

    @Test
    fun `skips entries without any url`() {
        stubAll(
            github = listOf(GithubRepoConfig(id = 9, name = "Demo GitHub – no base", owner = "o", repo = "r", apiBase = null)),
        )

        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "true")
        assertTrue(service.alignAll().isEmpty())
        verify(githubRepo, never()).saveGithubRepoConfig(any())
    }

    @Test
    fun `keeps aligning the remaining sections when one section fails`() {
        stubAll(
            jira = listOf(demoJira("http://localhost:8081")),
            mcp = listOf(demoMcp("http://localhost:7456")),
        )
        whenever(gitlabRepo.findAllGitlabConfigs()).thenThrow(RuntimeException("db down"))

        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "true")
        val changes = service.alignAll()

        verify(jiraRepo, times(1)).saveJiraConfig(any())
        verify(mcpRepo, times(1)).saveMcpServerConfig(any())
        assertTrue(changes.contains("Jira:Demo Jira→http://host.docker.internal:8081"))
    }
}
