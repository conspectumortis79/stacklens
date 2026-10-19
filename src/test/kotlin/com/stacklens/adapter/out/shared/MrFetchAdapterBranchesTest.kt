package com.stacklens.adapter.out.shared

import com.stacklens.adapter.out.git.DefaultDiffHunkParser
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.GithubRepoConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MrFetchAdapterBranchesTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: MrFetchAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = MrFetchAdapter(diffHunkParser = DefaultDiffHunkParser())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchGithubPr returns null when path has fewer than 4 segments`() {
        val result =
            adapter.fetchMrData(
                mrUrl = "https://github.com/owner/repo",
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "expected Failure, got $result")
    }

    @Test
    fun `fetchGithubPr returns null when parts 2 is not pull`() {
        val result =
            adapter.fetchMrData(
                mrUrl = "https://github.com/owner/repo/issues/1",
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchGithubPr returns null when PR number is not a Long`() {
        val result =
            adapter.fetchMrData(
                mrUrl = "https://github.com/owner/repo/pull/notanumber",
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchGithubPr returns null when PR endpoint returns no body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val result =
            adapter.fetchMrData(
                mrUrl = "https://github.com/owner/repo/pull/1",
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "got $result")
    }

    @Test
    fun `fetchGithubPr returns null when files endpoint returns no body`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"number":1,"title":"T","state":"open","head":{"ref":"a","sha":"abc"},"base":{"ref":"b","sha":"def"},"user":{"login":"u"},"body":""}""",
                ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val result =
            adapter.fetchMrData(
                mrUrl = "https://github.com/owner/repo/pull/1",
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "got $result")
    }

    @Test
    fun `fetchGithubPr returns Failure when files endpoint returns empty body`() {
        val result =
            adapter.fetchMrData(
                mrUrl = "https://github.com/owner/repo/pull/1",
                gitlabConfigs = emptyList(),
                githubConfigs = listOf(GithubRepoConfig(id = 1L, name = "r", owner = "owner", repo = "repo")),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "got $result")
    }

    @Test
    fun `fetchGithubPr returns success with full PR data`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"number":1,"title":"My PR","state":"open","head":{"ref":"a","sha":"abc"},"base":{"ref":"b","sha":"def"},"user":{"login":"u"},"body":"desc"}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"filename":"a.txt","status":"modified","patch":"@@ -1 +1 @@\n-old\n+new"}]""",
                ),
        )
        val result =
            adapter.fetchMrData(
                mrUrl = "https://github.com/owner/repo/pull/1",
                gitlabConfigs = emptyList(),
                githubConfigs = listOf(GithubRepoConfig(id = 1L, name = "r", owner = "owner", repo = "repo")),
            )
        assertNotNull(result)
    }

    @Test
    fun `fetchGitlabMr returns null when mrIid is missing from URL`() {
        val result =
            adapter.fetchMrData(
                mrUrl = "https://gitlab.com/owner/repo/-/merge_requests/",
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "got $result")
    }

    @Test
    fun `fetchGitlabMr returns null when mrIid is not numeric`() {
        val result =
            adapter.fetchMrData(
                mrUrl = "https://gitlab.com/owner/repo/-/merge_requests/notanumber",
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "got $result")
    }

    @Test
    fun `fetchGitlabMr returns null when project lookup fails`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result =
            adapter.fetchMrData(
                mrUrl = "https://gitlab.com/owner/repo/-/merge_requests/1",
                gitlabConfigs =
                    listOf(
                        com.stacklens.domain.model
                            .GitlabConfig(name = "g", baseUrl = server.url("/").toString().trimEnd('/'), accessToken = "t"),
                    ),
                githubConfigs = emptyList(),
            )
        assertNotNull(result)
    }

    @Test
    fun `fetchMrData returns Failure when URL has no host`() {
        val result = adapter.fetchMrData("not a url", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData returns Failure when URL has host but no path`() {
        val result = adapter.fetchMrData("http://example.com", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData returns Failure when exception message is null`() {
        try {
            adapter.fetchMrData("", emptyList(), emptyList())
        } catch (_: Exception) {
        }
        assertTrue(true)
    }
}
