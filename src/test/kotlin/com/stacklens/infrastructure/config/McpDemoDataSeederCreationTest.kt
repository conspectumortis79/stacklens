package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoHostResolver
import com.stacklens.adapter.out.demo.DemoRuntimeMode
import com.stacklens.adapter.out.demo.McpDemoDataSeeder
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpDemoDataSeederCreationTest {
    private val rows = mutableListOf<McpServerConfig>()

    private val repo: McpServerConfigPort =
        mock {
            whenever(it.findAllMcpServerConfigs()).thenAnswer { rows.toList() }
            whenever(it.saveMcpServerConfig(any())).thenAnswer { call ->
                val saved = call.getArgument<McpServerConfig>(0)
                if (rows.none { it === saved }) {
                    rows += saved
                }
                saved
            }
        }

    @AfterEach
    fun clearDockerFlag() {
        System.clearProperty(DOCKER_FLAG)
    }

    @Test
    fun `a created mcp demo entry points its service urls at the container host`() {
        System.setProperty(DOCKER_FLAG, "true")

        seeder().seed()

        val created = rows.single()
        assertEquals(McpDemoDataSeeder.STACKLENS_MCP_NAME, created.name)
        assertEquals("http://stacklens-mcp:7456", created.baseUrl)
        assertEquals("http://host.docker.internal:8081", created.jiraBaseUrl)
        assertEquals("http://host.docker.internal:8081", created.confluenceBaseUrl)
        assertEquals("http://host.docker.internal:3080", created.gitlabBaseUrl)
        assertEquals("http://host.docker.internal:8082", created.githubApiBase)
        assertEquals("http://host.docker.internal:3000", created.grafanaBaseUrl)
    }

    @Test
    fun `a created mcp demo entry keeps localhost service urls when run natively`() {
        System.clearProperty(DOCKER_FLAG)

        seeder().seed()

        val created = rows.single()
        assertEquals("http://localhost:7456", created.baseUrl)
        assertEquals("http://localhost:8081", created.jiraBaseUrl)
        assertEquals("http://localhost:3080", created.gitlabBaseUrl)
    }

    private fun seeder(): McpDemoDataSeeder =
        McpDemoDataSeeder(
            mcpConfigRepo = repo,
            hostResolver = DemoHostResolver(),
            mockGrafanaUrl = "http://localhost:3000",
            mockJiraUrl = "http://localhost:8081",
            mockConfluenceUrl = "http://localhost:8081",
            mockGitlabUrl = "http://localhost:3080",
            mockGithubUrl = "http://localhost:8082",
        )

    private companion object {
        const val DOCKER_FLAG = DemoRuntimeMode.DOCKER_FLAG_PROPERTY
    }
}
