package com.stacklens.application.service.webpage

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraTicketImplementationPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.ReviewAnalysisConfig
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WebPageQueryServicesCoverageTest {
    private val demoConfigFilter = PassthroughDemoConfigFilter.instance

    private val gitlabConfigRepo = mock<GitlabConfigPort>()
    private val dbConfigRepo = mock<DatabaseConfigPort>()
    private val confluenceConfigRepo = mock<ConfluenceConfigPort>()
    private val grafanaConfigRepo = mock<GrafanaConfigPort>()
    private val jiraConfigRepo = mock<JiraConfigPort>()
    private val githubRepoConfigRepo = mock<GithubRepoConfigPort>()
    private val aiConfigRepo = mock<AiConfigPort>()
    private val localRepoConfigRepo = mock<LocalRepoConfigPort>()
    private val implementationRepo = mock<JiraTicketImplementationPort>()
    private val reviewAnalysisConfigRepo = mock<ReviewAnalysisConfigPort>()

    private val gitlab = GitlabConfig(name = "gitlab-one", baseUrl = "https://gitlab", accessToken = "t", groupId = "g")
    private val database =
        DatabaseConfig(
            name = "db-one",
            dbType = DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            databaseName = "app",
            username = "u",
            password = "p",
        )
    private val confluence = ConfluenceConfig(name = "wiki", baseUrl = "https://wiki", username = "u", apiToken = "t")
    private val grafana = GrafanaConfig(name = "dash", baseUrl = "https://grafana", username = "u", password = "p")
    private val jira = JiraConfig(name = "jira-one", baseUrl = "https://jira", username = "u", apiToken = "t")
    private val github = GithubRepoConfig(name = "gh", owner = "acme", repo = "shop")
    private val ai = AiConfig()

    @Test
    fun `config page collects every connection profile and the ai settings`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(listOf(gitlab))
        whenever(dbConfigRepo.findAllDatabaseConfigs()).thenReturn(listOf(database))
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(listOf(confluence))
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(listOf(grafana))
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(listOf(jira))
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(github))
        whenever(aiConfigRepo.findAiConfig()).thenReturn(ai)

        val service =
            ConfigPageQueryService(
                gitlabConfigRepo,
                dbConfigRepo,
                confluenceConfigRepo,
                grafanaConfigRepo,
                jiraConfigRepo,
                aiConfigRepo,
                githubRepoConfigRepo,
                demoConfigFilter,
                parallelTasks = com.stacklens.adapter.out.async.ForkJoinParallelTaskPort,
            )

        val data = service.getConfigPageData()

        assertEquals(listOf(gitlab), data.gitlabConfigs)
        assertEquals(listOf(database), data.dbConfigs)
        assertEquals(listOf(confluence), data.confluenceConfigs)
        assertEquals(listOf(grafana), data.grafanaConfigs)
        assertEquals(listOf(jira), data.jiraConfigs)
        assertEquals(listOf(github), data.githubRepoConfigs)
        assertSame(ai, data.aiConfig)
    }

    @Test
    fun `search page collects the active repositories of every integration`() {
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(listOf(gitlab))
        whenever(dbConfigRepo.findByActiveTrue()).thenReturn(listOf(database))
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(listOf(jira))
        whenever(confluenceConfigRepo.findByActiveTrue()).thenReturn(listOf(confluence))
        whenever(githubRepoConfigRepo.findByActiveTrue()).thenReturn(listOf(github))

        val service =
            SearchPageQueryService(
                gitlabConfigRepo,
                dbConfigRepo,
                jiraConfigRepo,
                confluenceConfigRepo,
                githubRepoConfigRepo,
                demoConfigFilter,
            )

        val data = service.getSuchePageData()

        assertEquals(listOf(gitlab), data.gitlabConfigs)
        assertEquals(listOf(database), data.dbConfigs)
        assertEquals(listOf(jira), data.jiraConfigs)
        assertEquals(listOf(confluence), data.confluenceConfigs)
        assertEquals(listOf(github), data.githubConfigs)
    }

    @Test
    fun `ticket page keeps only active repositories and the ten newest analyses`() {
        val inactive = GithubRepoConfig(name = "inactive", owner = "acme", repo = "old", active = false)
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(listOf(jira))
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(listOf(gitlab))
        whenever(dbConfigRepo.findByActiveTrue()).thenReturn(listOf(database))
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(github, inactive))
        whenever(localRepoConfigRepo.findAllByScope("TICKET_ANALYSIS")).thenReturn(listOf(LocalRepoConfig(name = "local", path = "/tmp/local")))

        val service =
            TicketPageQueryService(
                gitlabConfigRepo,
                jiraConfigRepo,
                localRepoConfigRepo,
                githubRepoConfigRepo,
                dbConfigRepo,
                implementationRepo,
                demoConfigFilter,
            )

        val data = service.getTicketImplementationPageData()

        assertEquals(listOf(jira), data.jiraConfigs)
        assertEquals(listOf(gitlab), data.gitlabConfigs)
        assertEquals(listOf(database), data.dbConfigs)
        assertEquals(listOf(github), data.githubRepoConfigs)
        assertEquals(1, data.localRepoConfigs.size)
        assertEquals(emptyList<Any>(), data.recentAnalyses)
    }

    @Test
    fun `review page pairs the active jira profile with the saved analysis config`() {
        val saved = ReviewAnalysisConfig()
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(listOf(jira))
        whenever(reviewAnalysisConfigRepo.findReviewAnalysisConfig()).thenReturn(saved)

        val service = ReviewPageQueryService(jiraConfigRepo, reviewAnalysisConfigRepo, demoConfigFilter)
        val data = service.getReviewAnalysePageData()

        assertEquals(listOf(jira), data.jiraConfigs)
        assertSame(saved, data.savedConfig)
    }

    @Test
    fun `review page tolerates a missing analysis config`() {
        whenever(reviewAnalysisConfigRepo.findReviewAnalysisConfig()).thenReturn(null)

        val service = ReviewPageQueryService(jiraConfigRepo, reviewAnalysisConfigRepo, demoConfigFilter)

        assertEquals(emptyList<JiraConfig>(), service.getReviewAnalysePageData().jiraConfigs)
    }
}
