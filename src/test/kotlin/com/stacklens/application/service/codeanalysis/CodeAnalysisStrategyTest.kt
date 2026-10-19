package com.stacklens.application.service.codeanalysis
import com.stacklens.adapter.out.analysis.CodeAnalysisRepoApi
import com.stacklens.application.port.out.analysis.CodeAnalysisRepoData
import com.stacklens.domain.model.CodeAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CodeAnalysisStrategyTest {
    private val repoPort = mock<CodeAnalysisRepoApi>()

    @Test
    fun `LocalCodeAnalysisStrategy params with analysis uses repoConfigId`() {
        val strategy = LocalCodeAnalysisStrategy(repoPort)
        val analysis = CodeAnalysis(mrUrl = "x", repoConfigId = 42L)
        val p = strategy.params(analysis)
        assertEquals(42L, p.repoConfigId)
    }

    @Test
    fun `LocalCodeAnalysisStrategy params with null analysis has null repoConfigId`() {
        val strategy = LocalCodeAnalysisStrategy(repoPort)
        assertNull(strategy.params(null).repoConfigId)
    }

    @Test
    fun `LocalCodeAnalysisStrategy load returns repo data when present`() {
        val data = CodeAnalysisRepoData("app", "/path", "", "")
        whenever(repoPort.loadLocal(42L)).thenReturn(data)
        val strategy = LocalCodeAnalysisStrategy(repoPort)
        val params = CodeAnalysisLoadParams(repoConfigId = 42L)
        assertEquals(data, strategy.load(params))
    }

    @Test
    fun `LocalCodeAnalysisStrategy load returns null when repoConfigId is null`() {
        val strategy = LocalCodeAnalysisStrategy(repoPort)
        assertNull(strategy.load(CodeAnalysisLoadParams()))
    }

    @Test
    fun `LocalCodeAnalysisStrategy load returns null when repo missing`() {
        whenever(repoPort.loadLocal(org.mockito.kotlin.any())).thenReturn(null)
        val strategy = LocalCodeAnalysisStrategy(repoPort)
        assertNull(strategy.load(CodeAnalysisLoadParams(repoConfigId = 999L)))
    }

    @Test
    fun `GithubCodeAnalysisStrategy params with analysis uses githubRepoConfigId`() {
        val strategy = GithubCodeAnalysisStrategy(repoPort)
        val analysis = CodeAnalysis(mrUrl = "x", githubRepoConfigId = 7L)
        val p = strategy.params(analysis)
        assertEquals(7L, p.githubRepoConfigId)
    }

    @Test
    fun `GithubCodeAnalysisStrategy params with null analysis has null githubRepoConfigId`() {
        val strategy = GithubCodeAnalysisStrategy(repoPort)
        assertNull(strategy.params(null).githubRepoConfigId)
    }

    @Test
    fun `GithubCodeAnalysisStrategy paramsFor builds params with given id`() {
        val strategy = GithubCodeAnalysisStrategy(repoPort)
        val p = strategy.paramsFor(99L)
        assertEquals(99L, p.githubRepoConfigId)
    }

    @Test
    fun `GithubCodeAnalysisStrategy load returns null when githubRepoConfigId is null`() {
        val strategy = GithubCodeAnalysisStrategy(repoPort)
        assertNull(strategy.load(CodeAnalysisLoadParams()))
    }

    @Test
    fun `GithubCodeAnalysisStrategy load delegates to port`() {
        val data = CodeAnalysisRepoData("gh-app", "url", "", "")
        whenever(repoPort.loadGithub(7L)).thenReturn(data)
        val strategy = GithubCodeAnalysisStrategy(repoPort)
        val params = CodeAnalysisLoadParams(githubRepoConfigId = 7L)
        assertEquals(data, strategy.load(params))
    }

    @Test
    fun `GitlabCodeAnalysisStrategy params with analysis extracts fields`() {
        val strategy = GitlabCodeAnalysisStrategy(repoPort)
        val analysis =
            CodeAnalysis(
                mrUrl = "x",
                gitlabConfigId = 1L,
                gitlabProjectId = 2L,
                gitlabProjectPath = "g/p",
                monorepoSubPath = "subsub",
            )
        val p = strategy.params(analysis)
        assertEquals(1L, p.gitlabConfigId)
        assertEquals(2L, p.gitlabProjectId)
        assertEquals("g/p", p.gitlabProjectPath)
        assertEquals("subsub", p.subPath)
    }

    @Test
    fun `GitlabCodeAnalysisStrategy params with null analysis has null fields`() {
        val strategy = GitlabCodeAnalysisStrategy(repoPort)
        val p = strategy.params(null)
        assertNull(p.gitlabConfigId)
        assertNull(p.gitlabProjectId)
        assertNull(p.gitlabProjectPath)
        assertNull(p.subPath)
    }

    @Test
    fun `GitlabCodeAnalysisStrategy paramsFor builds params`() {
        val strategy = GitlabCodeAnalysisStrategy(repoPort)
        val p = strategy.paramsFor(1L, 2L, "g/p", "subsub")
        assertEquals(1L, p.gitlabConfigId)
        assertEquals(2L, p.gitlabProjectId)
        assertEquals("g/p", p.gitlabProjectPath)
        assertEquals("subsub", p.subPath)
    }

    @Test
    fun `GitlabCodeAnalysisStrategy load returns null when gitlabConfigId is null`() {
        val strategy = GitlabCodeAnalysisStrategy(repoPort)
        val params = CodeAnalysisLoadParams(gitlabConfigId = null, gitlabProjectId = 2L, gitlabProjectPath = "g/p")
        assertNull(strategy.load(params))
    }

    @Test
    fun `GitlabCodeAnalysisStrategy load returns null when gitlabProjectId is null`() {
        val strategy = GitlabCodeAnalysisStrategy(repoPort)
        val params = CodeAnalysisLoadParams(gitlabConfigId = 1L, gitlabProjectId = null, gitlabProjectPath = "g/p")
        assertNull(strategy.load(params))
    }

    @Test
    fun `GitlabCodeAnalysisStrategy load returns null when gitlabProjectPath is null`() {
        val strategy = GitlabCodeAnalysisStrategy(repoPort)
        val params = CodeAnalysisLoadParams(gitlabConfigId = 1L, gitlabProjectId = 2L, gitlabProjectPath = null)
        assertNull(strategy.load(params))
    }

    @Test
    fun `GitlabCodeAnalysisStrategy load delegates to port with empty subPath fallback`() {
        val data = CodeAnalysisRepoData("gl-app", "url", "", "")
        whenever(repoPort.loadGitlab(1L, 2L, "g/p", "")).thenReturn(data)
        val strategy = GitlabCodeAnalysisStrategy(repoPort)
        val params = CodeAnalysisLoadParams(gitlabConfigId = 1L, gitlabProjectId = 2L, gitlabProjectPath = "g/p", subPath = null)
        assertEquals(data, strategy.load(params))
    }

    @Test
    fun `appName returns unknown when all fields are null`() {
        assertEquals("unknown", CodeAnalysisLoadParams().appName)
    }

    @Test
    fun `appName returns gitlab project path without subpath when subpath is null`() {
        val p = CodeAnalysisLoadParams(gitlabProjectPath = "group/project", subPath = null)
        assertEquals("group/project", p.appName)
    }

    @Test
    fun `appName returns gitlab project path without subpath when subpath is blank`() {
        val p = CodeAnalysisLoadParams(gitlabProjectPath = "group/project", subPath = "")
        assertEquals("group/project", p.appName)
    }

    @Test
    fun `appName returns gitlab project path without subpath when subpath is whitespace`() {
        val p = CodeAnalysisLoadParams(gitlabProjectPath = "group/project", subPath = "   ")
        assertEquals("group/project", p.appName)
    }

    @Test
    fun `appName returns gitlab project path with subpath when subpath is whitespace non-blank`() {
        val p = CodeAnalysisLoadParams(gitlabProjectPath = "group/project", subPath = "x")
        assertEquals("group/project/x", p.appName)
    }

    @Test
    fun `appName with gitlabProjectPath set and subPath containing whitespace`() {
        val p = CodeAnalysisLoadParams(gitlabProjectPath = "group/project", subPath = "  x  ")
        assertEquals("group/project/  x  ", p.appName)
    }

    @Test
    fun `appName returns gitlab project path with subpath appended with slash`() {
        val p = CodeAnalysisLoadParams(gitlabProjectPath = "group/project", subPath = "subsub")
        assertEquals("group/project/subsub", p.appName)
    }

    @Test
    fun `appName returns subpath alone when gitlabProjectPath is null but subpath set`() {
        val p = CodeAnalysisLoadParams(subPath = "some/sub")
        assertEquals("some/sub", p.appName)
    }

    @Test
    fun `appName returns local prefix when only repoConfigId is set`() {
        val p = CodeAnalysisLoadParams(repoConfigId = 7L)
        assertEquals("local-7", p.appName)
    }

    @Test
    fun `appName returns github prefix when only githubRepoConfigId is set`() {
        val p = CodeAnalysisLoadParams(githubRepoConfigId = 99L)
        assertEquals("github-99", p.appName)
    }

    @Test
    fun `appName with blank gitlabProjectPath falls through to subpath check`() {
        val p = CodeAnalysisLoadParams(gitlabProjectPath = "", subPath = "x")
        assertEquals("x", p.appName)
    }

    @Test
    fun `appName with whitespace-only gitlabProjectPath falls through to subpath check`() {
        val p = CodeAnalysisLoadParams(gitlabProjectPath = "   ", subPath = "x")
        assertEquals("x", p.appName)
    }

    @Test
    fun `appName with blank subPath falls through to repoConfigId`() {
        val p = CodeAnalysisLoadParams(subPath = "", repoConfigId = 5L)
        assertEquals("local-5", p.appName)
    }
}
