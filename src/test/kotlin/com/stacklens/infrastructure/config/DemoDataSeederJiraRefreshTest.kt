package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoDataSeeder
import com.stacklens.adapter.out.demo.DemoRuntimeMode
import com.stacklens.application.port.out.JiraConfigPort
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
class DemoDataSeederJiraRefreshTest {
    private val repo: JiraConfigPort = mock()

    private fun seeder(envInContainer: String): DemoDataSeeder {
        val stubs = DemoDataSeederTestHelper.stubRepos()
        val s =
            DemoDataSeederTestHelper.buildLegacy(
                gitlabConfigRepo = stubs.gitlabConfigRepo,
                jiraConfigRepo = repo,
                confluenceConfigRepo = stubs.confluenceConfigRepo,
                grafanaConfigRepo = stubs.grafanaConfigRepo,
                databaseConfigRepo = stubs.databaseConfigRepo,
                mcpConfigRepo = stubs.mcpConfigRepo,
                githubRepoConfigRepo = stubs.githubRepoConfigRepo,
            )
        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, if (envInContainer == "true") "true" else "false")
        return s
    }

    @Test
    fun `seedJira refreshes Demo Jira – Backend baseUrl in Docker mode`() {
        val primary =
            JiraConfig(
                id = 1,
                name = "Demo Jira",
                baseUrl = "http://host.docker.internal:8081",
                apiToken = "demo",
                projectKey = "STACKLENS,BACKEND,FRONTEND,OPS,MOBILE,INFRA",
                username = "demo",
                cloudMode = false,
            )
        val backend =
            JiraConfig(
                id = 2,
                name = "Demo Jira – Backend",
                baseUrl = "http://localhost:8081",
                apiToken = "demo-token",
                projectKey = "BACKEND",
                username = "demo",
                cloudMode = false,
            )
        whenever(repo.findAllJiraConfigs()).doReturn(listOf(primary, backend))
        whenever(repo.saveJiraConfig(any())).thenAnswer { it.arguments[0] as JiraConfig }

        seeder(envInContainer = "true").reseed()

        val captor = argumentCaptor<JiraConfig>()
        verify(repo, times(1)).saveJiraConfig(captor.capture())

        val saved = captor.allValues.first { it.id == 2L }
        assertEquals("http://host.docker.internal:8081", saved.baseUrl)
    }

    @Test
    fun `seedJira does not touch Demo Jira – Backend baseUrl in native mode`() {
        val primary =
            JiraConfig(
                id = 1,
                name = "Demo Jira",
                baseUrl = "http://localhost:8081",
                apiToken = "demo",
                projectKey = "STACKLENS,BACKEND,FRONTEND,OPS,MOBILE,INFRA",
                username = "demo",
                cloudMode = false,
            )
        val backend =
            JiraConfig(
                id = 2,
                name = "Demo Jira – Backend",
                baseUrl = "http://localhost:8081",
                apiToken = "demo-token",
                projectKey = "BACKEND",
                username = "demo",
                cloudMode = false,
            )
        whenever(repo.findAllJiraConfigs()).doReturn(listOf(primary, backend))

        seeder(envInContainer = "false").reseed()

        verify(repo, never()).saveJiraConfig(any())
    }

    @Test
    fun `seedJira does not rewrite user-customised hosts on Demo Jira – Backend`() {
        val primary =
            JiraConfig(
                id = 1,
                name = "Demo Jira",
                baseUrl = "http://host.docker.internal:8081",
                apiToken = "demo",
                projectKey = "STACKLENS,BACKEND,FRONTEND,OPS,MOBILE,INFRA",
                username = "demo",
                cloudMode = false,
            )
        val backend =
            JiraConfig(
                id = 2,
                name = "Demo Jira – Backend",
                baseUrl = "https://jira.backend.mycompany.com",
                apiToken = "real-token",
                projectKey = "BACKEND",
                username = "user",
                cloudMode = true,
            )
        whenever(repo.findAllJiraConfigs()).doReturn(listOf(primary, backend))

        seeder(envInContainer = "true").reseed()

        verify(repo, never()).saveJiraConfig(any())
    }
}
