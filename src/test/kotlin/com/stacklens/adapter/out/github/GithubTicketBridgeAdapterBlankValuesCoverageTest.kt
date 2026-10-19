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
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GithubTicketBridgeAdapterBlankValuesCoverageTest {
    private val configPort: GithubRepoConfigPort = mock()
    private val projectClient: GithubProjectApiClient = mock()
    private val repoFileClient: GithubRepoFileClient = mock()
    private val prClient: GithubPullRequestClient = mock()
    private val bodyBuilder: GithubPrBodyBuilder = mock()
    private val remoteDetector: GithubRemoteDetector = mock()

    private val adapter =
        GithubTicketBridgeAdapter(
            githubRepoConfigPort = configPort,
            projectClient = projectClient,
            repoFileClient = repoFileClient,
            prClient = prClient,
            prBodyBuilder = bodyBuilder,
            remoteDetector = remoteDetector,
        )

    private fun repo(branch: String?) = GithubRepoConfig(name = "widgets", owner = "acme", repo = "widgets", branch = branch, accessToken = "pat")

    private val ticket =
        JiraTicketData(
            key = "PROJ-1",
            summary = "Sum",
            description = "d",
            comments = "",
            issueType = "Story",
            status = "To Do",
            priority = null,
            labels = emptyList(),
            components = emptyList(),
            url = "https://jira/PROJ-1",
        )

    private fun code(files: List<JiraCodeFile>) =
        JiraCodeImplementation(
            identifiedRepositories = listOf("acme/widgets"),
            identifiedDatabases = emptyList(),
            files = files,
            testFiles = emptyList(),
            databaseChanges = emptyList(),
            implementationNotes = "notes",
        )

    @Test
    fun `a blank token is treated as no token at all`() {
        whenever(remoteDetector.resolveToken(any())).thenReturn("   ")

        assertNull(adapter.fetchRepoContext(repo("main")))
    }

    @Test
    fun `a missing token aborts the pull request creation`() {
        whenever(remoteDetector.resolveToken(any())).thenReturn(null)

        assertThrows(IllegalStateException::class.java) {
            adapter.createPullRequest(repo(null), ticket, code(emptyList()))
        }
    }

    @Test
    fun `a blank configured branch falls back to the repository default`() {
        whenever(remoteDetector.resolveToken(any())).thenReturn("pat")
        whenever(projectClient.fetchDefaultBranch(anyOrNull(), eq("acme"), eq("widgets"))).thenReturn("trunk")
        whenever(projectClient.fetchBranchSha(anyOrNull(), any(), any(), any())).thenReturn("sha-base")
        whenever(projectClient.fetchExistingFileSha(anyOrNull(), any(), any(), any(), any())).thenReturn(null)
        whenever(repoFileClient.fetchFileContent(anyOrNull(), any(), any(), any())).thenReturn(null)
        whenever(prClient.createBranch(anyOrNull(), any(), any(), any(), any())).thenReturn(true)
        whenever(prClient.commitFile(anyOrNull(), any(), any(), any(), any(), any(), any(), anyOrNull())).thenReturn(true)
        whenever(bodyBuilder.build(any())).thenReturn("body")
        whenever(
            prClient.openPullRequest(
                anyOrNull(),
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>(),
            ),
        ).thenReturn("https://github.com/acme/widgets/pull/7")
        val patch =
            JiraCodeFile(
                repository = "acme/widgets",
                path = "/src/Main.kt",
                language = "kotlin",
                description = "patched",
                changeType = FileChangeType.PATCH,
                code = "",
                patches =
                    listOf(
                        com.stacklens.application.port.out
                            .JiraCodePatch("old", "new"),
                    ),
            )

        val url =
            adapter.createPullRequest(
                repo("   "),
                ticket,
                code(listOf(patch)),
            )

        assertEquals("https://github.com/acme/widgets/pull/7", url)
    }
}
