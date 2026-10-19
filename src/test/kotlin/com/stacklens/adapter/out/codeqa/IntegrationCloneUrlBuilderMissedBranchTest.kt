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

class IntegrationCloneUrlBuilderMissedBranchTest {
    private val githubConfigPort: GithubRepoConfigPort = mock()
    private val gitlabConfigPort: GitlabConfigPort = mock()
    private val builder = IntegrationCloneUrlBuilder(githubConfigPort, gitlabConfigPort)

    private fun mrData(
        url: String,
        platform: String,
    ) = MrFetchPort.MrData(
        mrUrl = url,
        platform = platform,
        title = "t",
        description = "d",
        sourceBranch = "sb",
        targetBranch = "tb",
        authorName = "a",
        state = "opened",
        diffText = "",
        changedFiles = emptyList(),
    )

    private fun githubConfig(
        id: Long,
        owner: String,
        repo: String,
        token: String?,
    ) = GithubRepoConfig(id = id, name = "gh", owner = owner, repo = repo, accessToken = token, active = true)

    private fun gitlabConfig(
        id: Long,
        baseUrl: String,
        token: String,
    ) = GitlabConfig(id = id, name = "gl", baseUrl = baseUrl, accessToken = token, active = true)

    @Test
    fun `github preferred config id without a matching config falls back to the anonymous url`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs())
            .thenReturn(listOf(githubConfig(1L, "acme", "shop", "tok")))
        val url =
            builder.buildCloneUrl(
                mrData("https://github.com/acme/shop/pull/12", "github"),
                CodeQaAnalysis(mrUrl = "https://github.com/acme/shop/pull/12", gitConfigId = 404L),
            )

        assertEquals("https://github.com/acme/shop.git", url)
    }

    @Test
    fun `github matching config with a blank token falls back to the anonymous url`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs())
            .thenReturn(listOf(githubConfig(1L, "acme", "shop", "   ")))
        val url =
            builder.buildCloneUrl(
                mrData("https://github.com/acme/shop/pull/12", "github"),
                CodeQaAnalysis(mrUrl = "https://github.com/acme/shop/pull/12", gitConfigId = 1L),
            )

        assertEquals("https://github.com/acme/shop.git", url)
    }

    @Test
    fun `github lookup without a config id ignores configs of another repository`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs())
            .thenReturn(listOf(githubConfig(1L, "acme", "other-repo", "tok")))
        val url =
            builder.buildCloneUrl(
                mrData("https://github.com/acme/shop/pull/12", "github"),
                CodeQaAnalysis(mrUrl = "https://github.com/acme/shop/pull/12"),
            )

        assertEquals("https://github.com/acme/shop.git", url)
    }

    @Test
    fun `github lookup without a config id matches owner and repository case insensitively`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs())
            .thenReturn(listOf(githubConfig(1L, "ACME", "Shop", "tok")))
        val url =
            builder.buildCloneUrl(
                mrData("https://github.com/acme/shop/pull/12", "github"),
                CodeQaAnalysis(mrUrl = "https://github.com/acme/shop/pull/12"),
            )

        assertEquals("https://tok@github.com/acme/shop.git", url)
    }

    @Test
    fun `github url of another host yields no clone url`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs()).thenReturn(emptyList())
        val url =
            builder.buildCloneUrl(
                mrData("https://gitlab.com/acme/shop/-/merge_requests/3", "github"),
                CodeQaAnalysis(mrUrl = "https://gitlab.com/acme/shop/-/merge_requests/3"),
            )

        assertNull(url)
    }

    @Test
    fun `gitlab preferred config id without a matching config falls back to the anonymous url`() {
        whenever(gitlabConfigPort.findByActiveTrue())
            .thenReturn(listOf(gitlabConfig(1L, "https://gitlab.example.com", "tok")))
        val url =
            builder.buildCloneUrl(
                mrData("https://gitlab.example.com/group/proj/-/merge_requests/3", "gitlab"),
                CodeQaAnalysis(mrUrl = "https://gitlab.example.com/group/proj/-/merge_requests/3", gitConfigId = 77L),
            )

        assertEquals("https://gitlab.example.com/group/proj.git", url)
    }

    @Test
    fun `gitlab host lookup keeps an explicit port`() {
        whenever(gitlabConfigPort.findByActiveTrue())
            .thenReturn(listOf(gitlabConfig(1L, "https://gitlab.example.com:8443", "tok")))
        val url =
            builder.buildCloneUrl(
                mrData("https://gitlab.example.com:8443/group/proj/-/merge_requests/3", "gitlab"),
                CodeQaAnalysis(mrUrl = "https://gitlab.example.com:8443/group/proj/-/merge_requests/3"),
            )

        assertEquals("https://oauth2:tok@gitlab.example.com:8443/group/proj.git", url)
    }

    @Test
    fun `gitlab host lookup skips configs whose base url cannot be parsed`() {
        whenever(gitlabConfigPort.findByActiveTrue())
            .thenReturn(listOf(gitlabConfig(1L, "not a uri", "tok")))
        val url =
            builder.buildCloneUrl(
                mrData("https://gitlab.example.com/group/proj/-/merge_requests/3", "gitlab"),
                CodeQaAnalysis(mrUrl = "https://gitlab.example.com/group/proj/-/merge_requests/3"),
            )

        assertEquals("https://gitlab.example.com/group/proj.git", url)
    }

    @Test
    fun `github lookup ignores inactive configs and falls back to the anonymous url`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs())
            .thenReturn(listOf(githubConfig(1L, "acme", "shop", "tok").apply { active = false }))
        val url =
            builder.buildCloneUrl(
                mrData("https://github.com/acme/shop/pull/12", "github"),
                CodeQaAnalysis(mrUrl = "https://github.com/acme/shop/pull/12", gitConfigId = 1L),
            )

        assertEquals("https://github.com/acme/shop.git", url)
    }

    @Test
    fun `gitlab lookup without any active config falls back to the anonymous url`() {
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(emptyList())
        val url =
            builder.buildCloneUrl(
                mrData("https://gitlab.example.com/group/proj/-/merge_requests/3", "gitlab"),
                CodeQaAnalysis(mrUrl = "https://gitlab.example.com/group/proj/-/merge_requests/3", gitConfigId = 3L),
            )

        assertEquals("https://gitlab.example.com/group/proj.git", url)
    }

    @Test
    fun `unsupported platform yields no clone url`() {
        val url =
            builder.buildCloneUrl(
                mrData("https://bitbucket.org/group/proj/pull-request/3", "bitbucket"),
                CodeQaAnalysis(mrUrl = "https://bitbucket.org/group/proj/pull-request/3"),
            )

        assertNull(url)
    }

    @Test
    fun `gitlab url that cannot be parsed yields no clone url`() {
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(emptyList())
        val url =
            builder.buildCloneUrl(
                mrData("ht tp://bad url/-/merge_requests/1", "gitlab"),
                CodeQaAnalysis(mrUrl = "ht tp://bad url/-/merge_requests/1"),
            )

        assertNull(url)
    }
}
