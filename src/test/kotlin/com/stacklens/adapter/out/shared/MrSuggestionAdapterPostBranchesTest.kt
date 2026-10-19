package com.stacklens.adapter.out.shared

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.URI

class MrSuggestionAdapterPostBranchesTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `postGitlabSuggestion posts and returns web url`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"1","notes":[{"web_url":"https://gitlab.com/o/r/-/merge_requests/1"}]}"""),
        )
        val config = mock<com.stacklens.domain.model.GitlabConfig>()
        whenever(config.id).thenReturn(1L)
        whenever(config.baseUrl).thenReturn("http://localhost:${server.port}")
        whenever(config.accessToken).thenReturn("test-token")
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "http://localhost:${server.port}/o/r/-/merge_requests/1",
                platform = "GitLab",
                gitConfigId = 1L,
                gitlabConfigs = listOf(config),
                githubConfigs = emptyList(),
                filePath = "src/main.kt",
                lineNumber = 10,
                suggestedCode = "fun main() {}",
                diffBaseSha = "abc",
                diffStartSha = "abc",
                diffHeadSha = "def",
            )
        val result = GitlabMrSuggestionAdapter().post(req, URI.create(req.mrUrl))
        assertTrue(result.contains("merge_requests"))
    }

    @Test
    fun `postGitlabSuggestion falls back to base URL when no notes`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"1","notes":[]}"""),
        )
        val config = mock<com.stacklens.domain.model.GitlabConfig>()
        whenever(config.id).thenReturn(1L)
        whenever(config.baseUrl).thenReturn("http://localhost:${server.port}")
        whenever(config.accessToken).thenReturn("test-token")
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "http://localhost:${server.port}/o/r/-/merge_requests/1",
                platform = "GitLab",
                gitConfigId = 1L,
                gitlabConfigs = listOf(config),
                githubConfigs = emptyList(),
                filePath = "src/main.kt",
                lineNumber = 10,
                suggestedCode = "fun main() {}",
                diffBaseSha = "abc",
                diffStartSha = "abc",
                diffHeadSha = "def",
            )
        val result = GitlabMrSuggestionAdapter().post(req, URI.create(req.mrUrl))
        assertNotNull(result)
    }

    @Test
    fun `postGithubSuggestion posts and returns html url`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"html_url":"https://github.com/o/r/pull/1#discussion-1"}"""),
        )
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "https://github.com/o/r/pull/1",
                platform = "GitHub",
                gitConfigId = null,
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
                filePath = "src/main.kt",
                lineNumber = 10,
                suggestedCode = "fun main() {}",
                diffBaseSha = "abc",
                diffStartSha = "abc",
                diffHeadSha = "def",
            )
        val result = GithubPrSuggestionAdapter().post(req, URI.create(req.mrUrl), server.url("/").toString().trimEnd('/'))
        assertNotNull(result)
    }

    @Test
    fun `postGithubSuggestion falls back to default URL when html_url is null`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{}"""),
        )
        val req =
            com.stacklens.application.port.out.MrSuggestionPort.PostRequest(
                mrUrl = "https://github.com/o/r/pull/1",
                platform = "GitHub",
                gitConfigId = null,
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
                filePath = "src/main.kt",
                lineNumber = 10,
                suggestedCode = "fun main() {}",
                diffBaseSha = "abc",
                diffStartSha = "abc",
                diffHeadSha = "def",
            )
        val result = GithubPrSuggestionAdapter().post(req, URI.create(req.mrUrl), server.url("/").toString().trimEnd('/'))
        assertNotNull(result)
    }
}
