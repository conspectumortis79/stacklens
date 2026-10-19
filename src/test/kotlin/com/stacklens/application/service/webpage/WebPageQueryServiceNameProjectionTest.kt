package com.stacklens.application.service.webpage

import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.CodeQaAnalysisPort
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JiraTicketImplementationPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.testsupport.NameFilteringDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WebPageQueryServiceNameProjectionTest {
    private val demoConfigFilter = NameFilteringDemoConfigFilter.instance

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
    private val codeQaAnalysisRepo = mock<CodeQaAnalysisPort>()
    private val loggingFixPort = mock<LoggingFixPort>()

    private val retained = GitlabConfig(name = "gitlab-real", baseUrl = "https://gitlab", accessToken = "t", groupId = "g")
    private val dropped = GitlabConfig(name = "Demo gitlab", baseUrl = "https://gitlab", accessToken = "t", groupId = "g")

    @Test
    fun `config page drops demo profiles through the name projection`() {
        whenever(gitlabConfigRepo.findAllGitlabConfigs()).thenReturn(listOf(retained, dropped))
        whenever(
            dbConfigRepo.findAllDatabaseConfigs(),
        ).thenReturn(listOf(database("db-real"), database("Demo db")))
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(listOf(confluence("wiki"), confluence("Demo wiki")))
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(listOf(grafana("dash"), grafana("Demo dash")))
        whenever(jiraConfigRepo.findAllJiraConfigs()).thenReturn(listOf(jira("jira-real"), jira("Demo jira")))
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(github("gh"), github("Demo gh")))

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

        assertEquals(listOf(retained), data.gitlabConfigs)
        assertEquals(1, data.dbConfigs.size)
        assertEquals(1, data.confluenceConfigs.size)
        assertEquals(1, data.grafanaConfigs.size)
        assertEquals(1, data.jiraConfigs.size)
        assertEquals(1, data.githubRepoConfigs.size)
    }

    @Test
    fun `search page projects every repository name through the demo filter`() {
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(listOf(retained, dropped))
        whenever(dbConfigRepo.findByActiveTrue()).thenReturn(listOf(database("db-real"), database("Demo db")))
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(listOf(jira("jira-real"), jira("Demo jira")))
        whenever(confluenceConfigRepo.findByActiveTrue()).thenReturn(listOf(confluence("wiki"), confluence("Demo wiki")))
        whenever(githubRepoConfigRepo.findByActiveTrue()).thenReturn(listOf(github("gh"), github("Demo gh")))

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

        assertEquals(listOf(retained), data.gitlabConfigs)
        assertEquals(1, data.dbConfigs.size)
        assertEquals(1, data.jiraConfigs.size)
        assertEquals(1, data.confluenceConfigs.size)
        assertEquals(1, data.githubConfigs.size)
    }

    @Test
    fun `ticket page projects names for jira, gitlab, github and database profiles`() {
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(listOf(jira("jira-real"), jira("Demo jira")))
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(listOf(retained, dropped))
        whenever(dbConfigRepo.findByActiveTrue()).thenReturn(listOf(database("db-real"), database("Demo db")))
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(github("gh"), github("Demo gh")))
        whenever(localRepoConfigRepo.findAllByScope("TICKET_ANALYSIS")).thenReturn(listOf(local("local"), local("Demo local")))

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

        assertEquals(1, data.jiraConfigs.size)
        assertEquals(listOf(retained), data.gitlabConfigs)
        assertEquals(1, data.dbConfigs.size)
        assertEquals(1, data.githubRepoConfigs.size)
        assertEquals(2, data.localRepoConfigs.size)
    }

    @Test
    fun `review page projects the active jira name through the demo filter`() {
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(listOf(jira("jira-real"), jira("Demo jira")))

        val service = ReviewPageQueryService(jiraConfigRepo, reviewAnalysisConfigRepo, demoConfigFilter)

        assertEquals(1, service.getReviewAnalysePageData().jiraConfigs.size)
    }

    @Test
    fun `analysis pages project names for every integration they load`() {
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(listOf(retained, dropped))
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(listOf(jira("jira-real"), jira("Demo jira")))
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(
            listOf(github("gh"), GithubRepoConfig(name = "Demo gh", owner = "acme", repo = "x", active = false)),
        )
        whenever(localRepoConfigRepo.findAllByScope("CODE_ANALYSIS")).thenReturn(listOf(local("code")))

        val codeAnalyseService =
            CodeAnalysePageQueryService(localRepoConfigRepo, gitlabConfigRepo, githubRepoConfigRepo, demoConfigFilter)
        val lastTestAnalyseService =
            LastTestAnalysePageQueryService(gitlabConfigRepo, localRepoConfigRepo, githubRepoConfigRepo, demoConfigFilter)
        val securityAnalyseService =
            SecurityAnalysePageQueryService(localRepoConfigRepo, gitlabConfigRepo, githubRepoConfigRepo, demoConfigFilter)
        val codeQaService =
            CodeQaPageQueryService(jiraConfigRepo, gitlabConfigRepo, githubRepoConfigRepo, codeQaAnalysisRepo, demoConfigFilter)
        val pipelineAnalyseService =
            PipelineAnalysePageQueryService(localRepoConfigRepo, gitlabConfigRepo, githubRepoConfigRepo, demoConfigFilter)
        val loggingAnalyseService = LoggingAnalysePageQueryService(localRepoConfigRepo, loggingFixPort)

        assertEquals(listOf(retained), codeAnalyseService.getCodeAnalysePageData().gitlabConfigs)
        assertEquals(1, codeAnalyseService.getCodeAnalysePageData().githubRepoConfigs.size)
        assertEquals(1, lastTestAnalyseService.getLastTestAnalysePageData().gitlabConfigs.size)
        assertEquals(1, lastTestAnalyseService.getLastTestAnalysePageData().githubRepoConfigs.size)
        assertEquals(1, securityAnalyseService.getSecurityAnalysePageData().gitlabConfigs.size)
        assertEquals(1, securityAnalyseService.getSecurityAnalysePageData().githubRepoConfigs.size)
        assertEquals(1, pipelineAnalyseService.getPipelineAnalysePageData().gitlabConfigs.size)
        assertEquals(1, pipelineAnalyseService.getPipelineAnalysePageData().githubRepoConfigs.size)
        assertEquals(1, codeQaService.getCodeQaAnalysisPageData().jiraConfigs.size)
        assertEquals(1, codeQaService.getCodeQaAnalysisPageData().gitlabConfigs.size)
        assertEquals(1, codeQaService.getCodeQaAnalysisPageData().githubConfigs.size)
        assertEquals(0, loggingAnalyseService.getLoggingAnalysePageData().localRepoConfigs.size)
    }

    private fun database(name: String) =
        DatabaseConfig(
            name = name,
            dbType = DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            databaseName = "app",
            username = "u",
            password = "p",
        )

    private fun confluence(name: String) = ConfluenceConfig(name = name, baseUrl = "https://wiki", username = "u", apiToken = "t")

    private fun grafana(name: String) = GrafanaConfig(name = name, baseUrl = "https://grafana", username = "u", password = "p")

    private fun jira(name: String) = JiraConfig(name = name, baseUrl = "https://jira", username = "u", apiToken = "t")

    private fun github(name: String) = GithubRepoConfig(name = name, owner = "acme", repo = "shop")

    private fun local(name: String) = LocalRepoConfig(name = name, path = "/tmp/local")
}
