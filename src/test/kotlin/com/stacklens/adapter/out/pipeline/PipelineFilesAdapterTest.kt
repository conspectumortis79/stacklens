package com.stacklens.adapter.out.pipeline

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraRepoContext
import com.stacklens.application.port.out.JiraRepoFile
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.port.out.repo.PipelineSummary
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PipelineFilesAdapterTest {
    private lateinit var localRepoConfigRepo: LocalRepoConfigPort
    private lateinit var gitlabConfigRepo: GitlabConfigPort
    private lateinit var githubRepoConfigRepo: GithubRepoConfigPort
    private lateinit var scanService: LocalRepoScanPort
    private lateinit var gitlabService: GitlabPort
    private lateinit var githubBridge: GithubTicketBridgePort
    private lateinit var adapter: PipelineFilesAdapter

    @BeforeEach
    fun setup() {
        localRepoConfigRepo = mock()
        gitlabConfigRepo = mock()
        githubRepoConfigRepo = mock()
        scanService = mock()
        gitlabService = mock()
        githubBridge = mock()
        adapter =
            PipelineFilesAdapter(
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
    fun `loadLocal returns PipelineRepoData from the scanner result`() {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(1L))
            .thenReturn(LocalRepoConfig(id = 1L, name = "myrepo", path = "/repo"))
        whenever(scanService.buildPipelineSummary("/repo", maxFiles = 30, maxPerFileSizeKb = 16))
            .thenReturn(PipelineSummary("tree", "contents", 3, 100))

        val result = adapter.loadLocal(1L)
        assertNotNull(result)
        assertEquals("myrepo", result!!.appName)
        assertEquals("/repo", result.repoPath)
        assertEquals("tree", result.tree)
        assertEquals("contents", result.contents)
    }

    @Test
    fun `loadGitlab returns null when config not found`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(99L)).thenReturn(null)
        assertNull(adapter.loadGitlab(99L, 1L, "g/p"))
    }

    @Test
    fun `loadGitlab filters non-pipeline files out of the fetched list`() {
        val cfg = GitlabConfig(id = 1L, name = "g", baseUrl = "https://gitlab", accessToken = "x", active = true)
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(cfg)
        whenever(gitlabService.fetchProjectFiles(cfg, 9L, maxFiles = 30, maxPerFileSizeBytes = 16384))
            .thenReturn(
                listOf(
                    "docker-compose.yml" to "services:\n  app:\n    image: nginx",
                    "Dockerfile" to "FROM alpine\nCMD [\"echo\", \"hi\"]",
                    "README.md" to "Should be filtered out",
                    "src/main.kt" to "Should be filtered out",
                    ".gitlab-ci.yml" to "stages:\n  - test",
                ),
            )

        val result = adapter.loadGitlab(1L, 9L, "group/proj")
        assertNotNull(result)
        assertTrue(result!!.tree.contains("docker-compose.yml"))
        assertTrue(result.tree.contains(".gitlab-ci.yml"))
        assertTrue(result.tree.contains("Dockerfile"))
        assertFalse(result.tree.contains("README.md"))
        assertFalse(result.tree.contains("src/main.kt"))
        assertTrue(result.contents.contains("services:"))
        assertFalse(result.contents.contains("Should be filtered out"))
    }

    @Test
    fun `loadGithub throws when no config exists for the id`() {
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(emptyList())
        assertThrows(IllegalStateException::class.java) {
            adapter.loadGithub(99L)
        }
    }

    @Test
    fun `loadGithub filters to compose and pipeline files only`() {
        val cfg = GithubRepoConfig(id = 1L, name = "acme/app", owner = "acme", repo = "app", accessToken = "x", active = true)
        whenever(githubRepoConfigRepo.findAllGithubRepoConfigs()).thenReturn(listOf(cfg))
        whenever(githubBridge.fetchRepoContext(cfg))
            .thenReturn(
                JiraRepoContext(
                    repoName = "acme/app",
                    projectId = 42L,
                    files =
                        listOf(
                            JiraRepoFile(".github/workflows/ci.yml", "name: CI\non: [push]"),
                            JiraRepoFile("README.md", "Should be filtered out"),
                            JiraRepoFile("Dockerfile", "FROM alpine"),
                        ),
                ),
            )

        val result = adapter.loadGithub(1L)
        assertTrue(result.tree.contains(".github/workflows/ci.yml"))
        assertTrue(result.tree.contains("Dockerfile"))
        assertFalse(result.tree.contains("README.md"))
    }

    @Test
    fun `listGitlabFilePaths returns empty list when config is missing`() {
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(null)
        assertEquals(emptyList<String>(), adapter.listGitlabFilePaths(1L, 1L))
    }

    @Test
    fun `listGitlabFilePaths delegates to the GitlabPort`() {
        val cfg = GitlabConfig(id = 1L, name = "g", baseUrl = "https://gitlab", accessToken = "x", active = true)
        whenever(gitlabConfigRepo.findGitlabConfigById(1L)).thenReturn(cfg)
        whenever(gitlabService.listProjectFilePaths(cfg, 9L))
            .thenReturn(listOf("docker-compose.yml", ".gitlab-ci.yml"))

        assertEquals(listOf("docker-compose.yml", ".gitlab-ci.yml"), adapter.listGitlabFilePaths(1L, 9L))
    }

    @Test
    fun `loadLocal caches the summary within a single adapter instance`() {
        whenever(localRepoConfigRepo.findLocalRepoConfigById(1L))
            .thenReturn(LocalRepoConfig(id = 1L, name = "myrepo", path = "/repo"))
        whenever(scanService.buildPipelineSummary("/repo", maxFiles = 30, maxPerFileSizeKb = 16))
            .thenReturn(PipelineSummary("tree", "contents", 3, 100))

        adapter.loadLocal(1L)
        adapter.loadLocal(1L)

        org.mockito.kotlin
            .verify(scanService, org.mockito.kotlin.times(1))
            .buildPipelineSummary("/repo", maxFiles = 30, maxPerFileSizeKb = 16)
    }

    private fun assertFalse(
        condition: Boolean,
        message: String = "",
    ) {
        if (condition) throw AssertionError(message.ifBlank { "Expected condition to be false" })
    }
}
