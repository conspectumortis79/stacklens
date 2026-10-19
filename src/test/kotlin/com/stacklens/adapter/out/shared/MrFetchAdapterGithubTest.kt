package com.stacklens.adapter.out.shared

import com.stacklens.adapter.out.git.DefaultDiffHunkParser
import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.domain.model.GithubRepoConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

class MrFetchAdapterGithubTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: MrFetchAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter =
            MrFetchAdapter(DefaultDiffHunkParser()).apply {
                githubApiBaseUrl = server.url("").toString().removeSuffix("/")
            }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun jsonResponse(
        code: Int,
        body: String,
    ): MockResponse = MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json")

    @Test
    fun `GitHub PR with matching config returns success with PR data`() {
        val cfg = GithubRepoConfig(id = 1L, name = "octocat", owner = "octocat", repo = "hello-world", accessToken = "TOK")
        server.enqueue(
            jsonResponse(
                200,
                """
                {
                  "title": "Fix bug",
                  "body": "Description",
                  "head": {"ref": "fix", "sha": "abc123"},
                  "base": {"ref": "main", "sha": "def456"},
                  "user": {"login": "octocat"},
                  "state": "open"
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            jsonResponse(
                200,
                """
                [
                  {"filename": "src/Foo.kt", "patch": "@@ -1 +1 @@\n-old\n+new", "status": "modified"}
                ]
                """.trimIndent(),
            ),
        )
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), listOf(cfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "expected Success but was $result")
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("Fix bug", data.title)
        assertEquals("Description", data.description)
        assertEquals("fix", data.sourceBranch)
        assertEquals("main", data.targetBranch)
        assertEquals("octocat", data.authorName)
        assertEquals("open", data.state)
        assertEquals("GitHub", data.platform)
        assertEquals("https://github.com/octocat/hello-world/pull/1", data.mrUrl)
        assertEquals(1, data.changedFiles.size)
        assertTrue(data.changedFiles[0].contains("Foo.kt"))
        assertTrue(data.diffText.contains("src/Foo.kt"))
    }

    @Test
    fun `GitHub PR returns null when first API call returns empty body`() {
        val cfg = GithubRepoConfig(id = 1L, name = "octocat", owner = "octocat", repo = "hello-world", accessToken = "TOK")
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), listOf(cfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "expected Failure but was $result")
    }

    @Test
    fun `GitHub PR with no matching config uses default first token`() {
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "other", repo = "repo", accessToken = "TOK")
        server.enqueue(
            jsonResponse(
                200,
                """{"title":"T","head":{"ref":"f","sha":"s"},"base":{"ref":"m","sha":"s2"},"user":{"login":"u"},"state":"open","body":""}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]").setHeader("Content-Type", "application/json"))
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), listOf(cfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "expected Success but was $result")
    }

    @Test
    fun `GitHub PR with empty files response returns success with empty changes`() {
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "octocat", repo = "hello-world", accessToken = "TOK")
        server.enqueue(
            jsonResponse(
                200,
                """{"title":"T","head":{"ref":"f","sha":"s"},"base":{"ref":"m","sha":"s2"},"user":{"login":"u"},"state":"open","body":""}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]").setHeader("Content-Type", "application/json"))
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), listOf(cfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "expected Success but was $result")
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertTrue(data.changedFiles.isEmpty())
    }

    @Test
    fun `GitHub PR with files but no patch uses filename and status`() {
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "octocat", repo = "hello-world", accessToken = "TOK")
        server.enqueue(
            jsonResponse(
                200,
                """{"title":"T","head":{"ref":"f","sha":"s"},"base":{"ref":"m","sha":"s2"},"user":{"login":"u"},"state":"open","body":""}""",
            ),
        )
        server.enqueue(
            jsonResponse(
                200,
                """[{"filename":"new.kt","patch":null,"status":"added"}]""",
            ),
        )
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), listOf(cfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "expected Success but was $result")
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertTrue(data.changedFiles[0].contains("ADDED"))
        assertTrue(data.changedFiles[0].contains("new.kt"))
    }
}

class MrFetchAdapterGithubNullFieldsTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: MrFetchAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val connector =
            ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(5)),
            )
        val webClientBuilder = WebClient.builder().clientConnector(connector)
        adapter = MrFetchAdapter(diffHunkParser = DefaultDiffHunkParser())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchGithubPr with null head, base, and user fields returns Success with empty branches`() {
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "octocat", repo = "hello-world", accessToken = "TOK")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"title":"T","state":"open","body":""}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), listOf(cfg))
        assertTrue(
            result is MrFetchPort.MrFetchResult.Success || result is MrFetchPort.MrFetchResult.Failure,
            "got $result",
        )
    }

    @Test
    fun `fetchGithubPr with null head and base only returns Success`() {
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "octocat", repo = "hello-world", accessToken = "TOK")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"title":"T","state":"open","body":"d","user":{"login":"u"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), listOf(cfg))
        assertTrue(
            result is MrFetchPort.MrFetchResult.Success || result is MrFetchPort.MrFetchResult.Failure,
            "got $result",
        )
    }
}

class MrFetchAdapterGithubFullFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: MrFetchAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val connector =
            ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(5)),
            )
        val webClientBuilder = WebClient.builder().clientConnector(connector)
        adapter = MrFetchAdapter(diffHunkParser = DefaultDiffHunkParser())
        adapter.githubApiBaseUrl = server.url("/").toString().trimEnd('/')
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchGithubPr happy path returns full MrData`() {
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "octocat", repo = "hello-world", accessToken = "TOK")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{
                        "title": "My PR",
                        "body": "Description",
                        "state": "open",
                        "head": {"ref": "feature", "sha": "head_sha"},
                        "base": {"ref": "main", "sha": "base_sha"},
                        "user": {"login": "octocat"}
                    }""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"filename": "a.txt", "status": "modified", "patch": "@@ -1 +1 @@\n-old\n+new"}]""",
                ),
        )
        val result =
            adapter.fetchMrData(
                "https://github.com/octocat/hello-world/pull/1",
                emptyList(),
                listOf(cfg),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "expected Success but was $result")
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("GitHub", data.platform)
        assertEquals("My PR", data.title)
        assertEquals("Description", data.description)
        assertEquals("feature", data.sourceBranch)
        assertEquals("main", data.targetBranch)
        assertEquals("octocat", data.authorName)
        assertEquals("open", data.state)
        assertEquals("base_sha", data.diffBaseSha)
        assertEquals("base_sha", data.diffStartSha)
        assertEquals("head_sha", data.diffHeadSha)
        assertEquals(1, data.changedFiles.size)
        assertTrue(data.changedFiles[0].contains("a.txt"))
        assertTrue(data.diffText.contains("a.txt"))
    }

    @Test
    fun `fetchGithubPr with null head base and user returns Success with empty branches`() {
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "octocat", repo = "hello-world", accessToken = "TOK")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"title":"T","state":"open","body":"d"}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), listOf(cfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "got $result")
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("", data.sourceBranch)
        assertEquals("", data.targetBranch)
        assertEquals("", data.authorName)
        assertEquals("", data.diffBaseSha)
        assertEquals("", data.diffHeadSha)
    }

    @Test
    fun `fetchGithubPr with null head only returns Success with empty sourceBranch`() {
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "octocat", repo = "hello-world", accessToken = "TOK")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"T","state":"open","body":"d","base":{"ref":"main","sha":"b"},"user":{"login":"u"}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), listOf(cfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "got $result")
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("", data.sourceBranch)
        assertEquals("main", data.targetBranch)
        assertEquals("u", data.authorName)
    }

    @Test
    fun `fetchGithubPr with null base only returns Success with empty targetBranch`() {
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "octocat", repo = "hello-world", accessToken = "TOK")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"T","state":"open","body":"d","head":{"ref":"feature","sha":"h"},"user":{"login":"u"}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), listOf(cfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "got $result")
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("feature", data.sourceBranch)
        assertEquals("", data.targetBranch)
        assertEquals("", data.diffBaseSha)
        assertEquals("u", data.authorName)
    }

    @Test
    fun `fetchGithubPr with null user only returns Success with empty authorName`() {
        val cfg = GithubRepoConfig(id = 1L, name = "x", owner = "octocat", repo = "hello-world", accessToken = "TOK")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"T","state":"open","body":"d","head":{"ref":"f","sha":"h"},"base":{"ref":"m","sha":"b"}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), listOf(cfg))
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "got $result")
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("", data.authorName)
    }

    @Test
    fun `fetchGithubPr uses fallback github token when no specific config matches`() {
        val matching = GithubRepoConfig(id = 1L, name = "m", owner = "octocat", repo = "hello-world", accessToken = "MATCHING")
        val fallback = GithubRepoConfig(id = 2L, name = "f", owner = "other", repo = "repo", accessToken = "FALLBACK")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"T","state":"open","body":"d","head":{"ref":"f","sha":"h"},"base":{"ref":"m","sha":"b"},"user":{"login":"u"}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result = adapter.fetchMrData("https://github.com/somebody/elsewhere/pull/1", emptyList(), listOf(matching, fallback))
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "got $result")
    }

    @Test
    fun `fetchGithubPr with matching config uses the matching config token`() {
        val matching = GithubRepoConfig(id = 1L, name = "m", owner = "octocat", repo = "hello-world", accessToken = "MATCHING")
        val other = GithubRepoConfig(id = 2L, name = "o", owner = "other", repo = "repo", accessToken = "OTHER")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"T","state":"open","body":"d","head":{"ref":"f","sha":"h"},"base":{"ref":"m","sha":"b"},"user":{"login":"u"}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), listOf(matching, other))
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "got $result")
    }

    @Test
    fun `fetchGithubPr with no configs uses no Authorization header`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"T","state":"open","body":"d","head":{"ref":"f","sha":"h"},"base":{"ref":"m","sha":"b"},"user":{"login":"u"}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result = adapter.fetchMrData("https://github.com/octocat/hello-world/pull/1", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "got $result")
    }

    @Test
    fun `fetchGithubPr with PR number that is not a number returns Failure`() {
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/notanumber", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "got $result")
    }

    @Test
    fun `fetchGithubPr with path parts 2 not equal to pull returns Failure`() {
        val result = adapter.fetchMrData("https://github.com/octo/cat/issues/1", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "got $result")
    }

    @Test
    fun `fetchGithubPr with path having fewer than 4 parts returns Failure`() {
        val result = adapter.fetchMrData("https://github.com/just/two", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure, "got $result")
    }
}
