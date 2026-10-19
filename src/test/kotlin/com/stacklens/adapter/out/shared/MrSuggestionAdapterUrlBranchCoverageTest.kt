package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.MrSuggestionPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MrSuggestionAdapterUrlBranchCoverageTest {
    private val server = MockWebServer()

    @BeforeEach
    fun startServer() {
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun adapter(): MrSuggestionAdapter =
        MrSuggestionAdapter().also {
            it.githubApiBaseUrl = server.url("/").toString().trimEnd('/')
        }

    private fun gitlabConfig(
        id: Long,
        baseUrl: String,
        token: String = "token",
    ): GitlabConfig {
        val config = mock<GitlabConfig>()
        whenever(config.id).thenReturn(id)
        whenever(config.baseUrl).thenReturn(baseUrl)
        whenever(config.accessToken).thenReturn(token)
        return config
    }

    private fun githubConfig(
        id: Long,
        token: String?,
    ): GithubRepoConfig {
        val config = mock<GithubRepoConfig>()
        whenever(config.id).thenReturn(id)
        whenever(config.accessToken).thenReturn(token)
        return config
    }

    private fun request(
        mrUrl: String,
        gitlabConfigs: List<GitlabConfig> = emptyList(),
        githubConfigs: List<GithubRepoConfig> = emptyList(),
        gitConfigId: Long? = null,
    ) = MrSuggestionPort.PostRequest(
        mrUrl = mrUrl,
        platform = "GitLab",
        gitConfigId = gitConfigId,
        gitlabConfigs = gitlabConfigs,
        githubConfigs = githubConfigs,
        diffBaseSha = "base",
        diffStartSha = "",
        diffHeadSha = "head",
        filePath = "src/Main.kt",
        lineNumber = 7,
        suggestedCode = "val answer = 42",
    )

    private val localBase: String
        get() = "http://localhost:${server.port}"

    @Test
    fun `a gitlab url without a numeric mr iid is rejected`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                adapter().postSuggestion(request("$localBase/o/r/-/merge_requests/not-a-number"))
            }

        assertTrue(error.message!!.contains("Cannot parse MR IID"))
    }

    @Test
    fun `a gitlab url with an mr iid beyond the long range is rejected`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                adapter().postSuggestion(request("$localBase/o/r/-/merge_requests/99999999999999999999"))
            }

        assertTrue(error.message!!.contains("Cannot parse MR IID"))
    }

    @Test
    fun `a url without a path is rejected before any provider is chosen`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                adapter().postSuggestion(request("http://gitlab.example"))
            }

        assertTrue(error.message!!.contains("Unsupported MR provider"))
    }

    @Test
    fun `an unknown host with a non-merge-request path is rejected instead of posting to GitLab`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                adapter().postSuggestion(request("https://bitbucket.example/o/r/pull/4"))
            }

        assertTrue(error.message!!.contains("Unsupported MR provider for host: bitbucket.example"))
    }

    @Test
    fun `a gitlab url without a merge request path is rejected`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                adapter().postSuggestion(request("$localBase/o/r/-/merge_requests"))
            }

        assertTrue(error.message!!.contains("Cannot parse MR IID"))
    }

    @Test
    fun `a url without a host is rejected`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                adapter().postSuggestion(request("relative/path"))
            }

        assertTrue(error.message!!.contains("Cannot parse host"))
    }

    @Test
    fun `an unsupported provider host is rejected`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                adapter().postSuggestion(request("https://bitbucket.org/o/r/pull-requests/1"))
            }

        assertTrue(error.message!!.contains("Unsupported MR provider"))
    }

    @Test
    fun `a gitlab url without any configuration is rejected`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                adapter().postSuggestion(request("https://gitlab.example/o/r/-/merge_requests/4"))
            }

        assertTrue(error.message!!.contains("No GitLab config available"))
    }

    @Test
    fun `a gitlab url without a matching id or base url falls back to the first config`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"1","notes":[{"web_url":"$localBase/discussion"}]}"""),
        )
        val config = gitlabConfig(id = 99L, baseUrl = localBase)

        val result =
            adapter().postSuggestion(
                request("http://gitlab.example/o/r/-/merge_requests/4", gitlabConfigs = listOf(config), gitConfigId = 7L),
            )

        assertEquals("$localBase/discussion", result)
    }

    @Test
    fun `a gitlab url is matched by its base url when the id does not fit`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"1","notes":[]}"""),
        )
        val config = gitlabConfig(id = 99L, baseUrl = "$localBase/")

        val result =
            adapter().postSuggestion(
                request("$localBase/o/r/-/merge_requests/5", gitlabConfigs = listOf(config), gitConfigId = 3L),
            )

        assertEquals("$localBase/o/r/-/merge_requests/5", result)
    }

    @Test
    fun `a github pull request url with a short path is rejected`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                adapter().postSuggestion(request("https://github.com/o/r"))
            }

        assertTrue(error.message!!.contains("Cannot parse GitHub PR URL"))
    }

    @Test
    fun `a github url that is not a pull request is rejected`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                adapter().postSuggestion(request("https://github.com/o/r/issues/12"))
            }

        assertTrue(error.message!!.contains("Cannot parse GitHub PR URL"))
    }

    @Test
    fun `a github pull request url with a non numeric number is rejected`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                adapter().postSuggestion(request("https://github.com/o/r/pull/later"))
            }

        assertTrue(error.message!!.contains("Cannot parse PR number"))
    }

    @Test
    fun `a github answer without a body falls back to the pull request url`() {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = adapter().postSuggestion(request("https://www.github.com/o/r/pull/12"))

        assertEquals("https://github.com/o/r/pull/12", result)
    }

    @Test
    fun `a github suggestion with a blank token posts without an authorization header`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"html_url":"https://github.com/o/r/pull/12#discussion_r1"}"""),
        )

        val result =
            adapter().postSuggestion(
                request(
                    "https://github.com/o/r/pull/12",
                    githubConfigs = listOf(githubConfig(id = 1L, token = "  ")),
                    gitConfigId = 1L,
                ),
            )

        assertEquals("https://github.com/o/r/pull/12#discussion_r1", result)
        assertEquals(
            null,
            server.takeRequest().getHeader("Authorization"),
        )
    }
}
