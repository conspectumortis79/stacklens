package com.stacklens.application.service.webpage

import com.stacklens.application.port.out.CodeQaAnalysisPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.LocalRepoConfig
import com.stacklens.domain.model.LoggingFixAnalysis
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AnalysisPageQueryServiceTest {
    private val gitlabConfigRepo: GitlabConfigPort = mock()
    private val jiraConfigRepo: JiraConfigPort = mock()
    private val localRepoConfigRepo: LocalRepoConfigPort = mock()
    private val githubRepoConfigRepo: GithubRepoConfigPort = mock()
    private val codeQaAnalysisRepo: CodeQaAnalysisPort = mock()
    private val loggingFixPort: LoggingFixPort = mock()

    private val demoFilter = PassthroughDemoConfigFilter.instance

    private val codeAnalyseService =
        CodeAnalysePageQueryService(localRepoConfigRepo, gitlabConfigRepo, githubRepoConfigRepo, demoFilter)

    private val lastTestAnalyseService =
        LastTestAnalysePageQueryService(gitlabConfigRepo, localRepoConfigRepo, githubRepoConfigRepo, demoFilter)

    private val securityAnalyseService =
        SecurityAnalysePageQueryService(localRepoConfigRepo, gitlabConfigRepo, githubRepoConfigRepo, demoFilter)

    private val codeQaService =
        CodeQaPageQueryService(jiraConfigRepo, gitlabConfigRepo, githubRepoConfigRepo, codeQaAnalysisRepo, demoFilter)

    private val pipelineAnalyseService =
        PipelineAnalysePageQueryService(localRepoConfigRepo, gitlabConfigRepo, githubRepoConfigRepo, demoFilter)

    private val loggingAnalyseService =
        LoggingAnalysePageQueryService(localRepoConfigRepo, loggingFixPort)

    private fun gitlabConfig() = GitlabConfig(id = 1L, name = "gl", baseUrl = "https://gl.example.com", accessToken = "tok")

    private fun jiraConfig() = JiraConfig(id = 2L, name = "jira", baseUrl = "https://jira.example.com", apiToken = "t")

    private fun localRepoConfig(scope: String) = LocalRepoConfig(id = 3L, name = "repo", path = "/repo", scope = scope)

    private fun githubRepoConfig() = GithubRepoConfig(id = 4L, name = "gh", owner = "acme", repo = "svc")

    @Test
    fun `getCodeAnalysePageData returns local repos gitlab configs and filters github to active`() {
        val localRepos = listOf(localRepoConfig("CODE_ANALYSIS"))
        val gitlabConfigs = listOf(gitlabConfig())
        val active = githubRepoConfig().also { it.active = true }
        val inactive = GithubRepoConfig(id = 9L, name = "off", owner = "x", repo = "y").also { it.active = false }
        whenever(localRepoConfigRepo.findAllByScope("CODE_ANALYSIS")).thenReturn(localRepos)
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(gitlabConfigs)
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(active, inactive))

        val data = codeAnalyseService.getCodeAnalysePageData()

        assertEquals(localRepos, data.localRepoConfigs)
        assertEquals(gitlabConfigs, data.gitlabConfigs)
        assertEquals(1, data.githubRepoConfigs.size)
        assertTrue(data.githubRepoConfigs[0].active)
    }

    @Test
    fun `getLastTestAnalysePageData returns active gitlab configs, all local repos and filtered github repos`() {
        val gitlabConfigs = listOf(gitlabConfig())
        val activeLocal = localRepoConfig("LOAD_TEST_ANALYSIS").also { it.active = true }
        val inactiveLocal = LocalRepoConfig(id = 8L, name = "off", path = "/off", scope = "LOAD_TEST_ANALYSIS").also { it.active = false }
        val activeGithub = githubRepoConfig().also { it.active = true }
        val inactiveGithub = GithubRepoConfig(id = 99L, name = "off", owner = "x", repo = "y").also { it.active = false }
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(gitlabConfigs)
        whenever(localRepoConfigRepo.findAllByScope("LOAD_TEST_ANALYSIS")).thenReturn(listOf(activeLocal, inactiveLocal))
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(activeGithub, inactiveGithub))

        val data = lastTestAnalyseService.getLastTestAnalysePageData()

        assertEquals(gitlabConfigs, data.gitlabConfigs)
        assertEquals(listOf(activeLocal, inactiveLocal), data.localRepoConfigs)
        assertEquals(1, data.githubRepoConfigs.size)
        assertTrue(data.githubRepoConfigs[0].active)
    }

    @Test
    fun `getSecurityAnalysePageData returns local repos for BUG_ANALYSIS gitlab and all github repos`() {
        val localRepos = listOf(localRepoConfig("BUG_ANALYSIS"))
        val gitlabConfigs = listOf(gitlabConfig())
        val githubRepos =
            listOf(
                githubRepoConfig(),
                GithubRepoConfig(id = 9L, name = "off", owner = "x", repo = "y").also {
                    it.active =
                        false
                },
            )
        whenever(localRepoConfigRepo.findAllByScope("BUG_ANALYSIS")).thenReturn(localRepos)
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(gitlabConfigs)
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(githubRepos)

        val data = securityAnalyseService.getSecurityAnalysePageData()

        assertEquals(localRepos, data.localRepoConfigs)
        assertEquals(gitlabConfigs, data.gitlabConfigs)
        assertEquals(2, data.githubRepoConfigs.size)
    }

    @Test
    fun `getPipelineAnalysePageData returns local repos for PIPELINE_ANALYSIS plus active github and gitlab`() {
        val localRepos = listOf(localRepoConfig("PIPELINE_ANALYSIS"))
        val gitlabConfigs = listOf(gitlabConfig())
        val active = githubRepoConfig().also { it.active = true }
        val inactive = GithubRepoConfig(id = 99L, name = "off", owner = "x", repo = "y").also { it.active = false }
        whenever(localRepoConfigRepo.findAllByScope("PIPELINE_ANALYSIS")).thenReturn(localRepos)
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(gitlabConfigs)
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(active, inactive))

        val data = pipelineAnalyseService.getPipelineAnalysePageData()

        assertEquals(localRepos, data.localRepoConfigs)
        assertEquals(gitlabConfigs, data.gitlabConfigs)
        assertEquals(1, data.githubRepoConfigs.size)
        assertTrue(data.githubRepoConfigs[0].active)
    }

    @Test
    fun `getCodeQaAnalysisPageData returns jira gitlab active-only github and 10 most recent analyses`() {
        val jiraConfigs = listOf(jiraConfig())
        val gitlabConfigs = listOf(gitlabConfig())
        val active = githubRepoConfig().also { it.active = true }
        val inactive = GithubRepoConfig(id = 99L, name = "off", owner = "x", repo = "y").also { it.active = false }
        val analyses = (1..15).map { CodeQaAnalysis(mrUrl = "https://gl.example.com/g/r/-/merge_requests/$it") }
        whenever(jiraConfigRepo.findByActiveTrue()).thenReturn(jiraConfigs)
        whenever(gitlabConfigRepo.findByActiveTrue()).thenReturn(gitlabConfigs)
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(active, inactive))
        whenever(codeQaAnalysisRepo.findAllByOrderByCreatedAtDesc()).thenReturn(analyses)

        val data = codeQaService.getCodeQaAnalysisPageData()

        assertEquals(jiraConfigs, data.jiraConfigs)
        assertEquals(gitlabConfigs, data.gitlabConfigs)
        assertEquals(1, data.githubConfigs.size)
        assertTrue(data.githubConfigs[0].active)
        assertEquals(10, data.recentAnalyses.size)
    }

    @Test
    fun `getLoggingAnalysePageData returns active local repos in LOGGING_ANALYSIS scope and recent fix analyses`() {
        val activeLocal = localRepoConfig("LOGGING_ANALYSIS").also { it.active = true }
        val inactiveLocal = LocalRepoConfig(id = 99L, name = "off", path = "/off", scope = "LOGGING_ANALYSIS").also { it.active = false }
        val recent =
            listOf(
                LoggingFixAnalysis(featureSlug = "x", logText = "y", selectedCategories = "error"),
            )
        whenever(localRepoConfigRepo.findAllByScope("LOGGING_ANALYSIS")).thenReturn(listOf(activeLocal, inactiveLocal))
        whenever(loggingFixPort.findRecentLoggingFixAnalyses(10)).thenReturn(recent)

        val data = loggingAnalyseService.getLoggingAnalysePageData()

        assertEquals(1, data.localRepoConfigs.size)
        assertTrue(data.localRepoConfigs[0].active)
        assertEquals(recent, data.recentAnalyses)
    }
}
