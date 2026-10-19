package com.stacklens.adapter.out.github

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraCodePatch
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.GithubRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class GithubTicketBridgeAdapterBranchTest {
    private val githubRepoConfigPort: GithubRepoConfigPort = mock()
    private val projectClient: GithubProjectApiClient = mock()
    private val repoFileClient: GithubRepoFileClient = mock()
    private val prClient: GithubPullRequestClient = mock()
    private val prBodyBuilder: GithubPrBodyBuilder = GithubPrBodyBuilder()
    private val remoteDetector: GithubRemoteDetector = mock()

    private lateinit var adapter: GithubTicketBridgeAdapter

    private val ticket =
        JiraTicketData(
            key = "PROJ-7",
            summary = "Implement feature",
            description = "x",
            comments = "",
            issueType = "Story",
            status = "Open",
            priority = null,
            labels = emptyList(),
            components = emptyList(),
            url = "https://jira/PROJ-7",
        )

    @BeforeEach
    fun setUp() {
        adapter =
            GithubTicketBridgeAdapter(
                githubRepoConfigPort = githubRepoConfigPort,
                projectClient = projectClient,
                repoFileClient = repoFileClient,
                prClient = prClient,
                prBodyBuilder = prBodyBuilder,
                remoteDetector = remoteDetector,
            )
    }

    @Test
    fun `fetchRepoContext falls back to fetched default branch when branch is blank`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "octo/cat",
                owner = "octo",
                repo = "cat",
                accessToken = "TOK",
                branch = "   ",
            )
        doReturn("TOK").`when`(remoteDetector).resolveToken(cfg)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn(emptyList<com.stacklens.application.port.out.JiraRepoFile>())
            .`when`(repoFileClient)
            .fetchRepoFiles(any(), any(), any())
        val ctx = adapter.fetchRepoContext(cfg)
        assertNotNull(ctx)
        verify(projectClient).fetchDefaultBranch(any(), any(), any())
    }

    @Test
    fun `fetchRepoContext returns null when repoFileClient throws IllegalStateException`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "octo/cat",
                owner = "octo",
                repo = "cat",
                accessToken = "TOK",
            )
        doReturn("TOK").`when`(remoteDetector).resolveToken(cfg)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        wheneverThrowable =
            IllegalStateException("not found")
        org.mockito.kotlin
            .whenever(repoFileClient.fetchRepoFiles(any(), any(), any()))
            .thenAnswer { throw wheneverThrowable }
        val ctx = adapter.fetchRepoContext(cfg)
        assertNull(ctx)
    }

    private var wheneverThrowable: Throwable = IllegalStateException()

    @Test
    fun `createPullRequest trims leading slash from file path`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "octo/cat",
                owner = "octo",
                repo = "cat",
                accessToken = "TOK",
            )
        doReturn("TOK").`when`(remoteDetector).resolveToken(cfg)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn("baseSha").`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        doReturn(null).`when`(projectClient).fetchExistingFileSha(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).createBranch(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).commitFile(any(), any(), any(), any(), any(), any(), any(), anyOrNull<String>())
        doReturn("https://gh/pr/1").`when`(prClient).openPullRequest(any(), any(), any(), any(), any(), any(), any())
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files =
                    listOf(
                        JiraCodeFile(
                            repository = "octo/cat",
                            path = "/src/Leading.kt",
                            language = "kotlin",
                            description = "leading slash",
                            changeType = FileChangeType.CREATE,
                            code = "class Leading",
                            patches = emptyList(),
                        ),
                    ),
                testFiles = emptyList(),
                systemTestFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        val result = adapter.createPullRequest(cfg, ticket, impl)
        assertEquals("https://gh/pr/1", result)
    }

    @Test
    fun `createPullRequest skips applyPatches when patches list is empty even if PATCH`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "octo/cat",
                owner = "octo",
                repo = "cat",
                accessToken = "TOK",
            )
        doReturn("TOK").`when`(remoteDetector).resolveToken(cfg)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn("baseSha").`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        doReturn(null).`when`(projectClient).fetchExistingFileSha(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).createBranch(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).commitFile(any(), any(), any(), any(), any(), any(), any(), anyOrNull<String>())
        doReturn("https://gh/pr/1").`when`(prClient).openPullRequest(any(), any(), any(), any(), any(), any(), any())
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files =
                    listOf(
                        JiraCodeFile(
                            repository = "octo/cat",
                            path = "src/Empty.kt",
                            language = "kotlin",
                            description = "",
                            changeType = FileChangeType.PATCH,
                            code = "fallback code",
                            patches = emptyList(),
                        ),
                    ),
                testFiles = emptyList(),
                systemTestFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        adapter.createPullRequest(cfg, ticket, impl)
        verify(repoFileClient, times(0)).fetchFileContent(any(), any(), any(), any())
    }

    @Test
    fun `createPullRequest throws when fetchBranchSha returns null`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "octo/cat",
                owner = "octo",
                repo = "cat",
                accessToken = "TOK",
            )
        doReturn("TOK").`when`(remoteDetector).resolveToken(cfg)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn(null).`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files = emptyList(),
                testFiles = emptyList(),
                systemTestFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        assertThrows(Exception::class.java) {
            adapter.createPullRequest(cfg, ticket, impl)
        }
    }

    @Test
    fun `createPullRequest applies patch when PATCH and patches are non-empty`() {
        val cfg =
            GithubRepoConfig(
                id = 1L,
                name = "octo/cat",
                owner = "octo",
                repo = "cat",
                accessToken = "TOK",
            )
        doReturn("TOK").`when`(remoteDetector).resolveToken(cfg)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn("baseSha").`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        doReturn("existingSha").`when`(projectClient).fetchExistingFileSha(any(), any(), any(), any(), any())
        doReturn("original").`when`(repoFileClient).fetchFileContent(any(), any(), any(), any())
        doReturn(true).`when`(prClient).createBranch(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).commitFile(any(), any(), any(), any(), any(), any(), any(), anyOrNull<String>())
        doReturn("https://gh/pr/9").`when`(prClient).openPullRequest(any(), any(), any(), any(), any(), any(), any())
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files =
                    listOf(
                        JiraCodeFile(
                            repository = "octo/cat",
                            path = "src/Apply.kt",
                            language = "kotlin",
                            description = "",
                            changeType = FileChangeType.PATCH,
                            code = "unused",
                            patches =
                                listOf(
                                    JiraCodePatch(search = "original", replace = "patched"),
                                ),
                        ),
                    ),
                testFiles = emptyList(),
                systemTestFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        adapter.createPullRequest(cfg, ticket, impl)
        verify(repoFileClient).fetchFileContent(any(), any(), any(), any())
    }
}
