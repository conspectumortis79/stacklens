package com.stacklens.application.service

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpSyncFromStacklensServiceTest {
    private val repo: McpServerConfigPort = mock()
    private val jira: JiraConfigPort = mock()
    private val confluence: ConfluenceConfigPort = mock()
    private val gitlab: GitlabConfigPort = mock()
    private val github: GithubRepoConfigPort = mock()
    private val grafana: GrafanaConfigPort = mock()

    private val service =
        McpSyncFromStacklensService(repo, jira, confluence, gitlab, github, grafana)

    private fun mockEmptyState() {
        whenever(repo.findAllMcpServerConfigs()).thenReturn(emptyList())
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] as McpServerConfig }
        whenever(jira.findByActiveTrue()).thenReturn(emptyList())
        whenever(confluence.findByActiveTrue()).thenReturn(emptyList())
        whenever(gitlab.findByActiveTrue()).thenReturn(emptyList())
        whenever(github.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(grafana.findByActiveTrue()).thenReturn(emptyList())
    }

    @Test
    fun `syncFromStacklens creates a new config when no existing one`() {
        mockEmptyState()
        val result = service.syncFromStacklens()
        assertNotNull(result)
        assertEquals("StackLens MCP", result.name)
        assertEquals("http://localhost:7456", result.baseUrl)
    }

    @Test
    fun `syncFromStacklens reuses an existing config with the same name`() {
        val existing =
            McpServerConfig(
                name = "StackLens MCP",
                baseUrl = "http://localhost:7456",
            )
        whenever(repo.findAllMcpServerConfigs()).thenReturn(listOf(existing))
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { it.arguments[0] as McpServerConfig }
        mockEmptyState()
        val result = service.syncFromStacklens()
        assertEquals("StackLens MCP", result.name)
    }

    @Test
    fun `syncFromStacklens copies jira config when active exists`() {
        mockEmptyState()
        val jiraCfg =
            JiraConfig(
                name = "j",
                baseUrl = "https://jira.example.com",
                username = "u",
                apiToken = "t",
                cloudMode = true,
                active = true,
            )
        whenever(jira.findByActiveTrue()).thenReturn(listOf(jiraCfg))
        val result = service.syncFromStacklens()
        assertEquals("https://jira.example.com", result.jiraBaseUrl)
        assertEquals("u", result.jiraUsername)
        assertEquals("t", result.jiraApiToken)
        assertEquals(true, result.jiraCloudMode)
    }

    @Test
    fun `syncFromStacklens copies confluence config when active exists`() {
        mockEmptyState()
        val confCfg =
            ConfluenceConfig(
                name = "c",
                baseUrl = "https://confluence.example.com",
                username = "u",
                apiToken = "t",
                cloudMode = true,
                active = true,
            )
        whenever(confluence.findByActiveTrue()).thenReturn(listOf(confCfg))
        val result = service.syncFromStacklens()
        assertEquals("https://confluence.example.com", result.confluenceBaseUrl)
        assertEquals("u", result.confluenceUsername)
        assertEquals("t", result.confluenceApiToken)
    }

    @Test
    fun `syncFromStacklens copies gitlab config when active exists`() {
        mockEmptyState()
        val gitlabCfg =
            GitlabConfig(
                name = "g",
                baseUrl = "https://gitlab.example.com",
                accessToken = "tok",
                active = true,
            )
        whenever(gitlab.findByActiveTrue()).thenReturn(listOf(gitlabCfg))
        val result = service.syncFromStacklens()
        assertEquals("https://gitlab.example.com", result.gitlabBaseUrl)
        assertEquals("tok", result.gitlabAccessToken)
    }

    @Test
    fun `syncFromStacklens copies github config with non-blank token`() {
        mockEmptyState()
        val gh =
            GithubRepoConfig(
                name = "gh",
                owner = "o",
                repo = "r",
                accessToken = "gh-token",
                active = true,
            )
        whenever(github.findAllGithubRepoConfigs()).thenReturn(listOf(gh))
        val result = service.syncFromStacklens()
        assertEquals("gh-token", result.githubToken)
    }

    @Test
    fun `syncFromStacklens skips github config with blank token`() {
        mockEmptyState()
        val gh =
            GithubRepoConfig(
                name = "gh",
                owner = "o",
                repo = "r",
                accessToken = "",
                active = true,
            )
        whenever(github.findAllGithubRepoConfigs()).thenReturn(listOf(gh))
        val result = service.syncFromStacklens()
        assertEquals("", result.githubToken)
    }

    @Test
    fun `syncFromStacklens copies grafana config when active exists with apiToken`() {
        mockEmptyState()
        val gCfg =
            GrafanaConfig(
                name = "g",
                baseUrl = "https://grafana.example.com",
                username = "u",
                password = "p",
                apiToken = "gtok",
                active = true,
            )
        whenever(grafana.findByActiveTrue()).thenReturn(listOf(gCfg))
        val result = service.syncFromStacklens()
        assertEquals("https://grafana.example.com", result.grafanaBaseUrl)
        assertEquals("gtok", result.grafanaApiKey)
    }

    @Test
    fun `syncFromStacklens skips grafana apiToken when blank`() {
        mockEmptyState()
        val gCfg =
            GrafanaConfig(
                name = "g",
                baseUrl = "https://grafana.example.com",
                username = "u",
                password = "p",
                apiToken = "",
                active = true,
            )
        whenever(grafana.findByActiveTrue()).thenReturn(listOf(gCfg))
        val result = service.syncFromStacklens()
        assertEquals("", result.grafanaApiKey)
    }

    @Test
    fun `syncFromStacklens picks first github config with a non-blank access token`() {
        mockEmptyState()
        whenever(github.findAllGithubRepoConfigs()).thenReturn(
            listOf(
                GithubRepoConfig(name = "first", owner = "o", repo = "r", accessToken = null),
                GithubRepoConfig(name = "second", owner = "o", repo = "r", accessToken = "ghp_xxx"),
            ),
        )
        val result = service.syncFromStacklens()
        assertEquals("ghp_xxx", result.githubToken)
    }

    @Test
    fun `syncFromStacklens sets grafanaApiKey when grafana api token is not blank`() {
        mockEmptyState()
        whenever(grafana.findByActiveTrue()).thenReturn(
            listOf(GrafanaConfig(name = "g", baseUrl = "https://g", username = "u", password = "p", apiToken = "grafana-tok")),
        )
        val result = service.syncFromStacklens()
        assertEquals("grafana-tok", result.grafanaApiKey)
    }
}
