package com.stacklens.application.service

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpSyncFromStacklensServiceUpdateTest {
    private val repo = mock<McpServerConfigPort>()
    private val jira = mock<JiraConfigPort>()
    private val confluence = mock<ConfluenceConfigPort>()
    private val gitlab = mock<GitlabConfigPort>()
    private val github = mock<GithubRepoConfigPort>()
    private val grafana = mock<GrafanaConfigPort>()

    private val service = McpSyncFromStacklensService(repo, jira, confluence, gitlab, github, grafana)

    @Test
    fun `an already synced config is updated in place and keeps its activation`() {
        val existing = McpServerConfig(name = "StackLens MCP", baseUrl = "http://localhost:7456", active = false)
        whenever(repo.findAllMcpServerConfigs()).thenReturn(listOf(McpServerConfig(name = "anders"), existing))
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { invocation -> invocation.arguments[0] }
        whenever(jira.findByActiveTrue()).thenReturn(emptyList())
        whenever(confluence.findByActiveTrue()).thenReturn(emptyList())
        whenever(gitlab.findByActiveTrue()).thenReturn(emptyList())
        whenever(grafana.findByActiveTrue()).thenReturn(emptyList())
        whenever(github.findAllGithubRepoConfigs()).thenReturn(listOf(GithubRepoConfig(id = 1L, name = "n", owner = "o", repo = "r", accessToken = "   ")))

        val saved = service.syncFromStacklens()

        assertSame(existing, saved)
        assertFalse(saved.active)
    }

    @Test
    fun `a stored github token of the same project is synced into the config`() {
        val existing = McpServerConfig(name = "StackLens MCP", baseUrl = "http://localhost:7456", active = false)
        whenever(repo.findAllMcpServerConfigs()).thenReturn(listOf(existing))
        whenever(repo.saveMcpServerConfig(any())).thenAnswer { invocation -> invocation.arguments[0] }
        whenever(jira.findByActiveTrue()).thenReturn(emptyList())
        whenever(confluence.findByActiveTrue()).thenReturn(emptyList())
        whenever(gitlab.findByActiveTrue()).thenReturn(emptyList())
        whenever(grafana.findByActiveTrue()).thenReturn(emptyList())
        whenever(github.findAllGithubRepoConfigs()).thenReturn(listOf(GithubRepoConfig(id = 1L, name = "n", owner = "o", repo = "r", accessToken = "geheim")))

        val saved = service.syncFromStacklens()

        assertSame(existing, saved)
        assertFalse(saved.active)
    }
}
