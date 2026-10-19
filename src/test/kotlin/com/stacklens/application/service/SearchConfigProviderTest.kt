package com.stacklens.application.service

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.SearchSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SearchConfigProviderTest {
    private val gitlabRepo: GitlabConfigPort = mock()
    private val dbRepo: DatabaseConfigPort = mock()
    private val confluenceRepo: ConfluenceConfigPort = mock()
    private val grafanaRepo: GrafanaConfigPort = mock()
    private val jiraRepo: JiraConfigPort = mock()
    private val githubRepo: GithubRepoConfigPort = mock()

    private val provider =
        SearchConfigProvider(
            gitlabConfigRepo = gitlabRepo,
            dbConfigRepo = dbRepo,
            confluenceConfigRepo = confluenceRepo,
            grafanaConfigRepo = grafanaRepo,
            jiraConfigRepo = jiraRepo,
            githubRepoConfigRepo = githubRepo,
        )

    private fun stub(): Unit = Unit

    @Test
    fun `resolveGitlabConfigs returns active configs when IDs are blank`() {
        val session = SearchSession(keywords = "k")
        val active = listOf(GitlabConfig(name = "g", baseUrl = "u", accessToken = "t"))
        whenever(gitlabRepo.findByActiveTrue()).thenReturn(active)

        assertEquals(active, provider.resolveGitlabConfigs(session))
        verify(gitlabRepo).findByActiveTrue()
    }

    @Test
    fun `resolveGitlabConfigs fetches by ids when IDs are set`() {
        val session = SearchSession(keywords = "k", gitlabConfigIds = "1,2")
        val byIds = listOf(GitlabConfig(name = "g", baseUrl = "u", accessToken = "t"))
        whenever(gitlabRepo.findAllGitlabConfigsByIds(any())).thenReturn(byIds)

        assertEquals(byIds, provider.resolveGitlabConfigs(session))
        verify(gitlabRepo).findAllGitlabConfigsByIds(listOf(1L, 2L))
    }

    @Test
    fun `resolveDbConfigs returns active configs when IDs are blank`() {
        val session = SearchSession(keywords = "k")
        val active = listOf(DatabaseConfig(name = "d", dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL, host = "h", port = 5432, databaseName = "db", username = "u", password = "p"))
        whenever(dbRepo.findByActiveTrue()).thenReturn(active)

        assertEquals(active, provider.resolveDbConfigs(session))
    }

    @Test
    fun `resolveDbConfigs returns active configs when IDs are whitespace`() {
        val session = SearchSession(keywords = "k", databaseConfigIds = "   ")
        val active = listOf(DatabaseConfig(name = "d", dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL, host = "h", port = 5432, databaseName = "db", username = "u", password = "p"))
        whenever(dbRepo.findByActiveTrue()).thenReturn(active)

        assertEquals(active, provider.resolveDbConfigs(session))
    }

    @Test
    fun `resolveDbConfigs fetches by ids when IDs are set`() {
        val session = SearchSession(keywords = "k", databaseConfigIds = "5")
        val byIds = listOf(DatabaseConfig(name = "d", dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL, host = "h", port = 5432, databaseName = "db", username = "u", password = "p"))
        whenever(dbRepo.findAllDatabaseConfigsByIds(any())).thenReturn(byIds)

        assertEquals(byIds, provider.resolveDbConfigs(session))
        verify(dbRepo).findAllDatabaseConfigsByIds(listOf(5L))
    }

    @Test
    fun `resolveConfluenceConfigs returns active configs when IDs are blank`() {
        val session = SearchSession(keywords = "k")
        val active = listOf(ConfluenceConfig(name = "c", baseUrl = "u", username = "u", apiToken = "t"))
        whenever(confluenceRepo.findByActiveTrue()).thenReturn(active)

        assertEquals(active, provider.resolveConfluenceConfigs(session))
    }

    @Test
    fun `resolveConfluenceConfigs fetches by ids when IDs are set`() {
        val session = SearchSession(keywords = "k", confluenceConfigIds = "3")
        val byIds = listOf(ConfluenceConfig(name = "c", baseUrl = "u", username = "u", apiToken = "t"))
        whenever(confluenceRepo.findAllConfluenceConfigsByIds(any())).thenReturn(byIds)

        assertEquals(byIds, provider.resolveConfluenceConfigs(session))
        verify(confluenceRepo).findAllConfluenceConfigsByIds(listOf(3L))
    }

    @Test
    fun `resolveGrafanaConfigs returns active configs when IDs are blank`() {
        val session = SearchSession(keywords = "k")
        val active = listOf(GrafanaConfig(name = "g", baseUrl = "u", username = "u", password = "p"))
        whenever(grafanaRepo.findByActiveTrue()).thenReturn(active)

        assertEquals(active, provider.resolveGrafanaConfigs(session))
    }

    @Test
    fun `resolveGrafanaConfigs fetches by ids when IDs are set`() {
        val session = SearchSession(keywords = "k", grafanaConfigIds = "4")
        val byIds = listOf(GrafanaConfig(name = "g", baseUrl = "u", username = "u", password = "p"))
        whenever(grafanaRepo.findAllGrafanaConfigsByIds(any())).thenReturn(byIds)

        assertEquals(byIds, provider.resolveGrafanaConfigs(session))
        verify(grafanaRepo).findAllGrafanaConfigsByIds(listOf(4L))
    }

    @Test
    fun `resolveJiraConfigs returns active configs when IDs are blank`() {
        val session = SearchSession(keywords = "k")
        val active = listOf(JiraConfig(name = "j", baseUrl = "u", apiToken = "t"))
        whenever(jiraRepo.findByActiveTrue()).thenReturn(active)

        assertEquals(active, provider.resolveJiraConfigs(session))
    }

    @Test
    fun `resolveJiraConfigs fetches by ids when IDs are set`() {
        val session = SearchSession(keywords = "k", jiraConfigIds = "7")
        val byIds = listOf(JiraConfig(name = "j", baseUrl = "u", apiToken = "t"))
        whenever(jiraRepo.findAllJiraConfigsByIds(any())).thenReturn(byIds)

        assertEquals(byIds, provider.resolveJiraConfigs(session))
        verify(jiraRepo).findAllJiraConfigsByIds(listOf(7L))
    }

    @Test
    fun `resolveGithubConfigs returns active configs when IDs are blank`() {
        val session = SearchSession(keywords = "k")
        val active = listOf(GithubRepoConfig(name = "g", owner = "o", repo = "r"))
        whenever(githubRepo.findByActiveTrue()).thenReturn(active)

        assertEquals(active, provider.resolveGithubConfigs(session))
    }

    @Test
    fun `resolveGithubConfigs fetches by ids when IDs are set`() {
        val session = SearchSession(keywords = "k", githubConfigIds = "9")
        val byIds = listOf(GithubRepoConfig(name = "g", owner = "o", repo = "r"))
        whenever(githubRepo.findAllGithubRepoConfigsByIds(any())).thenReturn(byIds)

        assertEquals(byIds, provider.resolveGithubConfigs(session))
        verify(githubRepo).findAllGithubRepoConfigsByIds(listOf(9L))
    }
}
