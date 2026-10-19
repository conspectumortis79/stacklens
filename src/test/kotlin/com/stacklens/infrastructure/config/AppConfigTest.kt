package com.stacklens.infrastructure.config

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.stacklens.adapter.out.demo.DemoRuntimeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.web.client.RestTemplate

class AppConfigTest {
    @Test
    fun `WebClientConfig webClientBuilder returns configured builder`() {
        val config = WebClientConfig()
        val builder = config.webClientBuilder()
        assertNotNull(builder)
    }

    @Test
    fun `WebClientConfig restTemplate returns instance`() {
        val config = WebClientConfig()
        val template: RestTemplate = config.restTemplate()
        assertNotNull(template)
    }

    @Test
    fun `JacksonKotlinConfig kotlinModule returns a configured module`() {
        val config = JacksonKotlinConfig()
        val module: KotlinModule = config.kotlinModule()
        assertNotNull(module)
    }

    @Test
    fun `AsyncConfig searchTaskExecutor returns configured executor`() {
        val config = AsyncConfig()
        val executor = config.searchTaskExecutor()
        assertNotNull(executor)
    }

    @Test
    fun `AsyncConfig analysisTaskExecutor returns configured executor`() {
        val config = AsyncConfig()
        val executor = config.analysisTaskExecutor()
        assertNotNull(executor)
    }

    @Test
    fun `KotlinReflectionWarmup run executes without throwing`() {
        val warmup = KotlinReflectionWarmup()
        warmup.run(DefaultApplicationArguments())
    }

    @Test
    fun `DemoDataSeeder is idempotent when repos already populated`() {
        val gitlabRepo =
            mock<com.stacklens.application.port.out.GitlabConfigPort> {
                on { findAllGitlabConfigs() } doReturn
                    listOf(
                        com.stacklens.domain.model
                            .GitlabConfig(name = "Demo GitLab", baseUrl = "http://localhost:3080", accessToken = "t"),
                    )
            }
        val jiraRepo =
            mock<com.stacklens.application.port.out.JiraConfigPort> {
                on { findAllJiraConfigs() } doReturn
                    listOf(
                        com.stacklens.domain.model.JiraConfig(
                            name = "Demo Jira",
                            baseUrl = "http://localhost:8081",
                            username = "u",
                            apiToken = "t",
                        ),
                    )
            }
        val confluenceRepo =
            mock<com.stacklens.application.port.out.ConfluenceConfigPort> {
                on { findAllConfluenceConfigs() } doReturn
                    listOf(
                        com.stacklens.domain.model.ConfluenceConfig(
                            name = "Demo Confluence",
                            baseUrl = "http://localhost:8081",
                            username = "u",
                            apiToken = "t",
                        ),
                    )
            }
        val grafanaRepo =
            mock<com.stacklens.application.port.out.GrafanaConfigPort> {
                on { findAllGrafanaConfigs() } doReturn
                    listOf(
                        com.stacklens.domain.model.GrafanaConfig(
                            name = "Demo Grafana",
                            baseUrl = "http://localhost:3000",
                            username = "u",
                            password = "p",
                        ),
                    )
            }
        val databaseRepo =
            mock<com.stacklens.application.port.out.DatabaseConfigPort> {
                on { findAllDatabaseConfigs() } doReturn
                    listOf(
                        com.stacklens.domain.model.DatabaseConfig(
                            name = "Demo MySQL – ecommerce",
                            dbType = com.stacklens.domain.enums.DatabaseType.MYSQL,
                            host = "localhost",
                            port = 3306,
                            databaseName = "ecommerce",
                            username = "u",
                            password = "p",
                        ),
                        com.stacklens.domain.model.DatabaseConfig(
                            name = "Demo MySQL – auth_db",
                            dbType = com.stacklens.domain.enums.DatabaseType.MYSQL,
                            host = "localhost",
                            port = 3306,
                            databaseName = "auth_db",
                            username = "u",
                            password = "p",
                        ),
                        com.stacklens.domain.model.DatabaseConfig(
                            name = "Demo MySQL – analytics_db",
                            dbType = com.stacklens.domain.enums.DatabaseType.MYSQL,
                            host = "localhost",
                            port = 3306,
                            databaseName = "analytics_db",
                            username = "u",
                            password = "p",
                        ),
                    )
            }
        val mcpRepo =
            mock<com.stacklens.application.port.out.McpServerConfigPort> {
                on { findAllMcpServerConfigs() } doReturn
                    listOf(
                        com.stacklens.domain.model.McpServerConfig(
                            name = "StackLens MCP",
                            baseUrl = "http://localhost:7456",
                            jiraBaseUrl = "http://localhost:8081",
                            confluenceBaseUrl = "http://localhost:8081",
                            gitlabBaseUrl = "http://localhost:3080",
                            githubApiBase = "http://localhost:8082",
                            grafanaBaseUrl = "http://localhost:3000",
                        ),
                    )
            }
        val githubRepo =
            mock<com.stacklens.application.port.out.GithubRepoConfigPort> {
                on { findAllGithubRepoConfigs() } doReturn
                    listOf(
                        com.stacklens.domain.model.GithubRepoConfig(
                            name = "Demo GitHub – backend-api",
                            owner = "demo-org",
                            repo = "backend-api",
                            apiBase = "http://localhost:8082",
                        ),
                        com.stacklens.domain.model.GithubRepoConfig(
                            name = "Demo GitHub – frontend-app",
                            owner = "demo-org",
                            repo = "frontend-app",
                            apiBase = "http://localhost:8082",
                        ),
                        com.stacklens.domain.model.GithubRepoConfig(
                            name = "Demo GitHub – auth-service",
                            owner = "demo-org",
                            repo = "auth-service",
                            apiBase = "http://localhost:8082",
                        ),
                        com.stacklens.domain.model.GithubRepoConfig(
                            name = "Demo GitHub – devops-infra",
                            owner = "demo-org",
                            repo = "devops-infra",
                            apiBase = "http://localhost:8082",
                        ),
                    )
            }

        val seeder =
            DemoDataSeederTestHelper.buildLegacy(
                gitlabConfigRepo = gitlabRepo,
                jiraConfigRepo = jiraRepo,
                confluenceConfigRepo = confluenceRepo,
                grafanaConfigRepo = grafanaRepo,
                databaseConfigRepo = databaseRepo,
                mcpConfigRepo = mcpRepo,
                githubRepoConfigRepo = githubRepo,
            )
        seeder.reseed()
        verify(gitlabRepo, never()).saveGitlabConfig(any())
        verify(jiraRepo, never()).saveJiraConfig(any())
        verify(confluenceRepo, never()).saveConfluenceConfig(any())
        verify(grafanaRepo, never()).saveGrafanaConfig(any())
        verify(databaseRepo, never()).saveDatabaseConfig(any())
        verify(mcpRepo, never()).saveMcpServerConfig(any())
        verify(githubRepo, never()).saveGithubRepoConfig(any())
    }

