package com.stacklens.adapter.out.github

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.JiraCodeFile
import com.stacklens.application.port.out.JiraCodeImplementation
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.GithubRepoConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path

class GithubTicketBridgeAdapterTest {
    private val githubRepoConfigPort: GithubRepoConfigPort = mock()
    private val projectClient: GithubProjectApiClient = mock()
    private val repoFileClient: GithubRepoFileClient = mock()
    private val prClient: GithubPullRequestClient = mock()
    private val prBodyBuilder: GithubPrBodyBuilder = GithubPrBodyBuilder()
    private val remoteDetector: GithubRemoteDetector = mock()

    private lateinit var adapter: GithubTicketBridgeAdapter

    private val baseConfig =
        GithubRepoConfig(
            id = 1L,
            name = "octo/cat",
            owner = "octo",
            repo = "cat",
            accessToken = "TOK",
        )

    private val configWithBranch =
        GithubRepoConfig(
            id = 1L,
            name = "octo/cat",
            owner = "octo",
            repo = "cat",
            accessToken = "TOK",
            branch = "develop",
        )

    private val ticket =
        JiraTicketData(
            key = "P-1",
            summary = "Add login flow",
            description = "Implement login",
            comments = "",
            issueType = "Story",
            status = "Open",
            priority = null,
            labels = emptyList(),
            components = emptyList(),
            url = "https://jira/P-1",
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
    fun `fetchRepoContext returns null when no token is available`() {
        doReturn(null).`when`(remoteDetector).resolveToken(baseConfig)
        val result = adapter.fetchRepoContext(baseConfig)
        assertNull(result)
    }

    @Test
    fun `fetchRepoContext uses config branch when set`() {
        val cfgWithBranch =
            GithubRepoConfig(
                id = 1L,
                name = "octo/cat",
                owner = "octo",
                repo = "cat",
                accessToken = "TOK",
                branch = "feature",
            )
        doReturn("TOK").`when`(remoteDetector).resolveToken(cfgWithBranch)
        doReturn(emptyList<com.stacklens.application.port.out.JiraRepoFile>())
            .`when`(repoFileClient)
            .fetchRepoFiles(any(), any(), any())
        val result = adapter.fetchRepoContext(cfgWithBranch)
        assertEquals("octo/cat", result!!.repoName)
        assertEquals(1L, result.projectId)
        verify(repoFileClient).fetchRepoFiles(any(), any(), any())
    }

    @Test
    fun `fetchRepoContext fetches default branch when none configured`() {
        doReturn("TOK").`when`(remoteDetector).resolveToken(baseConfig)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn(emptyList<com.stacklens.application.port.out.JiraRepoFile>())
            .`when`(repoFileClient)
            .fetchRepoFiles(any(), any(), any())
        val result = adapter.fetchRepoContext(baseConfig)
        assertEquals("octo/cat", result!!.repoName)
        verify(projectClient).fetchDefaultBranch(any(), any(), any())
    }

    @Test
    fun `fetchRepoContext returns null when project client throws IllegalStateException`() {
        doReturn("TOK").`when`(remoteDetector).resolveToken(baseConfig)
        whenever(projectClient.fetchDefaultBranch(any(), any(), any()))
            .thenAnswer { throw IllegalStateException("repo not found") }
        val result = adapter.fetchRepoContext(baseConfig)
        assertNull(result)
    }

    @Test
    fun `fetchRepoContext returns repo context with fetched files`() {
        doReturn("TOK").`when`(remoteDetector).resolveToken(baseConfig)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn(
            listOf(
                com.stacklens.application.port.out
                    .JiraRepoFile(path = "src/Foo.kt", content = "code"),
            ),
        ).`when`(repoFileClient).fetchRepoFiles(any(), any(), any())
        val result = adapter.fetchRepoContext(baseConfig)
        assertEquals(1, result!!.files.size)
        assertEquals("src/Foo.kt", result.files.first().path)
    }

    @Test
    fun `createPullRequest throws when no token is available`() {
        doReturn(null).`when`(remoteDetector).resolveToken(baseConfig)
        val impl = emptyImpl()
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter.createPullRequest(baseConfig, ticket, impl)
            }
        assertEquals("No GitHub token available for octo/cat", ex.message)
    }

