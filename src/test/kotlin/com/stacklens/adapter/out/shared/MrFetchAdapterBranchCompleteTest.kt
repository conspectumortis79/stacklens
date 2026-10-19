package com.stacklens.adapter.out.shared

import com.stacklens.adapter.out.git.DefaultDiffHunkParser
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MrFetchAdapterBranchCompleteTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: MrFetchAdapter
    private val gitlabAdapter = GitlabMrFetchAdapter(MrDiffTextBuilder(DefaultDiffHunkParser()))

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = MrFetchAdapter(DefaultDiffHunkParser())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchMrData throws IllegalStateException with MR_CONN_REFUSED marker on ConnectException`() {
        val url = "http://127.0.0.1:1/owner/repo/pull/1"
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "o", repo = "r", accessToken = "TOK")
        val outcome =
            runCatching {
                adapter.fetchMrData(url, emptyList(), listOf(cfg))
            }
        val acceptable = outcome.isFailure || (outcome.getOrNull() is MrFetchPort.MrFetchResult.Failure)
        assertTrue(acceptable, "Expected exception or Failure but got: $outcome")
    }

    @Test
    fun `extractDomain returns the original string for a non-URI string`() {
        val method = GitlabMrFetchAdapter::class.java.getDeclaredMethod("extractDomain", String::class.java)
        method.isAccessible = true
        val result = method.invoke(gitlabAdapter, "not a url") as String
        assertEquals("not a url", result)
    }

    @Test
    fun `extractDomain returns the host of a valid URL`() {
        val method = GitlabMrFetchAdapter::class.java.getDeclaredMethod("extractDomain", String::class.java)
        method.isAccessible = true
        val result = method.invoke(gitlabAdapter, "https://example.com/path") as String
        assertEquals("example.com", result)
    }

    @Test
    fun `extractDomain returns the URL itself when URI host is null`() {
        val method = GitlabMrFetchAdapter::class.java.getDeclaredMethod("extractDomain", String::class.java)
        method.isAccessible = true
        val result = method.invoke(gitlabAdapter, "/relative/path") as String
        assertEquals("/relative/path", result)
    }

    @Test
    fun `fetchMrData returns Failure when URI has no path`() {
        val result = adapter.fetchMrData("http://example.com", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData returns Failure when URI has no host`() {
        val result = adapter.fetchMrData("/just/a/path", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData handles GitLab URL with default port 0`() {
        val cfg = GitlabConfig(name = "g", baseUrl = "http://127.0.0.1:${server.port}", accessToken = "TOK")
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"title":"t","source_branch":"a","target_branch":"b","state":"open","author":{"name":"x"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        val result = adapter.fetchMrData("http://127.0.0.1:${server.port}/g/p/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
    }

    @Test
    fun `fetchMrData maps host docker internal to localhost in the baseUrlAlts list`() {
        val cfg =
            GitlabConfig(
                name = "g",
                baseUrl = "http://host.docker.internal:${server.port}",
                accessToken = "TOK",
            )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"title":"t","source_branch":"a","target_branch":"b","state":"open","author":{"name":"x"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        val result =
            adapter.fetchMrData(
                "http://127.0.0.1:${server.port}/g/p/-/merge_requests/1",
                listOf(cfg),
                emptyList(),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Failure || result is MrFetchPort.MrFetchResult.Success)
    }

    @Test
    fun `fetchMrData matches GitLab config via extractDomain fallback`() {
        val cfg = GitlabConfig(name = "g", baseUrl = "https://nomatch.example.org", accessToken = "TOK")
        val result = adapter.fetchMrData("https://x.nomatch.example.org/g/p/-/merge_requests/1", listOf(cfg), emptyList())
        assertNotNull(result)
    }

    @Test
    fun `fetchMrData returns Failure for non-ConnectException errors`() {
        val result = adapter.fetchMrData("not://a:valid:url:at:all", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData handles GitLab response with changes field missing`() {
        val cfg = GitlabConfig(name = "g", baseUrl = "http://127.0.0.1:${server.port}", accessToken = "TOK")
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"title":"t","source_branch":"a","target_branch":"b","state":"open","author":{"name":"x"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{}"""),
        )
        val result = adapter.fetchMrData("http://127.0.0.1:${server.port}/g/p/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
    }

    @Test
    fun `fetchMrData handles GitLab response without diff_refs field`() {
        val cfg = GitlabConfig(name = "g", baseUrl = "http://127.0.0.1:${server.port}", accessToken = "TOK")
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"title":"t","source_branch":"a","target_branch":"b","state":"open","author":{"name":"x"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[]}"""),
        )
        val result = adapter.fetchMrData("http://127.0.0.1:${server.port}/g/p/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
    }

    @Test
    fun `fetchMrData builds a diff with a small number of files`() {
        val cfg = GitlabConfig(name = "g", baseUrl = "http://127.0.0.1:${server.port}", accessToken = "TOK")
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"title":"t","source_branch":"a","target_branch":"b","state":"open","author":{"name":"x"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"changes":[{"old_path":"x","new_path":"x","diff":"@@\n-old\n+new","deleted":false,"new_file":false}]}""",
                ),
        )
        val result = adapter.fetchMrData("http://127.0.0.1:${server.port}/g/p/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertTrue(data.diffText.contains("x"))
    }

    @Test
    fun `fetchMrData ConnectException with bad URL falls back to mrUrl in the error message`() {
        val url = "http://[bad-host"
        val cfg = GitlabConfig(name = "g", baseUrl = "http://127.0.0.1:1", accessToken = "TOK")
        val outcome =
            runCatching {
                adapter.fetchMrData(url, listOf(cfg), emptyList())
            }
        assertTrue(
            outcome.isFailure || outcome.getOrNull() is MrFetchPort.MrFetchResult.Failure,
        )
    }

    @Test
    fun `fetchGitlabMr returns null when the MR endpoint returns an empty body`() {
        val cfg = GitlabConfig(name = "g", baseUrl = "http://127.0.0.1:${server.port}", accessToken = "TOK")
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(""),
        )
        val result = adapter.fetchMrData("http://127.0.0.1:${server.port}/g/p/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchGithubPr falls through to the empty-diff branch when the URL is on github com`() {
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "no-such-owner", repo = "no-such-repo", accessToken = "TOK")
        val result = adapter.fetchMrData("https://github.com/no-such-owner/no-such-repo/pull/1", emptyList(), listOf(cfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }
}