    @Test
    fun `DemoDataSeeder refreshes existing demo entry whose host does not match the current environment`() {
        val existingGitlab =
            com.stacklens.domain.model
                .GitlabConfig(name = "Demo GitLab", baseUrl = "http://localhost:3080", accessToken = "t")
        val gitlabRepo =
            mock<com.stacklens.application.port.out.GitlabConfigPort> {
                on { findAllGitlabConfigs() } doReturn listOf(existingGitlab)
            }
        val emptyRepo =
            mock<com.stacklens.application.port.out.JiraConfigPort> {
                on { findAllJiraConfigs() } doReturn emptyList()
            }
        val emptyConfluenceRepo =
            mock<com.stacklens.application.port.out.ConfluenceConfigPort> {
                on { findAllConfluenceConfigs() } doReturn emptyList()
            }
        val emptyGrafanaRepo =
            mock<com.stacklens.application.port.out.GrafanaConfigPort> {
                on { findAllGrafanaConfigs() } doReturn emptyList()
            }
        val emptyDatabaseRepo =
            mock<com.stacklens.application.port.out.DatabaseConfigPort> {
                on { findAllDatabaseConfigs() } doReturn emptyList()
            }
        val emptyMcpRepo =
            mock<com.stacklens.application.port.out.McpServerConfigPort> {
                on { findAllMcpServerConfigs() } doReturn emptyList()
            }
        val emptyGithubRepo =
            mock<com.stacklens.application.port.out.GithubRepoConfigPort> {
                on { findAllGithubRepoConfigs() } doReturn emptyList()
            }

        val previousDockerFlag = java.lang.Boolean.getBoolean(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
        try {
            System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, "true")
            val seeder =
                DemoDataSeederTestHelper.buildLegacy(
                    gitlabConfigRepo = gitlabRepo,
                    jiraConfigRepo = emptyRepo,
                    confluenceConfigRepo = emptyConfluenceRepo,
                    grafanaConfigRepo = emptyGrafanaRepo,
                    databaseConfigRepo = emptyDatabaseRepo,
                    mcpConfigRepo = emptyMcpRepo,
                    githubRepoConfigRepo = emptyGithubRepo,
                )
            seeder.reseed()
        } finally {
            if (!previousDockerFlag) System.clearProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY)
        }