    @Test
    fun `createPullRequest uses config branch and creates PR`() {
        doReturn("TOK").`when`(remoteDetector).resolveToken(configWithBranch)
        doReturn("baseSha").`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        doReturn(true).`when`(prClient).createBranch(any(), any(), any(), any(), any())
        doReturn("https://gh/pr/1").`when`(prClient).openPullRequest(any(), any(), any(), any(), any(), any(), any())
        val result = adapter.createPullRequest(configWithBranch, ticket, emptyImpl())
        assertEquals("https://gh/pr/1", result)
        verify(prClient).openPullRequest(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `createPullRequest fetches default branch when not configured`() {
        doReturn("TOK").`when`(remoteDetector).resolveToken(baseConfig)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn("baseSha").`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        doReturn(true).`when`(prClient).createBranch(any(), any(), any(), any(), any())
        doReturn("https://gh/pr/2").`when`(prClient).openPullRequest(any(), any(), any(), any(), any(), any(), any())
        val result = adapter.createPullRequest(baseConfig, ticket, emptyImpl())
        assertEquals("https://gh/pr/2", result)
    }

    @Test
    fun `createPullRequest throws when PR URL is empty`() {
        doReturn("TOK").`when`(remoteDetector).resolveToken(baseConfig)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn("baseSha").`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        doReturn(true).`when`(prClient).createBranch(any(), any(), any(), any(), any())
        doReturn(null).`when`(prClient).openPullRequest(any(), any(), any(), any(), any(), any(), any())
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter.createPullRequest(baseConfig, ticket, emptyImpl())
            }
        assertEquals("GitHub PR creation returned empty URL", ex.message)
    }

    @Test
    fun `createPullRequest aborts when branch creation reports failure`() {
        doReturn("TOK").`when`(remoteDetector).resolveToken(baseConfig)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn("baseSha").`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        doReturn(false).`when`(prClient).createBranch(any(), any(), any(), any(), any())

        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter.createPullRequest(baseConfig, ticket, emptyImpl())
            }
        assertTrue(ex.message!!.contains("branch"), "unexpected message: ${ex.message}")
    }

    @Test
    fun `createPullRequest aborts when a file commit reports failure`() {
        doReturn("TOK").`when`(remoteDetector).resolveToken(baseConfig)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn("baseSha").`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        doReturn(null).`when`(projectClient).fetchExistingFileSha(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).createBranch(any(), any(), any(), any(), any())
        doReturn(false).`when`(prClient).commitFile(any(), any(), any(), any(), any(), any(), any(), anyOrNull<String>())
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files =
                    listOf(
                        JiraCodeFile(
                            repository = "octo/cat",
                            path = "src/New.kt",
                            language = "kotlin",
                            description = "new file",
                            changeType = FileChangeType.CREATE,
                            code = "class New",
                            patches = emptyList(),
                        ),
                    ),
                testFiles = emptyList(),
                systemTestFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )

        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter.createPullRequest(baseConfig, ticket, impl)
            }
        assertTrue(ex.message!!.contains("commit"), "unexpected message: ${ex.message}")
    }

    @Test
    fun `createPullRequest commits files with CREATE changeType`() {
        doReturn("TOK").`when`(remoteDetector).resolveToken(baseConfig)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn("baseSha").`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        doReturn(null).`when`(projectClient).fetchExistingFileSha(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).createBranch(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).commitFile(any(), any(), any(), any(), any(), any(), any(), anyOrNull())
        doReturn("https://gh/pr/3").`when`(prClient).openPullRequest(any(), any(), any(), any(), any(), any(), any())
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files =
                    listOf(
                        JiraCodeFile(
                            repository = "octo/cat",
                            path = "src/New.kt",
                            language = "kotlin",
                            description = "new file",
                            changeType = FileChangeType.CREATE,
                            code = "class New",
                            patches = emptyList(),
                        ),
                    ),
                testFiles = emptyList(),
                systemTestFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        val result = adapter.createPullRequest(baseConfig, ticket, impl)
        assertEquals("https://gh/pr/3", result)
        verify(prClient).commitFile(any(), any(), any(), any(), any(), any(), any(), anyOrNull<String>())
    }

    @Test
    fun `createPullRequest applies patches when changeType is PATCH`() {
        doReturn("TOK").`when`(remoteDetector).resolveToken(baseConfig)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn("baseSha").`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        doReturn("existingSha").`when`(projectClient).fetchExistingFileSha(any(), any(), any(), any(), any())
        doReturn("original content").`when`(repoFileClient).fetchFileContent(any(), any(), any(), any())
        doReturn(true).`when`(prClient).createBranch(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).commitFile(any(), any(), any(), any(), any(), any(), any(), anyOrNull())
        doReturn("https://gh/pr/4").`when`(prClient).openPullRequest(any(), any(), any(), any(), any(), any(), any())
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files =
                    listOf(
                        JiraCodeFile(
                            repository = "octo/cat",
                            path = "src/Existing.kt",
                            language = "kotlin",
                            description = "patched file",
                            changeType = FileChangeType.PATCH,
                            code = "unused",
                            patches =
                                listOf(
                                    com.stacklens.application.port.out.JiraCodePatch(
                                        search = "original content",
                                        replace = "new content",
                                    ),
                                ),
                        ),
                    ),
                testFiles = emptyList(),
                systemTestFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        val result = adapter.createPullRequest(baseConfig, ticket, impl)
        assertEquals("https://gh/pr/4", result)
        verify(repoFileClient).fetchFileContent(any(), any(), any(), any())
    }

    @Test
    fun `createPullRequest uses file code when not PATCH`() {
        doReturn("TOK").`when`(remoteDetector).resolveToken(baseConfig)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn("baseSha").`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        doReturn(null).`when`(projectClient).fetchExistingFileSha(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).createBranch(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).commitFile(any(), any(), any(), any(), any(), any(), any(), anyOrNull())
        doReturn("https://gh/pr/5").`when`(prClient).openPullRequest(any(), any(), any(), any(), any(), any(), any())
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files =
                    listOf(
                        JiraCodeFile(
                            repository = "octo/cat",
                            path = "/src/Updated.kt",
                            language = "kotlin",
                            description = "updated",
                            changeType = FileChangeType.UPDATE,
                            code = "new content",
                            patches = emptyList(),
                        ),
                    ),
                testFiles = emptyList(),
                systemTestFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        val result = adapter.createPullRequest(baseConfig, ticket, impl)
        assertEquals("https://gh/pr/5", result)
        verify(prClient).commitFile(any(), any(), any(), any(), any(), any(), any(), anyOrNull<String>())
    }

    @Test
    fun `createPullRequest commits test files and system test files`() {
        doReturn("TOK").`when`(remoteDetector).resolveToken(baseConfig)
        doReturn("main").`when`(projectClient).fetchDefaultBranch(any(), any(), any())
        doReturn("baseSha").`when`(projectClient).fetchBranchSha(any(), any(), any(), any())
        doReturn(null).`when`(projectClient).fetchExistingFileSha(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).createBranch(any(), any(), any(), any(), any())
        doReturn(true).`when`(prClient).commitFile(any(), any(), any(), any(), any(), any(), any(), anyOrNull())
        doReturn("https://gh/pr/6").`when`(prClient).openPullRequest(any(), any(), any(), any(), any(), any(), any())
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files = emptyList(),
                testFiles =
                    listOf(
                        JiraCodeFile(
                            repository = "octo/cat",
                            path = "src/Test.kt",
                            language = "kotlin",
                            description = "",
                            changeType = FileChangeType.CREATE,
                            code = "class Test",
                            patches = emptyList(),
                        ),
                    ),
                systemTestFiles =
                    listOf(
                        JiraCodeFile(
                            repository = "octo/cat",
                            path = "src/SystemTest.kt",
                            language = "kotlin",
                            description = "",
                            changeType = FileChangeType.CREATE,
                            code = "class SystemTest",
                            patches = emptyList(),
                        ),
                    ),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        val result = adapter.createPullRequest(baseConfig, ticket, impl)
        assertEquals("https://gh/pr/6", result)
        verify(prClient, times(2)).commitFile(any(), any(), any(), any(), any(), any(), any(), anyOrNull<String>())
    }

    @Test
    fun `detectGithubRemote delegates to remote detector`() {
        val path = Path.of("/tmp/repo")
        val detected = GithubRepoConfig(name = "n", owner = "octo", repo = "cat")
        doReturn(detected).`when`(remoteDetector).detectGithubRemote(path)
        assertEquals(detected, adapter.detectGithubRemote(path))
    }

    @Test
    fun `detectGithubRemote returns null when delegate returns null`() {
        val path = Path.of("/tmp/repo")
        doReturn(null).`when`(remoteDetector).detectGithubRemote(path)
        assertNull(adapter.detectGithubRemote(path))
    }

    private fun emptyImpl() =
        JiraCodeImplementation(
            identifiedRepositories = emptyList(),
            identifiedDatabases = emptyList(),
            files = emptyList(),
            testFiles = emptyList(),
            systemTestFiles = emptyList(),
            databaseChanges = emptyList(),
            implementationNotes = "",
        )
}
