package com.stacklens.application.service.webpage

import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraTicketImplementationPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.JiraTicketImplementation
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TicketPageQueryServiceTest {
    private val gitlabConfigRepo: GitlabConfigPort = mock()
    private val jiraConfigRepo: JiraConfigPort = mock()
    private val localRepoConfigRepo: LocalRepoConfigPort = mock()
    private val githubRepoConfigRepo: GithubRepoConfigPort = mock()
    private val dbConfigRepo: DatabaseConfigPort = mock()
    private val jiraTicketImplementationRepo: JiraTicketImplementationPort = mock()

    private val service =
        TicketPageQueryService(
            gitlabConfigRepo = gitlabConfigRepo,
            jiraConfigRepo = jiraConfigRepo,
            localRepoConfigRepo = localRepoConfigRepo,
            githubRepoConfigRepo = githubRepoConfigRepo,
            dbConfigRepo = dbConfigRepo,
            jiraTicketImplementationRepo = jiraTicketImplementationRepo,
            demoConfigFilter = PassthroughDemoConfigFilter.instance,
        )

    private fun gitlabConfig() = GitlabConfig(id = 1L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "tok")

    private fun jiraConfig() = JiraConfig(id = 2L, name = "jira", baseUrl = "https://jira.example.com", apiToken = "t")

    private fun localRepoConfig(scope: String) = LocalRepoConfig(id = 3L, name = "repo", path = "/repo", scope = scope)

    private fun githubRepoConfig() = GithubRepoConfig(id = 4L, name = "gh", owner = "acme", repo = "svc")

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

    @Test
    fun `getTicketImplementationPageData returns required data`() {
        val jiraConfigs = listOf(jiraConfig())
        val gitlabConfigs = listOf(gitlabConfig())
        val analyses = listOf(JiraTicketImplementation(ticketKey = "PAY-1", jiraConfigId = 2L))
        val localRepos = listOf(localRepoConfig("TICKET_ANALYSIS"))
        val githubRepos = listOf(githubRepoConfig())
        val dbConfigs = listOf(dbConfig())
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(jiraConfigs)
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(gitlabConfigs)
        whenever(jiraTicketImplementationRepo.findAllByOrderByCreatedAtDesc()).thenReturn(analyses)
        whenever(localRepoConfigRepo.findAllByScope("TICKET_ANALYSIS")).thenReturn(localRepos)
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(githubRepos)
        whenever(dbConfigRepo.findByActiveTrue()).thenReturn(dbConfigs)

        val data = service.getTicketImplementationPageData()

        assertEquals(jiraConfigs, data.jiraConfigs)
        assertEquals(gitlabConfigs, data.gitlabConfigs)
        assertEquals(1, data.recentAnalyses.size)
        assertEquals(localRepos, data.localRepoConfigs)
        assertEquals(githubRepos, data.githubRepoConfigs)
        assertEquals(dbConfigs, data.dbConfigs)
    }

    @Test
    fun `getTicketImplementationPageData limits recent analyses to 10`() {
        val analyses = (1..15).map { JiraTicketImplementation(ticketKey = "T-$it", jiraConfigId = 2L) }
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(jiraTicketImplementationRepo.findAllByOrderByCreatedAtDesc()).thenReturn(analyses)
        whenever(localRepoConfigRepo.findAllByScope("TICKET_ANALYSIS")).thenReturn(emptyList())
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        whenever(dbConfigRepo.findByActiveTrue()).thenReturn(emptyList())

        val data = service.getTicketImplementationPageData()

        assertEquals(10, data.recentAnalyses.size)
    }

    @Test
    fun `getTicketImplementationPageData filters github repos and local repos to active only`() {
        val activeLocal = localRepoConfig("TICKET_ANALYSIS").also { it.active = true }
        val inactiveLocal = LocalRepoConfig(id = 8L, name = "off", path = "/off", scope = "TICKET_ANALYSIS").also { it.active = false }
        val activeGithub = githubRepoConfig().also { it.active = true }
        val inactiveGithub = GithubRepoConfig(id = 9L, name = "off", owner = "x", repo = "y").also { it.active = false }
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(emptyList())
        whenever(jiraTicketImplementationRepo.findAllByOrderByCreatedAtDesc()).thenReturn(emptyList())
        whenever(localRepoConfigRepo.findAllByScope("TICKET_ANALYSIS")).thenReturn(listOf(activeLocal, inactiveLocal))
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(activeGithub, inactiveGithub))
        whenever(dbConfigRepo.findByActiveTrue()).thenReturn(emptyList())

        val data = service.getTicketImplementationPageData()

        assertEquals(1, data.localRepoConfigs.size)
        assertTrue(data.localRepoConfigs[0].active)
        assertEquals(1, data.githubRepoConfigs.size)
        assertTrue(data.githubRepoConfigs[0].active)
    }
}
