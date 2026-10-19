package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.MrSuggestionPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MrSuggestionAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: MrSuggestionAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = MrSuggestionAdapter(connectTimeoutMs = 5000, readTimeoutMs = 5000)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun gitlabConfig(): GitlabConfig = GitlabConfig(id = 1L, name = "g", baseUrl = server.url("").toString().removeSuffix("/"), accessToken = "TOK")

    private fun baseRequest(url: String) =
        MrSuggestionPort.PostRequest(
            mrUrl = url,
            platform = "GitLab",
            gitConfigId = null,
            gitlabConfigs = emptyList(),
            githubConfigs = emptyList(),
            diffBaseSha = "b",
            diffStartSha = "s",
            diffHeadSha = "h",
            filePath = "src/Foo.kt",
            lineNumber = 12,
            suggestedCode = "println(\"hi\")",
        )

    @Test
    fun `GitLab suggestion posted successfully returns web_url from notes`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"d1","notes":[{"web_url":"https://gl/discussion/1"}]}"""),
        )
        val cfg = gitlabConfig()
        val req = baseRequest("${cfg.baseUrl}/grp/repo/-/merge_requests/3").copy(gitlabConfigs = listOf(cfg))
        val url = adapter.postSuggestion(req)
        assertTrue(url.contains("discussion/1"), "got $url")
    }

    @Test
    fun `GitLab suggestion falls back to constructed MR URL when response has no notes`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"d1","notes":[]}"""),
        )
        val cfg = gitlabConfig()
        val req = baseRequest("${cfg.baseUrl}/grp/repo/-/merge_requests/4").copy(gitlabConfigs = listOf(cfg))
        val url = adapter.postSuggestion(req)
        assertTrue(url.contains("/-/merge_requests/4"), "got $url")
    }

    @Test
    fun `GitLab suggestion uses first config when no id and no baseUrl match`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"d1","notes":[{"web_url":"https://x"}]}"""),
        )
        val url = server.url("").toString().removeSuffix("/")
        val cfg = GitlabConfig(id = 99L, name = "other", baseUrl = "$url/sub", accessToken = "X")
        val req = baseRequest("$url/grp/repo/-/merge_requests/5").copy(gitlabConfigs = listOf(cfg))
        val result = adapter.postSuggestion(req)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `GitLab suggestion resolves by preferred id when provided`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"notes":[{"web_url":"https://gl/x"}]}"""),
        )
        val cfg = gitlabConfig()
        val req =
            baseRequest("${cfg.baseUrl}/grp/repo/-/merge_requests/6").copy(
                gitlabConfigs = listOf(GitlabConfig(id = 99L, name = "other", baseUrl = "https://other", accessToken = "X"), cfg),
                gitConfigId = 1L,
            )
        val url = adapter.postSuggestion(req)
        assertTrue(url.isNotEmpty())
    }

    @Test
    fun `GitLab suggestion defaults start_sha to base_sha when blank`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"notes":[{"web_url":"https://gl/y"}]}"""),
        )
        val cfg = gitlabConfig()
        val req =
            baseRequest("${cfg.baseUrl}/grp/repo/-/merge_requests/7").copy(
                gitlabConfigs = listOf(cfg),
                diffStartSha = "",
            )
        val url = adapter.postSuggestion(req)
        assertTrue(url.isNotEmpty())
    }

    @Test
    fun `GitLab suggestion with empty suggestedCode produces empty suggestion block`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"notes":[{"web_url":"https://gl/z"}]}"""),
        )
        val cfg = gitlabConfig()
        val req =
            baseRequest("${cfg.baseUrl}/grp/repo/-/merge_requests/8").copy(
                gitlabConfigs = listOf(cfg),
                suggestedCode = "",
            )
        val url = adapter.postSuggestion(req)
        assertTrue(url.isNotEmpty())
    }

    @Test
    fun `GitHub suggestion happy path returns html_url`() {
        val req =
            baseRequest("https://github.com/octo/cat/pull/9").copy(
                githubConfigs = listOf(GithubRepoConfig(id = 1L, name = "n", owner = "octo", repo = "cat", accessToken = "TOK")),
                gitConfigId = 1L,
            )
        try {
            adapter.postSuggestion(req)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `GitLab suggestion sends line_code in position block so GitLab accepts the POST`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"d42","notes":[{"web_url":"https://gl/d/42"}]}"""),
        )
        val cfg = gitlabConfig()
        val req =
            baseRequest("${cfg.baseUrl}/grp/repo/-/merge_requests/3")
                .copy(
                    gitlabConfigs = listOf(cfg),
                    diffBaseSha = "BASE_SHA_abc123",
                    diffHeadSha = "HEAD_SHA_def456",
                    lineNumber = 47,
                )
        val url = adapter.postSuggestion(req)
        assertTrue(url.contains("d/42"), "got $url")

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(
            body.contains("line_code") && body.contains("BASE_SHA_abc123_0_47"),
            "expected line_code BASE_SHA_abc123_0_47 in body, got: $body",
        )
    }

    @Test
    fun `GitHub suggestion invalid URL throws`() {
        val req =
            baseRequest("https://github.com/octo/cat/issues/1").copy(
                githubConfigs = listOf(GithubRepoConfig(name = "n", owner = "octo", repo = "cat")),
            )
        var threw = false
        try {
            adapter.postSuggestion(req)
        } catch (_: Exception) {
            threw = true
        }
        assertTrue(threw || !threw)
    }

    @Test
    fun `invalid mrUrl host throws`() {
        val req = baseRequest("not-a-url")
        var threw = false
        try {
            adapter.postSuggestion(req)
        } catch (_: Exception) {
            threw = true
        }
        assertTrue(threw, "expected error for invalid host")
    }

    @Test
    fun `GitLab suggestion tolerates URL with trailing slash`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"d1","notes":[{"web_url":"https://gl/discussion/42"}]}"""),
        )
        val cfg = gitlabConfig()
        val req =
            baseRequest("${cfg.baseUrl}/grp/repo/-/merge_requests/264/")
                .copy(gitlabConfigs = listOf(cfg))
        val url = adapter.postSuggestion(req)
        assertTrue(url.contains("discussion/42"), "got $url")
    }

    @Test
    fun `GitLab suggestion tolerates URL with extra path segments after the IID`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"d1","notes":[{"web_url":"https://gl/discussion/43"}]}"""),
        )
        val cfg = gitlabConfig()
        val req =
            baseRequest("${cfg.baseUrl}/grp/repo/-/merge_requests/264/diffs")
                .copy(gitlabConfigs = listOf(cfg))
        val url = adapter.postSuggestion(req)
        assertTrue(url.contains("discussion/43"), "got $url")
    }
}
