package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IntegrationCloneUrlBuilderMatchedConfigCoverageTest {
    private val githubConfigPort = mock<GithubRepoConfigPort>()
    private val gitlabConfigPort = mock<GitlabConfigPort>()
    private val builder = IntegrationCloneUrlBuilder(githubConfigPort, gitlabConfigPort)

    @Test
    fun `the github config behind the preferred id supplies the credentials`() {
        whenever(githubConfigPort.findAllGithubRepoConfigs())
            .thenReturn(listOf(githubConfig(1L, "unused-token"), githubConfig(2L, "gh-token")))

        val url = builder.buildCloneUrl(mrData("github", "https://github.com/acme/shop/pull/4"), analysis(2L))

        assertEquals("https://gh-token@github.com/acme/shop.git", url)
    }

    @Test
    fun `the gitlab config behind the preferred id supplies the credentials`() {
        whenever(gitlabConfigPort.findByActiveTrue())
            .thenReturn(listOf(gitlabConfig(1L, "unused-token"), gitlabConfig(2L, "gl-token")))

        val url =
            builder.buildCloneUrl(
                mrData("gitlab", "https://gitlab.example.com/team/app/-/merge_requests/4"),
                analysis(2L),
            )

        assertEquals("https://oauth2:gl-token@gitlab.example.com/team/app.git", url)
    }

    private fun analysis(gitConfigId: Long?) = CodeQaAnalysis(mrUrl = "ignored", gitConfigId = gitConfigId)

    private fun mrData(
        platform: String,
        mrUrl: String,
    ) = MrFetchPort.MrData(
        mrUrl = mrUrl,
        platform = platform,
        title = "t",
        description = "d",
        sourceBranch = "feature",
        targetBranch = "main",
        authorName = "a",
        state = "opened",
        diffText = "",
        changedFiles = emptyList(),
    )

    private fun githubConfig(
        id: Long,
        token: String,
    ) = GithubRepoConfig(id = id, name = "shop", owner = "acme", repo = "shop", accessToken = token)

    private fun gitlabConfig(
        id: Long,
        token: String,
    ) = GitlabConfig(id = id, name = "app", baseUrl = "https://gitlab.example.com", accessToken = token)
}
