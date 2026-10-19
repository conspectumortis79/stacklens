package com.stacklens.application.service.webpage

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SearchPageQueryServiceTest {
    private val gitlabConfigRepo: GitlabConfigPort = mock()
    private val dbConfigRepo: DatabaseConfigPort = mock()
    private val jiraConfigRepo: JiraConfigPort = mock()
    private val confluenceConfigRepo: ConfluenceConfigPort = mock()
    private val githubRepoConfigRepo: GithubRepoConfigPort = mock()

    private val service =
        SearchPageQueryService(
            gitlabConfigRepo = gitlabConfigRepo,
            dbConfigRepo = dbConfigRepo,
            jiraConfigRepo = jiraConfigRepo,
            confluenceConfigRepo = confluenceConfigRepo,
            githubRepoConfigRepo = githubRepoConfigRepo,
            demoConfigFilter = PassthroughDemoConfigFilter.instance,
        )

    private fun gitlabConfig() = GitlabConfig(id = 1L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "tok")

    private fun dbConfig() =
        DatabaseConfig(
            id = 5L,
            name = "db",
            dbType = DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            databaseName = "mydb",
            username = "user",
            password = "pass",
        )

    private fun jiraConfig() = JiraConfig(id = 2L, name = "jira", baseUrl = "https://jira.example.com", apiToken = "t")

    private fun confluenceConfig() = ConfluenceConfig(id = 6L, name = "conf", baseUrl = "https://conf.example.com", username = "u", apiToken = "t")

    private fun githubRepoConfig() = GithubRepoConfig(id = 4L, name = "gh", owner = "acme", repo = "svc")

    @Test
    fun `getSuchePageData returns all active search source configs`() {
        val gitlabConfigs = listOf(gitlabConfig())
        val dbConfigs = listOf(dbConfig())
        val jiraConfigs = listOf(jiraConfig())
        val confluenceConfigs = listOf(confluenceConfig())
        val githubConfigs = listOf(githubRepoConfig())
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(gitlabConfigs)
        whenever(dbConfigRepo.findByActiveTrue()).thenReturn(dbConfigs)
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(jiraConfigs)
        whenever(confluenceConfigRepo.findByActiveTrue()).thenReturn(confluenceConfigs)
        whenever(githubRepoConfigRepo.findByActiveTrue()).thenReturn(githubConfigs)

        val data = service.getSuchePageData()

        assertEquals(gitlabConfigs, data.gitlabConfigs)
        assertEquals(dbConfigs, data.dbConfigs)
        assertEquals(jiraConfigs, data.jiraConfigs)
        assertEquals(confluenceConfigs, data.confluenceConfigs)
        assertEquals(githubConfigs, data.githubConfigs)
    }

    @Test
    fun `getSuchePageData returns empty lists when nothing is active`() {
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(dbConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(confluenceConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(githubRepoConfigRepo.findByActiveTrue()).thenReturn(emptyList())

        val data = service.getSuchePageData()

        assertEquals(emptyList<GitlabConfig>(), data.gitlabConfigs)
        assertEquals(emptyList<DatabaseConfig>(), data.dbConfigs)
        assertEquals(emptyList<JiraConfig>(), data.jiraConfigs)
        assertEquals(emptyList<ConfluenceConfig>(), data.confluenceConfigs)
        assertEquals(emptyList<GithubRepoConfig>(), data.githubConfigs)
    }
}
