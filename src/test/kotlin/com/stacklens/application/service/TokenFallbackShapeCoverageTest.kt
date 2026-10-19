package com.stacklens.application.service
import com.stacklens.adapter.out.codeqa.IntegrationCloneUrlBuilder
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.GithubConnectionTestPort
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.service.config.GithubConfigService
import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TokenFallbackShapeCoverageTest {
    private fun githubPort(vararg configs: GithubRepoConfig): GithubRepoConfigPort =
        mock<GithubRepoConfigPort>().apply {
            whenever(findAllGithubRepoConfigs()).thenReturn(configs.toList())
        }

    private fun gitlabPort(vararg configs: GitlabConfig): GitlabConfigPort =
        mock<GitlabConfigPort>().apply {
            whenever(findByActiveTrue()).thenReturn(configs.toList())
        }

    private fun gitlab(
        url: String,
        token: String,
        id: Long = 0L,
    ): GitlabConfig =
        GitlabConfig(
            id = id,
            name = url.substringAfterLast('/'),
            baseUrl = url,
            accessToken = token,
            active = true,
        )

    private fun repo(
        id: Long,
        token: String?,
        owner: String = "acme",
        repo: String = "shop",
    ): GithubRepoConfig =
        GithubRepoConfig(
            id = id,
            name = "$owner/$repo",
            owner = owner,
            repo = repo,
            accessToken = token,
            active = true,
        )

    @Test
    fun `a preferred github config that is not in the list falls back to the plain url`() {
        val builder = IntegrationCloneUrlBuilder(githubPort(repo(1L, "tok")), gitlabPort())

        val url = PrivateInvocation.invoke(builder, "buildGithubCloneUrl", "https://github.com/acme/shop/pull/1", 99L)

        assertEquals("https://github.com/acme/shop.git", url)
    }

    @Test
    fun `the preferred github config is used when it is in the list`() {
        val builder = IntegrationCloneUrlBuilder(githubPort(repo(1L, null), repo(2L, "tok")), gitlabPort())

        val url = PrivateInvocation.invoke(builder, "buildGithubCloneUrl", "https://github.com/acme/shop/pull/1", 2L)

        assertEquals("https://tok@github.com/acme/shop.git", url)
    }

    @Test
    fun `a preferred gitlab config that is not in the list clones without a token`() {
        val builder = IntegrationCloneUrlBuilder(githubPort(), gitlabPort(gitlab("https://gitlab.com/other/team", "gl-token")))

        val url =
            PrivateInvocation.invoke(
                builder,
                "buildGitlabCloneUrl",
                "https://gitlab.com/acme/shop/-/merge_requests/7",
                55L,
            ) as String

        assertFalse(url.contains("gl-token"), url)
    }

    @Test
    fun `the preferred gitlab config supplies its token`() {
        val builder =
            IntegrationCloneUrlBuilder(
                githubPort(),
                gitlabPort(gitlab("https://gitlab.com/other/team", "gl-token", id = 7L)),
            )

        val url =
            PrivateInvocation.invoke(
                builder,
                "buildGitlabCloneUrl",
                "https://gitlab.com/acme/shop/-/merge_requests/7",
                7L,
            ) as String

        assertTrue(url.contains("gl-token"), url)
    }

    @Test
    fun `the ai config token is only used when it actually carries one`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val connectionTest = mock<GithubConnectionTestPort>()
        whenever(connectionTest.testConnection(any(), any(), any()))
            .thenReturn(GithubConnectionTestPort.TestResult.Success("ok"))
        val service =
            GithubConfigService(
                githubRepoConfigRepo =
                    mock<GithubRepoConfigPort>().apply {
                        whenever(findGithubRepoConfigById(any())).thenReturn(repo(1L, "   "))
                    },
                aiConfigRepo = aiConfigRepo,
                githubConnectionTest = connectionTest,
                demoConfigFilter = PassthroughDemoConfigFilter.instance,
            )

        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        assertEquals(false, service.testGithubRepoConnection(1L)["success"])

        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig())
        assertEquals(false, service.testGithubRepoConnection(1L)["success"])

        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(githubToken = "  "))
        assertEquals(false, service.testGithubRepoConnection(1L)["success"])

        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(githubToken = "global"))
        assertEquals(true, service.testGithubRepoConnection(1L)["success"])
    }

    @Test
    fun `a configuration list without any entries yields an anonymous clone url`() {
        val builder = IntegrationCloneUrlBuilder(githubPort(), gitlabPort())

        val github = PrivateInvocation.invoke(builder, "buildGithubCloneUrl", "https://github.com/acme/shop/pull/1", 3L)
        val gitlab =
            PrivateInvocation.invoke(
                builder,
                "buildGitlabCloneUrl",
                "https://gitlab.example/acme/shop/-/merge_requests/1",
                3L,
            ) as String

        assertEquals("https://github.com/acme/shop.git", github)
        assertFalse(gitlab.contains("@"), gitlab)
    }

    @Test
    fun `mr data without a known platform is not cloned`() {
        val builder = IntegrationCloneUrlBuilder(githubPort(), gitlabPort())
        val mrData =
            mock<MrFetchPort.MrData>().apply {
                whenever(platform).thenReturn("bitbucket")
                whenever(mrUrl).thenReturn("https://bitbucket.org/x/y/pull-requests/1")
            }

        assertEquals(null, builder.buildCloneUrl(mrData, mock<CodeQaAnalysis>()))
    }
}
