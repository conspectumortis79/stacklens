package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.web.CodeAnalysePageData
import com.stacklens.application.port.inbound.web.CodeAnalysePageQueryUseCase
import com.stacklens.application.port.inbound.web.CodeQaAnalysisPageData
import com.stacklens.application.port.inbound.web.CodeQaPageQueryUseCase
import com.stacklens.application.port.inbound.web.LastTestAnalysePageData
import com.stacklens.application.port.inbound.web.LastTestAnalysePageQueryUseCase
import com.stacklens.application.port.inbound.web.PipelineAnalysePageData
import com.stacklens.application.port.inbound.web.PipelineAnalysePageQueryUseCase
import com.stacklens.application.port.inbound.web.SecurityAnalysePageData
import com.stacklens.application.port.inbound.web.SecurityAnalysePageQueryUseCase
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.LocalRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ui.ExtendedModelMap

class AnalysisWebControllerTest {
    private val codeAnalysePageQuery: CodeAnalysePageQueryUseCase = mock()
    private val lastTestAnalysePageQuery: LastTestAnalysePageQueryUseCase = mock()
    private val securityAnalysePageQuery: SecurityAnalysePageQueryUseCase = mock()
    private val codeQaPageQuery: CodeQaPageQueryUseCase = mock()
    private val pipelineAnalysePageQuery: PipelineAnalysePageQueryUseCase = mock()
    private val controller =
        AnalysisWebController(
            codeAnalysePageQuery,
            lastTestAnalysePageQuery,
            securityAnalysePageQuery,
            codeQaPageQuery,
            pipelineAnalysePageQuery,
        )

    private fun localRepo(id: Long = 1L) = LocalRepoConfig(id = id, name = "r$id", path = "/p$id", scope = "x")

    private fun gitlab(id: Long = 1L) = GitlabConfig(id = id, name = "g$id", baseUrl = "u", accessToken = "t")

    private fun github(id: Long = 1L) = GithubRepoConfig(id = id, name = "gh$id", owner = "o", repo = "r")

    private fun jira(id: Long = 1L) = JiraConfig(id = id, name = "j$id", baseUrl = "u", apiToken = "t")

    private fun analysis(id: Long = 1L) = CodeQaAnalysis(mrUrl = "u/$id")

    @Suppress("unused")
    private fun keepLocal(): List<LocalRepoConfig> = listOf(localRepo())

    @Test
    fun `codeAnalysePage adds data to model and returns the code-analysis view`() {
        val local = listOf(localRepo())
        val gitlab = listOf(gitlab())
        val github = listOf(github())
        whenever(codeAnalysePageQuery.getCodeAnalysePageData()).thenReturn(CodeAnalysePageData(local, gitlab, github))

        val model = ExtendedModelMap()
        val view = controller.codeAnalysePage(model)

        assertEquals("code-analysis", view)
        assertSame(local, model.getAttribute("localRepoConfigs"))
        assertSame(gitlab, model.getAttribute("gitlabConfigs"))
        assertSame(github, model.getAttribute("githubRepoConfigs"))
    }

    @Test
    fun `lastTestAnalysePage adds data to model and returns the lasttest-analysis view`() {
        val local = listOf(localRepo())
        val gitlab = listOf(gitlab())
        val github = listOf(github())
        whenever(lastTestAnalysePageQuery.getLastTestAnalysePageData()).thenReturn(LastTestAnalysePageData(gitlab, local, github))

        val model = ExtendedModelMap()
        val view = controller.lastTestAnalysePage(model)

        assertEquals("lasttest-analysis", view)
        assertSame(gitlab, model.getAttribute("gitlabConfigs"))
        assertSame(local, model.getAttribute("localRepoConfigs"))
        assertSame(github, model.getAttribute("githubRepoConfigs"))
    }

    @Test
    fun `securityAnalysePage adds data to model and returns the security-analysis view`() {
        val local = listOf(localRepo())
        val gitlab = listOf(gitlab())
        val github = listOf(github())
        whenever(securityAnalysePageQuery.getSecurityAnalysePageData()).thenReturn(SecurityAnalysePageData(local, gitlab, github))

        val model = ExtendedModelMap()
        val view = controller.securityAnalysePage(model)

        assertEquals("security-analysis", view)
        assertSame(local, model.getAttribute("localRepoConfigs"))
        assertSame(gitlab, model.getAttribute("gitlabConfigs"))
        assertSame(github, model.getAttribute("githubRepoConfigs"))
    }

    @Test
    fun `codeQaPage adds data to model and returns the code-qa view`() {
        val local = listOf(jira())
        val gitlab = listOf(gitlab())
        val github = listOf(github())
        val recent = listOf(analysis())
        whenever(codeQaPageQuery.getCodeQaAnalysisPageData()).thenReturn(CodeQaAnalysisPageData(local, gitlab, github, recent))

        val model = ExtendedModelMap()
        val view = controller.codeQaPage(model)

        assertEquals("code-qa", view)
        assertSame(local, model.getAttribute("jiraConfigs"))
        assertSame(gitlab, model.getAttribute("gitlabConfigs"))
        assertSame(recent, model.getAttribute("recentAnalyses"))
    }

    @Test
    fun `pipelineAnalysePage adds data to model and returns the pipeline-analysis view`() {
        val local = listOf(localRepo())
        val gitlab = listOf(gitlab())
        val github = listOf(github())
        whenever(pipelineAnalysePageQuery.getPipelineAnalysePageData()).thenReturn(PipelineAnalysePageData(local, gitlab, github))

        val model = ExtendedModelMap()
        val view = controller.pipelineAnalysePage(model)

        assertEquals("pipeline-analysis", view)
        assertSame(local, model.getAttribute("localRepoConfigs"))
        assertSame(gitlab, model.getAttribute("gitlabConfigs"))
        assertSame(github, model.getAttribute("githubRepoConfigs"))
    }
}
