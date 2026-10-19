package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.demo.DemoConfigFilter
import com.stacklens.adapter.out.demo.DemoDataSeeder
import com.stacklens.adapter.out.demo.DemoDataSeederStrategy
import com.stacklens.adapter.out.demo.DemoHostResolver
import com.stacklens.adapter.out.jira.DefaultJiraApiVersionResolver
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.domain.model.JiraConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DemoHostResolverBranchTest {
    private val resolver = DemoHostResolver()

    @Test
    fun `hostFor chooses docker host only in container`() {
        assertEquals("localhost", resolver.hostFor("host.docker.internal", null, false))
        assertEquals("host.docker.internal", resolver.hostFor("host.docker.internal", "true", false))
        assertEquals("host.docker.internal", resolver.hostFor("host.docker.internal", null, true))
    }

    @Test
    fun `mcpHostFor and mcpBaseUrlFor choose stacklens-mcp in container`() {
        assertEquals("localhost", resolver.mcpHostFor(null, false))
        assertEquals("stacklens-mcp", resolver.mcpHostFor("true", false))
        assertEquals("http://localhost:7456", resolver.mcpBaseUrlFor(7456, null, false))
        assertEquals("http://stacklens-mcp:7999", resolver.mcpBaseUrlFor(7999, "true", false))
    }

    @Test
    fun `refreshHost rewrites default host when flipped`() {
        assertEquals("http://host.docker.internal:8080", resolver.refreshHost("http://localhost:8080", "true", false))
        assertEquals("http://localhost:8080", resolver.refreshHost("http://host.docker.internal:8080", null, false))
    }

    @Test
    fun `refreshHost leaves other urls untouched`() {
        assertEquals("https://example.com/x", resolver.refreshHost("https://example.com/x", "true", false))
        assertEquals("not-a-url", resolver.refreshHost("not-a-url", "true", false))
    }
}

class DemoConfigFilterBranchTest {
    private val uiPreferences: com.stacklens.application.port.out.UiPreferencesPort = mock()

    @Test
    fun `filter keeps demo configs when dev env enabled`() {
        whenever(uiPreferences.getAll()).thenReturn(mapOf("dev_docker_test_env" to "true"))
        val filter = DemoConfigFilter(uiPreferences)
        val result = filter.apply(listOf("Demo GitLab", "Real"), { it })
        assertEquals(listOf("Demo GitLab", "Real"), result)
    }

    @Test
    fun `filter strips demo configs otherwise`() {
        whenever(uiPreferences.getAll()).thenReturn(mapOf())
        val filter = DemoConfigFilter(uiPreferences)
        val result = filter.apply(listOf("Demo GitLab", "  Demo Confluence  ", "Real"), { it })
        assertEquals(listOf("Real"), result)
    }

    @Test
    fun `filter tolerates preferences errors`() {
        whenever(uiPreferences.getAll()).thenThrow(RuntimeException("boom"))
        val filter = DemoConfigFilter(uiPreferences)
        assertEquals(listOf("Real"), filter.apply(listOf("Demo x", "Real"), { it }))
    }
}

class DefaultJiraApiVersionResolverBranchTest {
    private val resolver = DefaultJiraApiVersionResolver()

    private fun cfg(cloud: Boolean) = JiraConfig(name = "j", baseUrl = "u", apiToken = "t", cloudMode = cloud)

    @Test
    fun `apiVersion and paths follow cloud mode`() {
        assertEquals("3", resolver.apiVersion(cfg(true)))
        assertEquals("2", resolver.apiVersion(cfg(false)))
        assertEquals("/rest/api/3/search/jql", resolver.searchPath(cfg(true)))
        assertEquals("/rest/api/2/search", resolver.searchPath(cfg(false)))
        assertEquals("/wiki/rest/api/content/search", resolver.contentSearchPath(cfg(true)))
        assertEquals("/rest/api/content/search", resolver.contentSearchPath(cfg(false)))
        assertEquals("/rest/api/3/issue", resolver.issueBasePath(cfg(true)))
        assertEquals("/rest/api/2/issue", resolver.issueBasePath(cfg(false)))
    }
}

class DemoDataSeederBranchTest {
    private val strategy: DemoDataSeederStrategy = mock()
    private val hostResolver: DemoHostResolver = mock()
    private val seeder = DemoDataSeeder(listOf(strategy))

    @Test
    fun `reseed runs all seeders`() {
        seeder.reseed()
        verify(strategy).seed()
    }

    @Test
    fun `the legacy seeder list returns all seven seeders`() {
        val seeders =
            DemoDataSeederTestHelper.legacySeeders(
                gitlabConfigRepo = mock<GitlabConfigPort>(),
                jiraConfigRepo = mock<JiraConfigPort>(),
                confluenceConfigRepo = mock<ConfluenceConfigPort>(),
                grafanaConfigRepo = mock<GrafanaConfigPort>(),
                databaseConfigRepo = mock<DatabaseConfigPort>(),
                mcpConfigRepo = mock<McpServerConfigPort>(),
                githubRepoConfigRepo = mock<GithubRepoConfigPort>(),
                hostResolver = hostResolver,
            )
        assertEquals(7, seeders.size)
    }
}
