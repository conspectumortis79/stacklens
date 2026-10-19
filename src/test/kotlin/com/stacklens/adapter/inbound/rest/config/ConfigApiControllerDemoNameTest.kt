package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.GithubRepoApiController
import com.stacklens.adapter.inbound.rest.LocalRepoApiController
import com.stacklens.adapter.inbound.web.GrafanaAnalyseController
import com.stacklens.application.port.inbound.config.GithubConfigUseCase
import com.stacklens.application.port.inbound.config.GitlabConfigUseCase
import com.stacklens.application.port.inbound.config.JiraConfigUseCase
import com.stacklens.application.port.inbound.grafana.GrafanaAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.repo.GitlabConfigSummary
import com.stacklens.application.port.inbound.repo.LocalRepoUseCase
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.grafana.GrafanaConfigInfo
import com.stacklens.application.service.config.ConfluenceConfigService
import com.stacklens.application.service.config.DatabaseConfigService
import com.stacklens.application.service.config.GithubConfigService
import com.stacklens.application.service.config.GitlabConfigService
import com.stacklens.application.service.config.GrafanaConfigService
import com.stacklens.application.service.config.JiraConfigService
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.GrafanaConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.testsupport.NameFilteringDemoConfigFilter
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ui.ExtendedModelMap

class ConfigApiControllerDemoNameTest {
    private val hidden = NameFilteringDemoConfigFilter.instance
    private val shown = PassthroughDemoConfigFilter.instance
    private val i18n = mock<I18nUseCase>()

    @Test
    fun `the jira use case hides demo configs from the rest list`() {
        val repo = mock<JiraConfigPort>()
        whenever(repo.findAllJiraConfigs()).thenReturn(listOf(jira("Jira real"), jira("Demo jira")))
        val controller = JiraConfigApiController(JiraConfigService(repo, hidden, mock()))

        val dtos = controller.listJiraConfigs()

        assertEquals(listOf("Jira real"), dtos.map { it.name })
    }

    @Test
    fun `the database use case hides demo configs from the rest list`() {
        val repo = mock<DatabaseConfigPort>()
        whenever(repo.findAllDatabaseConfigs()).thenReturn(listOf(database("Db real"), database("Demo db")))
        val controller = DatabaseConfigApiController(DatabaseConfigService(repo, mock(), hidden), i18n)

        assertEquals(listOf("Db real"), controller.listDbConfigs().map { it.name })
    }

    @Test
    fun `the gitlab use case hides demo configs from the rest list`() {
        val repo = mock<GitlabConfigPort>()
        whenever(repo.findAllGitlabConfigs()).thenReturn(listOf(gitlab("Gitlab real"), gitlab("Demo gitlab")))
        val controller = GitlabConfigApiController(GitlabConfigService(repo, mock(), hidden))

        assertEquals(listOf("Gitlab real"), controller.listGitlabConfigs().map { it.name })
    }

    @Test
    fun `the grafana use case hides demo configs from the rest list`() {
        val repo = mock<GrafanaConfigPort>()
        whenever(repo.findAllGrafanaConfigs()).thenReturn(listOf(grafana("Grafana real"), grafana("Demo grafana")))
        val controller = GrafanaConfigApiController(GrafanaConfigService(repo, hidden, mock()))

        assertEquals(listOf("Grafana real"), controller.listGrafanaConfigs().map { it.name })
    }

    @Test
    fun `the confluence use case hides demo configs from the rest list`() {
        val repo = mock<ConfluenceConfigPort>()
        whenever(repo.findAllConfluenceConfigs()).thenReturn(listOf(confluence("Wiki real"), confluence("Demo wiki")))
        val controller = ConfluenceConfigApiController(ConfluenceConfigService(repo, hidden, mock()))

        assertEquals(listOf("Wiki real"), controller.listConfluenceConfigs().map { it.name })
    }

