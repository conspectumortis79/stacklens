package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoDataSeeder
import com.stacklens.adapter.out.demo.DemoRuntimeMode
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.testsupport.DemoRuntimeFlagGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(DemoRuntimeFlagGuard::class)
class DemoDataSeederMcpRefreshTest {
    private val repo: McpServerConfigPort = mock()

    private fun seeder(envInContainer: String): DemoDataSeeder {
        val stubs = DemoDataSeederTestHelper.stubRepos()
        val s =
            DemoDataSeederTestHelper.buildLegacy(
                gitlabConfigRepo = stubs.gitlabConfigRepo,
                jiraConfigRepo = stubs.jiraConfigRepo,
                confluenceConfigRepo = stubs.confluenceConfigRepo,
                grafanaConfigRepo = stubs.grafanaConfigRepo,
                databaseConfigRepo = stubs.databaseConfigRepo,
                mcpConfigRepo = repo,
                githubRepoConfigRepo = stubs.githubRepoConfigRepo,
            )
        System.setProperty(DemoRuntimeMode.DOCKER_FLAG_PROPERTY, if (envInContainer == "true") "true" else "false")
        return s
    }

    @Test
    fun `seedMcp refreshes Demo MCP Server URLs in Docker mode`() {
        val demo =
            McpServerConfig(
                id = 2,
                name = "Demo MCP Server",
                baseUrl = "http://localhost:7456",
                active = true,
                jiraBaseUrl = "http://localhost:8081",
                confluenceBaseUrl = "http://localhost:8081",
                gitlabBaseUrl = "http://localhost:3080",
                githubApiBase = "http://localhost:8082",
                grafanaBaseUrl = "http://localhost:3000",
            )
        val stacklens =
            McpServerConfig(
                id = 3,
                name = "StackLens MCP",
                baseUrl = "http://localhost:7456",
                active = true,
                jiraBaseUrl = "http://host.docker.internal:8081",
                confluenceBaseUrl = "http://host.docker.internal:8081",
            )
        whenever(repo.findAllMcpServerConfigs()).doReturn(listOf(demo, stacklens))
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] as McpServerConfig }

        seeder(envInContainer = "true").reseed()

        val captor = argumentCaptor<McpServerConfig>()
        verify(repo, times(2)).saveMcpServerConfig(captor.capture())

        val savedDemo = captor.allValues.first { it.id == 2L }
        assertEquals("http://stacklens-mcp:7456", savedDemo.baseUrl)
        assertEquals("http://host.docker.internal:8081", savedDemo.jiraBaseUrl)
        assertEquals("http://host.docker.internal:8081", savedDemo.confluenceBaseUrl)
        assertEquals("http://host.docker.internal:3080", savedDemo.gitlabBaseUrl)
        assertEquals("http://host.docker.internal:8082", savedDemo.githubApiBase)
        assertEquals("http://host.docker.internal:3000", savedDemo.grafanaBaseUrl)

        val savedStacklens = captor.allValues.first { it.id == 3L }
        assertEquals("http://stacklens-mcp:7456", savedStacklens.baseUrl)
    }

    @Test
    fun `seedMcp rewrites a stale docker-style baseUrl to localhost in native mode`() {
        val demo =
            McpServerConfig(
                id = 2,
                name = "Demo MCP Server",
                baseUrl = "http://localhost:7456",
                active = true,
                jiraBaseUrl = "http://localhost:8081",
                confluenceBaseUrl = "http://localhost:8081",
                gitlabBaseUrl = "http://localhost:3080",
            )
        val stacklens =
            McpServerConfig(
                id = 3,
                name = "StackLens MCP",
                baseUrl = "http://stacklens-mcp:7456",
                active = true,
                jiraBaseUrl = "http://localhost:8081",
                confluenceBaseUrl = "http://localhost:8081",
            )
        whenever(repo.findAllMcpServerConfigs()).doReturn(listOf(demo, stacklens))
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] as McpServerConfig }

        seeder(envInContainer = "false").reseed()

        val captor = argumentCaptor<McpServerConfig>()
        verify(repo, times(1)).saveMcpServerConfig(captor.capture())

        val savedStacklens = captor.allValues.first { it.id == 3L }
        assertEquals("http://localhost:7456", savedStacklens.baseUrl)
    }

    @Test
    fun `seedMcp rewrites the default MCP baseUrl but leaves user-customised per-service hosts alone`() {
        val demo =
            McpServerConfig(
                id = 2,
                name = "Demo MCP Server",
                baseUrl = "http://localhost:7456",
                active = true,
                jiraBaseUrl = "http://confluence.internal.acme.corp:8081",
                confluenceBaseUrl = "http://confluence.internal.acme.corp:8081",
                gitlabBaseUrl = "http://gitlab.internal.acme.corp:3080",
            )
        val stacklens =
            McpServerConfig(
                id = 3,
                name = "StackLens MCP",
                baseUrl = "http://stacklens-mcp:7456",
                active = true,
                jiraBaseUrl = "http://host.docker.internal:8081",
                confluenceBaseUrl = "http://host.docker.internal:8081",
            )
        whenever(repo.findAllMcpServerConfigs()).doReturn(listOf(demo, stacklens))
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] as McpServerConfig }

        seeder(envInContainer = "true").reseed()

        val captor = argumentCaptor<McpServerConfig>()
        verify(repo, times(1)).saveMcpServerConfig(captor.capture())

        val savedDemo = captor.allValues.first { it.id == 2L }
        assertEquals("http://stacklens-mcp:7456", savedDemo.baseUrl)
        assertEquals("http://confluence.internal.acme.corp:8081", savedDemo.jiraBaseUrl)
        assertEquals("http://confluence.internal.acme.corp:8081", savedDemo.confluenceBaseUrl)
        assertEquals("http://gitlab.internal.acme.corp:3080", savedDemo.gitlabBaseUrl)
    }
}
