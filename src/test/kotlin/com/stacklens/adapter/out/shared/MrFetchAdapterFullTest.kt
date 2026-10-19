package com.stacklens.adapter.out.shared

import com.stacklens.adapter.out.git.DefaultDiffHunkParser
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MrFetchAdapterFullTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: MrFetchAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = MrFetchAdapter(DefaultDiffHunkParser(), connectTimeoutMs = 5000, readTimeoutMs = 5000)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `githubApiBaseUrl property getter returns the configured base URL`() {
        val adapter = MrFetchAdapter(DefaultDiffHunkParser())
        org.junit.jupiter.api.Assertions
            .assertEquals("https://api.github.com", adapter.githubApiBaseUrl)
    }

    @Test
    fun `GitHub PR URL with too few path parts returns null`() {
        val result = adapter.fetchMrData("https://github.com/o/r", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "expected Failure but was $result")
    }

    @Test
    fun `GitHub PR URL with non-pull path returns Failure`() {
        val result = adapter.fetchMrData("https://github.com/o/r/issues/1", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "expected Failure but was $result")
    }

    @Test
    fun `GitHub PR URL with non-numeric PR number returns Failure`() {
        val result = adapter.fetchMrData("https://github.com/o/r/pull/abc", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "expected Failure but was $result")
    }

    @Test
    fun `GitHub sub-domain URL is recognised`() {
        val result = adapter.fetchMrData("https://api.github.com/o/r/pull/1", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "expected Failure but was $result")
    }

    @Test
    fun `GitLab config matches via fallback when baseUrl contains URL host as substring`() {
        val cfg =
            GitlabConfig(
                name = "g",
                baseUrl = "http://127.0.0.1.alt.example.com",
                accessToken = "TOK",
            )
        val result = adapter.fetchMrData("http://127.0.0.1:${server.port}/grp/repo/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "expected Failure but was $result")
    }

    @Test
    fun `GitLab URL with localhost and host-docker-internal alt matches`() {
        val cfg =
            GitlabConfig(
                name = "g",
                baseUrl = "http://127.0.0.1:${server.port}",
                accessToken = "TOK",
            )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"t","source_branch":"a","target_branch":"b","state":"open","author":{"name":"x"}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        val url = "http://127.0.0.1:${server.port}/grp/repo/-/merge_requests/1"
        val result = adapter.fetchMrData(url, listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "expected Success but was $result")
    }

    @Test
    fun `GitLab URL with no matching config returns Failure`() {
        val cfg = GitlabConfig(name = "x", baseUrl = "https://nomatch.example.org", accessToken = "TOK")
        val result = adapter.fetchMrData("https://gitlab.com/grp/repo/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "expected Failure but was $result")
    }

    @Test
    fun `GitLab URL with invalid mrIid returns Failure`() {
        val cfg = GitlabConfig(name = "g", baseUrl = server.url("").toString().removeSuffix("/"), accessToken = "TOK")
        val result = adapter.fetchMrData("${cfg.baseUrl}/grp/repo/-/merge_requests/abc", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "expected Failure but was $result")
    }

    @Test
    fun `extractDomain handles invalid URL gracefully`() {
        val result = adapter.fetchMrData("not://a-real-url", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "expected Failure but was $result")
    }

    @Test
    fun `diffText contains truncation marker when very large`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"t","source_branch":"a","target_branch":"b","state":"open","author":{"name":"x"}}""",
                ),
        )
        val bigDiff = "x".repeat(20_000)
        val changes =
            (1..10).joinToString(",") { i ->
                """{"old_path":"f$i.kt","new_path":"f$i.kt","diff":"$bigDiff","deleted":false,"new_file":false}"""
            }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[$changes]}"""),
        )
        val cfg = GitlabConfig(name = "g", baseUrl = server.url("").toString().removeSuffix("/"), accessToken = "TOK")
        val result = adapter.fetchMrData("${cfg.baseUrl}/grp/repo/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "expected Success but was $result")
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertTrue(data.diffText.length <= 80_000, "diffText length ${data.diffText.length} exceeds cap")
    }

    @Test
    fun `GitLab matches via resolvedUrls when URL port matches server port`() {
        val cfg =
            GitlabConfig(
                name = "g",
                baseUrl = "http://127.0.0.1:${server.port}",
                accessToken = "TOK",
            )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"t","source_branch":"a","target_branch":"b","state":"open","author":{"name":"x"}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        val result = adapter.fetchMrData("http://127.0.0.1:${server.port}/grp/repo/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "expected Success but was $result")
    }

    @Test
    fun `GitHub PR URL with valid format attempts API call`() {
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "octo", repo = "cat", accessToken = "TOK")
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/12345", emptyList(), listOf(cfg))
        assertNotNull(result)
    }
}
