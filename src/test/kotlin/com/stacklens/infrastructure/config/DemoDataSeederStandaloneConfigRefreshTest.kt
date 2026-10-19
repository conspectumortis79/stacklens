package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoDataSeeder
import com.stacklens.adapter.out.demo.DemoRuntimeMode
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.testsupport.DemoRuntimeFlagGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(DemoRuntimeFlagGuard::class)
class DemoDataSeederStandaloneConfigRefreshTest {
    private val jiraRepo: JiraConfigPort = mock()
    private val confluenceRepo: ConfluenceConfigPort = mock()
    private val grafanaRepo: GrafanaConfigPort = mock()

    private fun seeder(envInContainer: String): DemoDataSeeder {
        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, if (envInContainer == "true") "true" else "false")
        val stubs = DemoDataSeederTestHelper.stubRepos()
        return DemoDataSeederTestHelper.buildLegacy(
            gitlabConfigRepo = stubs.gitlabConfigRepo,
            jiraConfigRepo = jiraRepo,
            confluenceConfigRepo = confluenceRepo,
            grafanaConfigRepo = grafanaRepo,
            databaseConfigRepo = stubs.databaseConfigRepo,
            mcpConfigRepo = stubs.mcpConfigRepo,
            githubRepoConfigRepo = stubs.githubRepoConfigRepo,
        )
    }

    @Test
    fun `seedJira refreshes the primary Demo Jira and a second demo entry in Docker mode`() {
        val primary =
            JiraConfig(
                id = 188,
                name = "Demo Jira",
                baseUrl = "http://localhost:8081",
                username = "demo",
                apiToken = "demo",
                projectKey = "STACKLENS",
                active = true,
            )
        val secondary =
            JiraConfig(
                id = 189,
                name = "Demo Jira – Backend",
                baseUrl = "http://localhost:8081",
                username = "demo",
                apiToken = "demo",
                projectKey = "BACKEND",
                active = true,
            )
        whenever(jiraRepo.findAllJiraConfigs()).doReturn(listOf(primary, secondary))
        whenever(jiraRepo.saveJiraConfig(any())).thenAnswer { it.arguments[0] as JiraConfig }

        seeder(envInContainer = "true").reseed()

        val captor = argumentCaptor<JiraConfig>()
        verify(jiraRepo, times(2)).saveJiraConfig(captor.capture())
        val byId = captor.allValues.associateBy { it.id }
        assertEquals("http://host.docker.internal:8081", byId.getValue(188).baseUrl)
        assertEquals("http://host.docker.internal:8081", byId.getValue(189).baseUrl)
    }

    @Test
    fun `seedConfluence refreshes every Demo Confluence entry in Docker mode`() {
        val entries =
            listOf(
                ConfluenceConfig(
                    id = 187,
                    name = "Demo Confluence",
                    baseUrl = "http://localhost:8081",
                    username = "demo",
                    apiToken = "demo",
                    spaceKeys = "TECH",
                    active = true,
                ),
                ConfluenceConfig(
                    id = 200,
                    name = "Demo Confluence – Dev",
                    baseUrl = "http://localhost:8081",
                    username = "demo",
                    apiToken = "demo",
                    spaceKeys = "DEV",
                    active = true,
                ),
            )
        whenever(confluenceRepo.findAllConfluenceConfigs()).doReturn(entries)
        whenever(confluenceRepo.saveConfluenceConfig(any())).thenAnswer { it.arguments[0] as ConfluenceConfig }

        seeder(envInContainer = "true").reseed()

        val captor = argumentCaptor<ConfluenceConfig>()
        verify(confluenceRepo, times(2)).saveConfluenceConfig(captor.capture())
        captor.allValues.forEach {
            assertEquals("http://host.docker.internal:8081", it.baseUrl)
        }
    }

    @Test
    fun `seedGrafana refreshes every Demo Grafana entry in Docker mode`() {
        val entries =
            listOf(
                GrafanaConfig(
                    id = 50,
                    name = "Demo Grafana",
                    baseUrl = "http://localhost:3000",
                    username = "admin",
                    password = "stacklens",
                    orgId = 1,
                    active = true,
                ),
                GrafanaConfig(
                    id = 51,
                    name = "Demo Grafana – Ops",
                    baseUrl = "http://localhost:3000",
                    username = "admin",
                    password = "stacklens",
                    orgId = 1,
                    active = true,
                ),
            )
        whenever(grafanaRepo.findAllGrafanaConfigs()).doReturn(entries)
        whenever(grafanaRepo.saveGrafanaConfig(any())).thenAnswer { it.arguments[0] as GrafanaConfig }

        seeder(envInContainer = "true").reseed()

        val captor = argumentCaptor<GrafanaConfig>()
        verify(grafanaRepo, times(2)).saveGrafanaConfig(captor.capture())
        captor.allValues.forEach {
            assertEquals("http://host.docker.internal:3000", it.baseUrl)
        }
    }

    @Test
    fun `seedJira leaves user-customised hosts untouched in Docker mode`() {
        val primary =
            JiraConfig(
                id = 188,
                name = "Demo Jira",
                baseUrl = "http://host.docker.internal:8081",
                username = "demo",
                apiToken = "demo",
                projectKey = "STACKLENS",
                active = true,
            )
        val custom =
            JiraConfig(
                id = 300,
                name = "Demo Jira – Custom",
                baseUrl = "http://confluence.internal.acme.corp:8081",
                username = "demo",
                apiToken = "demo",
                projectKey = "X",
                active = true,
            )
        whenever(jiraRepo.findAllJiraConfigs()).doReturn(listOf(primary, custom))

        seeder(envInContainer = "true").reseed()

        val captor = argumentCaptor<JiraConfig>()
        verify(jiraRepo, never()).saveJiraConfig(captor.capture())
    }

    @Test
    fun `seedJira does not touch a localhost primary in native mode`() {
        val primary =
            JiraConfig(
                id = 1,
                name = "Demo Jira",
                baseUrl = "http://localhost:8081",
                username = "demo",
                apiToken = "demo",
                projectKey = "STACKLENS",
                active = true,
            )
        val secondary =
            JiraConfig(
                id = 2,
                name = "Demo Jira – Backend",
                baseUrl = "http://localhost:8081",
                username = "demo",
                apiToken = "demo",
                projectKey = "BACKEND",
                active = true,
            )
        whenever(jiraRepo.findAllJiraConfigs()).doReturn(listOf(primary, secondary))

        seeder(envInContainer = "false").reseed()

        verify(jiraRepo, never()).saveJiraConfig(any())
    }
}
