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

class IntegrationCloneUrlBuilderCoverageTest {
    private val githubConfigPort = mock<GithubRepoConfigPort>()
    private val gitlabConfigPort = mock<GitlabConfigPort>()
    private val builder = IntegrationCloneUrlBuilder(githubConfigPort, gitlabConfigPort)

    private fun mrData(
        url: String,
        platform: String,
    ) = MrFetchPort.MrData(
        mrUrl = url,
        platform = platform,
        title = "t",
        description = "d",
        sourceBranch = "s",
        targetBranch = "main",
        authorName = "a",
        state = "opened",
        diffText = "",
        changedFiles = emptyList(),
    )

    private fun analysisWith(gitConfigId: Long?) =
        CodeQaAnalysis(
            id = 1L,
            mrUrl = "https://github.com/acme/shop/pull/3",
            status = "COMPLETED",
            gitConfigId = gitConfigId,
        )

    @Test
    fun `the preferred github config supplies the access token`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(
            listOf(
                GithubRepoConfig(id = 3L, name = "other", owner = "acme", repo = "other", accessToken = "nope"),
                GithubRepoConfig(id = 7L, name = "chosen", owner = "acme", repo = "shop", accessToken = "tok"),
            ),
        )

        val url = builder.buildCloneUrl(mrData("https://github.com/acme/shop/pull/3", "github"), analysisWith(7L))

        assertEquals("https://tok@github.com/acme/shop.git", url)
    }

    @Test
    fun `an inactive preferred github config is not selected`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(
            listOf(GithubRepoConfig(id = 7L, name = "archived", owner = "acme", repo = "shop", accessToken = "tok", active = false)),
        )

        assertEquals("https://github.com/acme/shop.git", builder.buildCloneUrl(mrData("https://github.com/acme/shop/pull/3", "github"), analysisWith(7L)))
    }

    @Test
    fun `without a preferred id the repository owner and name decide`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(
            listOf(GithubRepoConfig(id = 4L, name = "match", owner = "ACME", repo = "SHOP", accessToken = "tok2")),
        )

        val url = builder.buildCloneUrl(mrData("https://github.com/acme/shop/pull/3", "github"), analysisWith(null))

        assertEquals("https://tok2@github.com/acme/shop.git", url)
    }

    @Test
    fun `a blank token on the matched github config falls back to an anonymous url`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(
            listOf(GithubRepoConfig(id = 4L, name = "match", owner = "acme", repo = "shop", accessToken = "   ")),
        )

        assertEquals(
            "https://github.com/acme/shop.git",
            builder.buildCloneUrl(mrData("https://github.com/acme/shop/pull/3", "github"), analysisWith(null)),
        )
    }

    @Test
    fun `an unrelated github repository has no matching config`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(
            listOf(GithubRepoConfig(id = 4L, name = "elsewhere", owner = "other", repo = "shop", accessToken = "tok")),
        )

        assertEquals(
            "https://github.com/acme/shop.git",
            builder.buildCloneUrl(mrData("https://github.com/acme/shop/pull/3", "github"), analysisWith(null)),
        )
    }

    @Test
    fun `the preferred gitlab config wins over host matching`() {
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(
            listOf(
                GitlabConfig(id = 2L, name = "first", baseUrl = "https://gitlab.example", accessToken = "a"),
                GitlabConfig(id = 5L, name = "second", baseUrl = "https://other.example", accessToken = "b"),
            ),
        )

        val url = builder.buildCloneUrl(mrData("https://gitlab.example/group/app/-/merge_requests/9", "gitlab"), analysisWith(5L))

        assertEquals("https://oauth2:b@gitlab.example/group/app.git", url)
    }

    @Test
    fun `an unknown preferred gitlab id yields an anonymous url`() {
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(
            listOf(GitlabConfig(id = 2L, name = "first", baseUrl = "https://gitlab.example", accessToken = "a")),
        )

        val url = builder.buildCloneUrl(mrData("https://gitlab.example/group/app/-/merge_requests/9", "gitlab"), analysisWith(99L))

        assertEquals("https://gitlab.example/group/app.git", url)
    }

    @Test
    fun `a malformed gitlab url yields no clone url`() {
        assertEquals(null, builder.buildCloneUrl(mrData("not a url", "gitlab"), analysisWith(null)))
    }

    @Test
    fun `unsupported platforms yield no clone url`() {
        assertNull(builder.buildCloneUrl(mrData("https://example/x/pull/1", "bitbucket"), analysisWith(null)))
    }
}