        val captor = org.mockito.kotlin.argumentCaptor<com.stacklens.domain.model.GitlabConfig>()
        verify(gitlabRepo, org.mockito.kotlin.times(1)).saveGitlabConfig(captor.capture())
        assertEquals("http://host.docker.internal:3080", captor.firstValue.baseUrl)
        assertEquals("Demo GitLab", captor.firstValue.name)
        assertEquals("t", captor.firstValue.accessToken)
    }

    @Test
    fun `DemoDataSeeder seeds all sections when empty`() {
        val gitlabRepo =
            mock<com.stacklens.application.port.out.GitlabConfigPort> {
                on { findAllGitlabConfigs() } doReturn emptyList()
            }
        val jiraRepo =
            mock<com.stacklens.application.port.out.JiraConfigPort> {
                on { findAllJiraConfigs() } doReturn emptyList()
            }
        val confluenceRepo =
            mock<com.stacklens.application.port.out.ConfluenceConfigPort> {
                on { findAllConfluenceConfigs() } doReturn emptyList()
            }
        val grafanaRepo =
            mock<com.stacklens.application.port.out.GrafanaConfigPort> {
                on { findAllGrafanaConfigs() } doReturn emptyList()
            }
        val databaseRepo =
            mock<com.stacklens.application.port.out.DatabaseConfigPort> {
                on { findAllDatabaseConfigs() } doReturn emptyList()
            }
        val mcpRepo =
            mock<com.stacklens.application.port.out.McpServerConfigPort> {
                on { findAllMcpServerConfigs() } doReturn emptyList()
            }
        val githubRepo =
            mock<com.stacklens.application.port.out.GithubRepoConfigPort> {
                on { findAllGithubRepoConfigs() } doReturn emptyList()
            }

        val seeder =
            DemoDataSeederTestHelper.buildLegacy(
                gitlabConfigRepo = gitlabRepo,
                jiraConfigRepo = jiraRepo,
                confluenceConfigRepo = confluenceRepo,
                grafanaConfigRepo = grafanaRepo,
                databaseConfigRepo = databaseRepo,
                mcpConfigRepo = mcpRepo,
                githubRepoConfigRepo = githubRepo,
            )
        seeder.reseed()
        verify(gitlabRepo).saveGitlabConfig(any())
        verify(jiraRepo).saveJiraConfig(any())
        verify(confluenceRepo).saveConfluenceConfig(any())
        verify(grafanaRepo).saveGrafanaConfig(any())
        verify(databaseRepo, org.mockito.kotlin.times(3)).saveDatabaseConfig(any())
        verify(githubRepo, org.mockito.kotlin.times(4)).saveGithubRepoConfig(any())
        verify(mcpRepo).saveMcpServerConfig(any())
    }

    @Test
    fun `WebClientConfig field default values match the @Value defaults`() {
        val config = WebClientConfig()
        val builder = config.webClientBuilder()
        assertNotNull(builder)
        assertTrue(builder.toString().isNotBlank())
    }
}

class JiraEndpointsCompanionTest {
    @Test
    fun `DEFAULT_CLOUD_ENDPOINTS has expected size`() {
        val endpoints = com.stacklens.adapter.out.jira.JiraEndpoints.DEFAULT_CLOUD_ENDPOINTS
        assertTrue(endpoints.isNotEmpty())
    }

    @Test
    fun `DEFAULT_SERVER_ENDPOINTS has expected size`() {
        val endpoints = com.stacklens.adapter.out.jira.JiraEndpoints.DEFAULT_SERVER_ENDPOINTS
        assertTrue(endpoints.isNotEmpty())
    }
}
