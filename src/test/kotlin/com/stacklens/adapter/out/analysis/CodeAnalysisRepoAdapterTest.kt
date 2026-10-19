package com.stacklens.adapter.out.analysis

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraRepoContext
import com.stacklens.application.port.out.JiraRepoFile
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.repo.CodeSummary
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.domain.model.LocalRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CodeAnalysisRepoAdapterTest {
    private lateinit var localRepoConfigRepo: LocalRepoConfigPort
    private lateinit var gitlabConfigRepo: GitlabConfigPort
    private lateinit var githubRepoConfigRepo: GithubRepoConfigPort
    private lateinit var scanService: LocalRepoScanPort
    private lateinit var gitlabService: GitlabPort
    private lateinit var githubBridge: GithubTicketBridgePort
    private lateinit var adapter: CodeAnalysisRepoAdapter

    @BeforeEach
    fun setup() {
        localRepoConfigRepo = mock()
        gitlabConfigRepo = mock()
        githubRepoConfigRepo = mock()
        scanService = mock()
        gitlabService = mock()
        githubBridge = mock()
        adapter =
            CodeAnalysisRepoAdapter(
                localRepoConfigRepo,
                gitlabConfigRepo,
                githubRepoConfigRepo,
                scanService,
                gitlabService,
                githubBridge,
            )
    }

    @Test
    fun `loadLocal returns null when config not found`() {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(99L)).thenReturn(null)
        assertNull(adapter.loadLocal(99L))
    }

    @Test
    fun `loadLocal returns RepoData when config exists`() {
        val config = LocalRepoConfig(id = 1L, name = "myrepo", path = "/repo")
        val summary = CodeSummary("tree", "contents", 5, 1000)
        whenever(localRepoConfigRepo.findLocalRepoConfigById(1L)).thenReturn(config)
        whenever(scanService.buildCodeSummary("/repo", maxFiles = 30, maxPerFileSizeKb = 2)).thenReturn(summary)
        val result = adapter.loadLocal(1L)
        assertNotNull(result)
        assertEquals("myrepo", result!!.appName)
        assertEquals("/repo", result.repoPath)
        assertEquals("tree", result.tree)
        assertEquals("contents", result.contents)
    }

    @Test
    fun `listGitlabFilePaths returns empty when config not found`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(99L)).thenReturn(null)
        assertEquals(emptyList<String>(), adapter.listGitlabFilePaths(99L, 1L))
    }

    @Test
    fun `listGitlabFilePaths returns paths when config exists`() {
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl", accessToken = "tok")
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(config)
        whenever(gitlabService.listProjectFilePaths(any(), any())).thenReturn(listOf("a.kt", "b.kt"))
        assertEquals(listOf("a.kt", "b.kt"), adapter.listGitlabFilePaths(1L, 10L))
    }

    @Test
    fun `loadGitlab returns null when config not found`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(99L)).thenReturn(null)
        assertNull(adapter.loadGitlab(99L, 1L, "g/p"))
    }

    @Test
    fun `loadGitlab with blank subPath calls fetchProjectFiles`() {
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl", accessToken = "tok")
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(config)
        whenever(gitlabService.fetchProjectFiles(any(), any(), any(), any()))
            .thenReturn(listOf("src/Main.kt" to "code"))
        val result = adapter.loadGitlab(1L, 10L, "g/project", "")
        assertNotNull(result)
        assertEquals("project", result!!.appName)
        assertEquals("g/project", result.repoPath)
        verify(gitlabService).fetchProjectFiles(any(), any(), any(), any())
        verify(gitlabService, never()).fetchProjectFilesForPath(any(), any(), any(), any(), any())
    }

    @Test
    fun `loadGitlab with non-blank subPath calls fetchProjectFilesForPath`() {
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl", accessToken = "tok")
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(config)
        whenever(gitlabService.fetchProjectFilesForPath(any(), any(), any(), any(), any()))
            .thenReturn(listOf("backend/App.kt" to "code"))
        val result = adapter.loadGitlab(1L, 10L, "g/project", "backend")
        assertNotNull(result)
        assertEquals("backend", result!!.appName)
        assertEquals("g/project/backend", result.repoPath)
        verify(gitlabService, never()).fetchProjectFiles(any(), any(), any(), any())
        verify(gitlabService).fetchProjectFilesForPath(any(), any(), any(), any(), any())
    }

    @Test
    fun `loadGithub throws when config not found`() {
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        assertThrows(IllegalStateException::class.java) { adapter.loadGithub(99L) }
    }

    @Test
    fun `loadGithub throws when list has configs but none match the requested id`() {
        val other = GithubRepoConfig(id = 5L, name = "other", owner = "org", repo = "other-repo")
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(other))
        assertThrows(IllegalStateException::class.java) { adapter.loadGithub(99L) }
    }

    @Test
    fun `loadGithub throws when fetchRepoContext returns null`() {
        val config = GithubRepoConfig(id = 1L, name = "r", owner = "acme", repo = "app")
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(config))
        whenever(githubBridge.fetchRepoContext(config)).thenReturn(null)
        assertThrows(IllegalStateException::class.java) { adapter.loadGithub(1L) }
    }

    @Test
    fun `loadGithub success sorts files by codeFilePriority`() {
        val config = GithubRepoConfig(id = 1L, name = "r", owner = "acme", repo = "app")
        val files =
            listOf(
                JiraRepoFile("build.gradle.kts", "plugins {}"),
                JiraRepoFile("src/main/kotlin/App.kt", "class App"),
                JiraRepoFile("src/main/resources/app.yml", "spring: {}"),
                JiraRepoFile("src/test/kotlin/Test.kt", "class Test"),
                JiraRepoFile("src/main/resources/static/style.css", "body{}"),
            )
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(config))
        whenever(githubBridge.fetchRepoContext(config)).thenReturn(JiraRepoContext("app", 1L, files))
        val result = adapter.loadGithub(1L)
        assertEquals("acme/app", result.appName)
        assertTrue(result.tree.startsWith("src/main/kotlin/App.kt"))
    }

    @Test
    fun `findGitlabConfig returns null when not found`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(99L)).thenReturn(null)
        assertNull(adapter.findGitlabConfig(99L))
    }

    @Test
    fun `findGitlabConfig returns config when found`() {
        val config = GitlabConfig(id = 1L, name = "gl", baseUrl = "http://gl", accessToken = "tok")
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(config)
        assertEquals(config, adapter.findGitlabConfig(1L))
    }

    @Test
    fun `codeFilePriority returns 0 for main production source`() {
        assertEquals(0, adapter.codeFilePriority("src/main/kotlin/App.kt"))
    }

    @Test
    fun `codeFilePriority returns 1 for main resources non-static non-template`() {
        assertEquals(1, adapter.codeFilePriority("src/main/resources/app.yml"))
    }

    @Test
    fun `codeFilePriority returns 2 for test files`() {
        assertEquals(2, adapter.codeFilePriority("src/test/kotlin/AppTest.kt"))
    }

    @Test
    fun `codeFilePriority returns 4 for static assets`() {
        assertEquals(4, adapter.codeFilePriority("src/main/resources/static/style.css"))
    }

    @Test
    fun `codeFilePriority returns 4 for templates`() {
        assertEquals(4, adapter.codeFilePriority("src/main/resources/templates/index.html"))
    }

    @Test
    fun `codeFilePriority returns 3 for everything else`() {
        assertEquals(3, adapter.codeFilePriority("build.gradle.kts"))
        assertEquals(3, adapter.codeFilePriority("README.md"))
    }
}
