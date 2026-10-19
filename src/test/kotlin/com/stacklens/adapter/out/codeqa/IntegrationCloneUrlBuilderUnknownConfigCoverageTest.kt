package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IntegrationCloneUrlBuilderUnknownConfigCoverageTest {
    private val githubConfigPort: GithubRepoConfigPort = mock()
    private val gitlabConfigPort: GitlabConfigPort = mock()
    private val builder = IntegrationCloneUrlBuilder(githubConfigPort, gitlabConfigPort)

    private fun mrData(
        platform: String,
        url: String,
    ) = MrFetchPort.MrData(
        mrUrl = url,
        platform = platform,
        title = "t",
        description = "d",
        sourceBranch = "b",
        targetBranch = "main",
        authorName = "a",
        state = "opened",
        diffText = "",
        changedFiles = emptyList(),
    )

    private fun analysis(gitConfigId: Long?) =
        CodeQaAnalysis(
            id = 1L,
            mrUrl = "https://github.com/acme/repo/pull/7",
            status = "COMPLETED",
            gitConfigId = gitConfigId,
        )

    @Test
    fun `an unknown github configuration id leaves the url unauthenticated`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(
            listOf(GithubRepoConfig(name = "other", owner = "acme", repo = "repo", accessToken = "secret")),
        )

        val url = builder.buildCloneUrl(mrData("github", "https://github.com/acme/repo/pull/7"), analysis(99L))

        assertEquals("https://github.com/acme/repo.git", url)
    }

    @Test
    fun `an unknown gitlab configuration id leaves the url unauthenticated`() {
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(
            listOf(GitlabConfig(name = "other", baseUrl = "https://gitlab.example", accessToken = "secret")),
        )

        val url =
            builder.buildCloneUrl(
                mrData("gitlab", "https://gitlab.example/group/repo/-/merge_requests/7"),
                analysis(99L),
            )

        assertEquals("https://gitlab.example/group/repo/-/merge_requests/7".let { "https://gitlab.example/group/repo.git" }, url)
    }

    @Test
    fun `a host that matches no configuration still produces a plain url`() {
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(
            listOf(GitlabConfig(name = "other", baseUrl = "https://elsewhere.example", accessToken = "secret")),
        )

        val url =
            builder.buildCloneUrl(
                mrData("gitlab", "https://gitlab.example/group/repo/-/merge_requests/7"),
                analysis(null),
            )

        assertEquals("https://gitlab.example/group/repo.git", url)
    }

    @Test
    fun `an unsupported platform yields no clone url at all`() {
        assertNull(builder.buildCloneUrl(mrData("bitbucket", "https://bitbucket.org/x/y/pull/1"), analysis(null)))
    }
}