    @Test
    fun `the github use case hides demo repos from the rest list`() {
        val repo = mock<GithubRepoConfigPort>()
        whenever(repo.findAllGithubRepoConfigs()).thenReturn(
            listOf(
                GithubRepoConfig(name = "Gh real", owner = "acme", repo = "shop"),
                GithubRepoConfig(name = "Demo gh", owner = "acme", repo = "shop"),
            ),
        )
        val controller = GithubRepoApiController(GithubConfigService(repo, mock(), mock(), hidden), i18n)

        val rows = controller.list()

        assertEquals(listOf("Gh real"), rows.map { it["name"] })
    }

    @Test
    fun `the analyse page model asks the use case for the visible configs`() {
        val useCase = mock<GrafanaAnalysisUseCase>()
        whenever(useCase.listVisibleConfigs()).thenReturn(listOf(grafanaInfo("Grafana real")))
        val controller = GrafanaAnalyseController(useCase)
        val model = ExtendedModelMap()

        val view = controller.grafanaAnalyse(model)

        assertEquals("grafana-analysis", view)
        assertEquals(
            listOf("Grafana real"),
            (model.asMap()["grafanaConfigs"] as List<*>).filterIsInstance<GrafanaConfigInfo>().map { it.name },
        )
    }

    @Test
    fun `the local repo endpoint asks the use case for the visible gitlab configs`() {
        val useCase = mock<LocalRepoUseCase>()
        whenever(useCase.listVisibleActiveGitlabConfigs()).thenReturn(listOf(summary("Gitlab real")))
        val controller = LocalRepoApiController(useCase, i18n, mock(), mock())

        assertEquals(listOf("Gitlab real"), controller.listGitlabConfigs().map { it["name"] })
    }

    @Test
    fun `the test environment keeps every config visible`() {
        val repo = mock<JiraConfigPort>()
        whenever(repo.findAllJiraConfigs()).thenReturn(listOf(jira("Jira real"), jira("Demo jira")))

        assertEquals(2, JiraConfigService(repo, shown, mock()).listVisibleJiraConfigs().size)
    }

    @Test
    fun `controllers delegate to the visible list of their use case`() {
        val jiraUseCase = mock<JiraConfigUseCase>()
        whenever(jiraUseCase.listVisibleJiraConfigs()).thenReturn(listOf(jira("Only visible")))
        assertEquals(listOf("Only visible"), JiraConfigApiController(jiraUseCase).listJiraConfigs().map { it.name })

        val gitlabUseCase = mock<GitlabConfigUseCase>()
        whenever(gitlabUseCase.listVisibleGitlabConfigs()).thenReturn(listOf(gitlab("Only visible")))
        assertEquals(1, GitlabConfigApiController(gitlabUseCase).listGitlabConfigs().size)

        val githubUseCase = mock<GithubConfigUseCase>()
        whenever(githubUseCase.listVisibleGithubRepoConfigs()).thenReturn(
            listOf(GithubRepoConfig(name = "Only visible", owner = "acme", repo = "shop")),
        )
        assertEquals(1, GithubRepoApiController(githubUseCase, i18n).list().size)
    }

    private fun jira(name: String) = JiraConfig(name = name, baseUrl = "https://jira", username = "u", apiToken = "t")

    private fun confluence(name: String) = ConfluenceConfig(name = name, baseUrl = "https://wiki", username = "u", apiToken = "t")

    private fun grafana(name: String) = GrafanaConfig(name = name, baseUrl = "https://gf", username = "u", password = "p", apiToken = "t", orgId = 1L)

    private fun gitlab(name: String) = GitlabConfig(name = name, baseUrl = "https://gl", accessToken = "t")

    private fun database(name: String) =
        DatabaseConfig(
            name = name,
            dbType = com.stacklens.domain.enums.DatabaseType.MYSQL,
            host = "localhost",
            port = 3306,
            username = "root",
            password = "",
            databaseName = "db",
        )

    private fun grafanaInfo(name: String) = GrafanaConfigInfo(1L, name, "https://gf", "u", "p", "t", 1L)

    private fun summary(name: String) = GitlabConfigSummary(1L, name, "https://gl")
}
