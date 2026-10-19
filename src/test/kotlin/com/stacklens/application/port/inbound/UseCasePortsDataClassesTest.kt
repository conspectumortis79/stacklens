package com.stacklens.application.port.inbound

import com.stacklens.application.port.inbound.analysis.CodeAnalysisUseCase
import com.stacklens.application.port.inbound.loadtest.K6StatusResult
import com.stacklens.application.port.inbound.loadtest.LoadTestAnalysisUseCase
import com.stacklens.application.port.inbound.mcp.McpCallResult
import com.stacklens.application.port.inbound.mcp.McpToolCallRequest
import com.stacklens.application.port.inbound.mcp.McpToolSummary
import com.stacklens.application.port.inbound.mcp.McpToolsResult
import com.stacklens.application.port.inbound.platform.AppKnowledgeUseCase
import com.stacklens.application.port.inbound.prompt.PromptContent
import com.stacklens.application.port.inbound.prompt.PromptGroupSummary
import com.stacklens.application.port.inbound.repo.GitlabCloneResult
import com.stacklens.application.port.inbound.repo.GitlabConfigSummary
import com.stacklens.application.port.inbound.repo.GitlabProjectSummary
import com.stacklens.application.port.inbound.search.SearchCommand
import com.stacklens.application.port.inbound.security.SecurityAnalysisUseCase
import com.stacklens.application.port.inbound.tooling.ConfluenceConfigSummary
import com.stacklens.application.port.inbound.tooling.ToolingCodeEditUseCase
import com.stacklens.application.port.inbound.web.CodeAnalysePageData
import com.stacklens.application.port.inbound.web.ReviewAnalysePageData
import com.stacklens.application.port.inbound.web.SecurityAnalysePageData
import com.stacklens.application.port.inbound.web.SuchePageData
import com.stacklens.application.port.out.grafana.GrafanaConfigInfo
import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.domain.model.SecurityAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UseCasePortsDataClassesTest {
    @Test
    fun `SearchCommand has correct defaults`() {
        val cmd = SearchCommand(keywords = listOf("test"))
        assertEquals(listOf("test"), cmd.keywords)
        assertFalse(cmd.includeGitlab)
        assertFalse(cmd.includeDatabase)
        assertFalse(cmd.includePipeline)
        assertFalse(cmd.includeConfluence)
        assertFalse(cmd.includeGrafana)
        assertFalse(cmd.includeJira)
        assertFalse(cmd.includeContextMap)
        assertFalse(cmd.crossReferenceEnabled)
        assertTrue(cmd.gitlabConfigIds.isEmpty())
        assertTrue(cmd.databaseConfigIds.isEmpty())
    }

    @Test
    fun `SearchCommand with all options enabled`() {
        val cmd =
            SearchCommand(
                keywords = listOf("test"),
                includeGitlab = true,
                includeDatabase = true,
                includePipeline = true,
                includeConfluence = true,
                includeGrafana = true,
                includeJira = true,
                includeContextMap = true,
                crossReferenceEnabled = true,
                gitlabConfigIds = listOf(1, 2),
                databaseConfigIds = listOf(3),
                confluenceConfigIds = listOf(4),
                grafanaConfigIds = listOf(5),
                jiraConfigIds = listOf(6),
                gitlabRepositories = listOf("group/repo"),
                jiraProjects = listOf("PROJ"),
                jiraComponents = listOf("backend"),
                jiraLabels = listOf("bug"),
                confluenceWebsites = listOf("https://confluence.com"),
                grafanaPages = listOf("/d/abc"),
                pipelineFilters = listOf("deploy"),
            )
        assertTrue(cmd.includeGitlab)
        assertTrue(cmd.includeDatabase)
        assertTrue(cmd.includePipeline)
        assertTrue(cmd.includeConfluence)
        assertTrue(cmd.includeGrafana)
        assertTrue(cmd.includeJira)
        assertTrue(cmd.includeContextMap)
        assertTrue(cmd.crossReferenceEnabled)
        assertEquals(2, cmd.gitlabConfigIds.size)
        assertEquals(1, cmd.databaseConfigIds.size)
        assertEquals(1, cmd.confluenceConfigIds.size)
        assertEquals(1, cmd.grafanaConfigIds.size)
        assertEquals(1, cmd.jiraConfigIds.size)
        assertEquals(1, cmd.gitlabRepositories.size)
        assertEquals(1, cmd.jiraProjects.size)
        assertEquals(1, cmd.jiraComponents.size)
        assertEquals(1, cmd.jiraLabels.size)
        assertEquals(1, cmd.confluenceWebsites.size)
        assertEquals(1, cmd.grafanaPages.size)
        assertEquals(1, cmd.pipelineFilters.size)
    }

    @Test
    fun `K6StatusResult running with null exitCode`() {
        val result = K6StatusResult(running = true, exitCode = null, output = "processing...", error = "", summaryJson = null)
        assertTrue(result.running)
        assertNull(result.exitCode)
        assertEquals("processing...", result.output)
        assertEquals("", result.error)
        assertNull(result.summaryJson)
    }

    @Test
    fun `K6StatusResult completed with exitCode`() {
        val result = K6StatusResult(running = false, exitCode = 0, output = "done", error = "", summaryJson = """{"total":100}""")
        assertFalse(result.running)
        assertEquals(0, result.exitCode)
        assertEquals("done", result.output)
        assertEquals("", result.error)
        assertEquals("""{"total":100}""", result.summaryJson)
    }

    @Test
    fun `GrafanaConfigInfo creates correctly`() {
        val info =
            GrafanaConfigInfo(
                id = 1L,
                name = "Test Grafana",
                baseUrl = "https://grafana.example.com",
                username = "admin",
                password = "secret",
                apiToken = "token123",
                orgId = 1L,
            )
        assertEquals(1L, info.id)
        assertEquals("Test Grafana", info.name)
        assertEquals("https://grafana.example.com", info.baseUrl)
        assertEquals("admin", info.username)
        assertEquals("secret", info.password)
        assertEquals("token123", info.apiToken)
        assertEquals(1L, info.orgId)
    }

    @Test
    fun `GitlabConfigSummary creates correctly`() {
        val summary = GitlabConfigSummary(id = 5L, name = "My GitLab", baseUrl = "https://gitlab.com")
        assertEquals(5L, summary.id)
        assertEquals("My GitLab", summary.name)
        assertEquals("https://gitlab.com", summary.baseUrl)
    }

    @Test
    fun `GitlabProjectSummary creates correctly`() {
        val summary =
            GitlabProjectSummary(
                id = 42L,
                name = "my-project",
                pathWithNamespace = "group/my-project",
                httpUrlToRepo = "https://gitlab.com/group/my-project.git",
            )
        assertEquals(42L, summary.id)
        assertEquals("my-project", summary.name)
        assertEquals("group/my-project", summary.pathWithNamespace)
        assertEquals("https://gitlab.com/group/my-project.git", summary.httpUrlToRepo)
    }

    @Test
    fun `PromptGroupSummary creates correctly`() {
        val group = PromptGroupSummary(label = "Search Prompts", mainKey = "search.main")
        assertEquals("Search Prompts", group.label)
        assertEquals("search.main", group.mainKey)
    }

    @Test
    fun `PromptContent creates correctly`() {
        val content =
            PromptContent(
                key = "search.main",
                content = "Custom prompt",
                isCustom = true,
                defaultContent = "Default prompt",
            )
        assertEquals("search.main", content.key)
        assertEquals("Custom prompt", content.content)
        assertTrue(content.isCustom)
        assertEquals("Default prompt", content.defaultContent)
    }

    @Test
    fun `CodeAnalysePageData creates correctly`() {
        val data =
            CodeAnalysePageData(
                localRepoConfigs = emptyList(),
                gitlabConfigs = emptyList(),
            )
        assertTrue(data.localRepoConfigs.isEmpty())
        assertTrue(data.gitlabConfigs.isEmpty())
    }

    @Test
    fun `SuchePageData creates correctly`() {
        val data =
            SuchePageData(
                gitlabConfigs = emptyList(),
                dbConfigs = emptyList(),
                jiraConfigs = emptyList(),
                confluenceConfigs = emptyList(),
            )
        assertTrue(data.gitlabConfigs.isEmpty())
        assertTrue(data.dbConfigs.isEmpty())
        assertTrue(data.jiraConfigs.isEmpty())
        assertTrue(data.confluenceConfigs.isEmpty())
    }

    @Test
    fun `ReviewAnalysePageData creates correctly with null config`() {
        val data =
            ReviewAnalysePageData(
                jiraConfigs = emptyList(),
                savedConfig = null,
            )
        assertTrue(data.jiraConfigs.isEmpty())
        assertNull(data.savedConfig)
    }

    @Test
    fun `SecurityAnalysePageData creates correctly with default empty list`() {
        val data =
            SecurityAnalysePageData(
                localRepoConfigs = emptyList(),
                gitlabConfigs = emptyList(),
            )
        assertTrue(data.localRepoConfigs.isEmpty())
        assertTrue(data.githubRepoConfigs.isEmpty())
    }

    @Test
    fun `GitlabCloneResult Success creates correctly`() {
        val result =
            GitlabCloneResult.Success(
                repoId = 1L,
                repoName = "my-repo",
                repoPath = "/path/to/repo",
                active = true,
                scope = "local",
            )
        assertEquals(1L, result.repoId)
        assertEquals("my-repo", result.repoName)
        assertEquals("/path/to/repo", result.repoPath)
        assertTrue(result.active)
        assertEquals("local", result.scope)
    }

    @Test
    fun `GitlabCloneResult BadRequest creates correctly`() {
        val result = GitlabCloneResult.BadRequest(error = "Invalid parameters")
        assertEquals("Invalid parameters", result.error)
    }

    @Test
    fun `GitlabCloneResult ExecutionError creates correctly`() {
        val result = GitlabCloneResult.ExecutionError(error = "Clone failed")
        assertEquals("Clone failed", result.error)
    }

    @Test
    fun `GitlabCloneResult ConfigNotFound is singleton`() {
        val result1 = GitlabCloneResult.ConfigNotFound
        val result2 = GitlabCloneResult.ConfigNotFound
        assertTrue(result1 === result2)
    }

    @Test
    fun `GitlabCloneResult when expression covers all cases`() {
        fun describe(result: GitlabCloneResult): String =
            when (result) {
                is GitlabCloneResult.Success -> "Success: ${result.repoName}"
                is GitlabCloneResult.BadRequest -> "BadRequest: ${result.error}"
                is GitlabCloneResult.ExecutionError -> "ExecutionError: ${result.error}"
                GitlabCloneResult.ConfigNotFound -> "ConfigNotFound"
            }
        assertEquals("Success: my-repo", describe(GitlabCloneResult.Success(1L, "my-repo", "/path", true, "local")))
        assertEquals("BadRequest: bad", describe(GitlabCloneResult.BadRequest("bad")))
        assertEquals("ExecutionError: fail", describe(GitlabCloneResult.ExecutionError("fail")))
        assertEquals("ConfigNotFound", describe(GitlabCloneResult.ConfigNotFound))
    }

    @Test
    fun `Tooling ConfluenceConfigSummary creates and equals`() {
        val s1 = ConfluenceConfigSummary(id = 1L, name = "Confluence", baseUrl = "http://confluence")
        val s2 = ConfluenceConfigSummary(id = 1L, name = "Confluence", baseUrl = "http://confluence")
        assertEquals(s1, s2)
        assertEquals(1L, s1.id)
        assertEquals("Confluence", s1.name)
        assertEquals("http://confluence", s1.baseUrl)
    }

    @Test
    fun `Tooling ConfluenceConfigSummary copy`() {
        val s = ConfluenceConfigSummary(1L, "old", "http://old")
        val copy = s.copy(name = "new")
        assertEquals("new", copy.name)
        assertEquals("old", s.name)
    }

    @Test
    fun `CodeAnalysisUseCase startLocalAnalysis with default params`() {
        val useCase =
            object : CodeAnalysisUseCase {
                override fun startLocalAnalysis(
                    repoConfigId: Long,
                    includeMermaid: Boolean,
                    includeDrawio: Boolean,
                ) = CodeAnalysis()

                override fun startGitlabAnalysis(
                    gitlabConfigId: Long,
                    gitlabProjectId: Long,
                    projectPath: String,
                    includeMermaid: Boolean,
                    includeDrawio: Boolean,
                ) = listOf<CodeAnalysis>()

                override fun startGithubAnalysis(
                    githubRepoConfigId: Long,
                    includeMermaid: Boolean,
                    includeDrawio: Boolean,
                ) = CodeAnalysis()

                override fun createMr(
                    analysisId: Long,
                    gitlabConfigId: Long,
                    projectId: Long,
                ) = ""

                override fun getAnalysis(analysisId: Long): CodeAnalysis? = null

                override fun getLatestLocalAnalysis(repoConfigId: Long): CodeAnalysis? = null

                override fun getLatestGitlabAnalysis(
                    gitlabConfigId: Long,
                    gitlabProjectId: Long,
                ): CodeAnalysis? = null

                override fun getLatestGithubAnalysis(githubRepoConfigId: Long): CodeAnalysis? = null

                override fun getAllAnalyses() = emptyList<CodeAnalysis>()

                override fun deleteAnalysis(analysisId: Long) {}

                override fun cancelAnalysis(analysisId: Long) {}

                override fun cancelLatestRunningForRepo(repoConfigId: Long): Long? = null
            }
        val result = useCase.startLocalAnalysis(1L)
        assertEquals(0L, result.id)
        useCase.startGitlabAnalysis(1L, 2L, "group/repo")
        useCase.startGithubAnalysis(1L)
    }

    @Test
    fun `SecurityAnalysisUseCase with default iterations`() {
        val useCase =
            object : SecurityAnalysisUseCase {
                override fun startLocalAnalysis(
                    repoConfigId: Long,
                    iterations: Int,
                ) = SecurityAnalysis()

                override fun startGitlabAnalysis(
                    gitlabConfigId: Long,
                    gitlabProjectId: Long,
                    projectPath: String,
                    iterations: Int,
                ) = SecurityAnalysis()

                override fun startGithubAnalysis(
                    githubRepoConfigId: Long,
                    iterations: Int,
                ) = SecurityAnalysis()

                override fun getAnalysis(analysisId: Long): SecurityAnalysis? = null

                override fun getAllAnalyses() = emptyList<SecurityAnalysis>()

                override fun deleteAnalysis(analysisId: Long) {}

                override fun cancelAnalysis(analysisId: Long) {}

                override fun generateFix(
                    analysisId: Long,
                    findingId: String,
                ) = com.stacklens.domain.model.security
                    .SecurityFixResult(null, null, "", emptyList(), false, null)
            }
        useCase.startLocalAnalysis(1L)
        useCase.startGitlabAnalysis(1L, 2L, "group/repo")
        useCase.startGithubAnalysis(1L)
    }

    @Test
    fun `LoadTestAnalysisUseCase startK6Script with defaults`() {
        val useCase =
            object : LoadTestAnalysisUseCase {
                override fun startGitlabAnalysis(
                    gitlabConfigId: Long,
                    gitlabProjectId: Long,
                    projectPath: String,
                ) = LoadTestAnalysis()

                override fun startLocalAnalysis(repoConfigId: Long) = LoadTestAnalysis()

                override fun startGithubAnalysis(githubRepoConfigId: Long) = LoadTestAnalysis()

                override fun getAnalysis(analysisId: Long): LoadTestAnalysis? = null

                override fun getLatestGitlabAnalysis(
                    gitlabConfigId: Long,
                    gitlabProjectId: Long,
                ): LoadTestAnalysis? = null

                override fun getLatestLocalAnalysis(repoConfigId: Long): LoadTestAnalysis? = null

                override fun getLatestGithubAnalysis(githubRepoConfigId: Long): LoadTestAnalysis? = null

                override fun getAllAnalyses() = emptyList<LoadTestAnalysis>()

                override fun deleteAnalysis(analysisId: Long) {}

                override fun cancelAnalysis(analysisId: Long) {}

                override fun startK6Script(
                    analysisId: Long,
                    bearerToken: String?,
                    environmentName: String?,
                    customScript: String?,
                ) = K6StatusResult(false, 0, "", "")

                override fun getK6Status(analysisId: Long) = K6StatusResult(false, 0, "", "")

                override fun stopK6Script(analysisId: Long) {}
            }
        val r = useCase.startK6Script(1L)
        assertFalse(r.running)
    }

    @Test
    fun `AppKnowledgeUseCase relevant with defaults`() {
        val useCase =
            object : AppKnowledgeUseCase {
                override fun index() = "index"

                override fun relevant(
                    query: String,
                    maxFiles: Int,
                    perFileChars: Int,
                ) = "result"

                override fun readme(): String? = null
            }
        assertEquals("result", useCase.relevant("my query"))
    }

    @Test
    fun `ToolingCodeEditUseCase aiReworkCode default newName parameter`() {
        val tool =
            com.stacklens.domain.model
                .ConfluenceTool(name = "t", confluencePageUrl = "http://x")
        val useCase =
            object : ToolingCodeEditUseCase {
                override fun aiReworkCode(
                    toolId: Long,
                    instructions: String,
                    newName: String?,
                ) = tool

                override fun updateCode(
                    toolId: Long,
                    newName: String?,
                    newCode: String,
                ) = tool

                override fun updatePrompt(
                    toolId: Long,
                    prompt: String,
                ) = tool

                override fun updateAndRunPrompt(
                    toolId: Long,
                    prompt: String,
                ) = tool
            }
        val result = useCase.aiReworkCode(1L, "improve performance")
        assertEquals("t", result.name)
    }

    @Test
    fun `McpToolSummary creates correctly`() {
        val summary = McpToolSummary(name = "tool1", description = "A tool")
        assertEquals("tool1", summary.name)
        assertEquals("A tool", summary.description)
    }

    @Test
    fun `McpToolsResult creates correctly`() {
        val summary = McpToolSummary(name = "tool1", description = "A tool")
        val result = McpToolsResult(mcpServerId = 1L, mcpServerName = "myServer", tools = listOf(summary))
        assertEquals(1L, result.mcpServerId)
        assertEquals("myServer", result.mcpServerName)
        assertEquals(1, result.tools.size)
    }

    @Test
    fun `McpCallResult tool getter is accessible`() {
        val result = McpCallResult(ok = true, text = "output", tool = "my-tool")
        assertEquals("my-tool", result.tool)
    }

    @Test
    fun `McpToolCallRequest default values`() {
        val req = McpToolCallRequest()
        assertNull(req.mcpServerId)
        assertEquals("", req.tool)
        assertEquals(emptyMap<String, Any?>(), req.args)
    }
}
