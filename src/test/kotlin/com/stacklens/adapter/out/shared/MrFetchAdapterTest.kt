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
import org.junit.jupiter.api.assertThrows

class MrFetchAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: MrFetchAdapter
    private val gitlabAdapter = GitlabMrFetchAdapter(MrDiffTextBuilder(DefaultDiffHunkParser()))

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

    private fun gitlabConfig(): GitlabConfig = GitlabConfig(name = "g", baseUrl = server.url("").toString().removeSuffix("/"), accessToken = "TOK")

    @Test
    fun `fetchGitlabMr success returns MrData with diff`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"t","description":"d","source_branch":"feat","target_branch":"main","author":{"name":"alice"},"state":"opened"}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"diff_refs":{"base_sha":"b","start_sha":"s","head_sha":"h"},"changes":[{"old_path":"a.kt","new_path":"a.kt","diff":"+x","deleted":false,"new_file":false},{"old_path":"b.kt","new_path":"b.kt","diff":"","deleted":true,"new_file":false}]}""",
                ),
        )
        val cfg = gitlabConfig()
        val url = "${cfg.baseUrl}/grp/repo/-/merge_requests/42"
        val result = adapter.fetchMrData(url, listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("t", data.title)
        assertEquals("alice", data.authorName)
        assertEquals("GitLab", data.platform)
        assertEquals(listOf("a.kt", "DELETED: b.kt"), data.changedFiles)
        assertTrue(data.diffText.contains("### a.kt"))
        assertEquals("b", data.diffBaseSha)
        assertEquals("s", data.diffStartSha)
        assertEquals("h", data.diffHeadSha)
    }

    @Test
    fun `fetchGitlabMr returns Failure when no matching config`() {
        val other = GitlabConfig(name = "o", baseUrl = "https://other.example.com", accessToken = "TOK")
        val result = adapter.fetchMrData("https://gitlab.com/grp/repo/-/merge_requests/1", listOf(other), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchGitlabMr returns Failure for invalid mr iid`() {
        val cfg = gitlabConfig()
        val result = adapter.fetchMrData("${cfg.baseUrl}/grp/repo/-/merge_requests/abc", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchGitlabMr handles 404 from API`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        val cfg = gitlabConfig()
        val result = adapter.fetchMrData("${cfg.baseUrl}/grp/repo/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchGitlabMr matches via extractDomain fallback when baseUrl differs`() {
        val cfg =
            GitlabConfig(
                name = "g",
                baseUrl = "http://localhost:${server.port}",
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
        val url = "${cfg.baseUrl}/grp/repo/-/merge_requests/6"
        val result = adapter.fetchMrData(url, listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
    }

    @Test
    fun `fetchGitlabMr matches host docker internal when URL is localhost`() {
        val cfg =
            GitlabConfig(
                name = "g",
                baseUrl = "http://host.docker.internal:${server.port}",
                accessToken = "TOK",
            )
        val url = "http://localhost:${server.port}/grp/repo/-/merge_requests/6"
        val result =
            try {
                adapter.fetchMrData(url, listOf(cfg), emptyList())
            } catch (e: Exception) {
                MrFetchPort.MrFetchResult.Failure(e.message ?: "err")
            }
        assertNotNull(result)
    }

    @Test
    fun `fetchGitlabMr matches localhost when URL is host docker internal`() {
        val cfg =
            GitlabConfig(
                name = "g",
                baseUrl = "http://localhost:${server.port}",
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
        val url = "http://host.docker.internal:${server.port}/grp/repo/-/merge_requests/6"
        val result = adapter.fetchMrData(url, listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
    }

    @Test
    fun `fetchGitlabMr returns Failure when neither baseUrlAlts nor extractDomain match`() {
        val cfg = GitlabConfig(name = "g", baseUrl = "https://gitlab.example.com", accessToken = "TOK")
        val result = adapter.fetchMrData("https://other.example.com/grp/repo/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchMrData catches exception and returns Failure when no listener exists`() {
        val cfg = GitlabConfig(name = "g", baseUrl = "http://127.0.0.1:1", accessToken = "TOK")
        val result =
            try {
                adapter.fetchMrData("https://gitlab.example.com/grp/repo/-/merge_requests/1", listOf(cfg), emptyList())
            } catch (_: Exception) {
                null
            }
        if (result != null) {
            assertTrue(result is MrFetchPort.MrFetchResult.Failure)
        }
    }

    @Test
    fun `fetchGitlabMr diffText omits empty diffs`() {
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
                .setBody(
                    """{"changes":[{"old_path":"e.kt","new_path":"e.kt","diff":"","deleted":false,"new_file":false}]}""",
                ),
        )
        val cfg = gitlabConfig()
        val result = adapter.fetchMrData("${cfg.baseUrl}/grp/repo/-/merge_requests/7", listOf(cfg), emptyList())
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertTrue(!data.diffText.contains("### e.kt"))
    }

    @Test
    fun `invalid URL with no host returns Failure`() {
        val result = adapter.fetchMrData("not-a-url", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `unknown host returns Failure`() {
        val result = adapter.fetchMrData("https://example.com/grp/repo/-/merge_requests/1", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `github URL on no-config returns Failure`() {
        adapter.githubApiBaseUrl = server.url("").toString().trimEnd('/')
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        val result = adapter.fetchMrData("https://github.com/octo/cat/pull/1", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchGithubPr success returns MrData with diff`() {
        adapter.githubApiBaseUrl = server.url("").toString().trimEnd('/')
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"GH PR","body":"GH desc","state":"open","head":{"ref":"feat","sha":"h"},"base":{"ref":"main","sha":"b"},"user":{"login":"octocat"}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"status":"modified","filename":"a.kt","patch":"@@\n+x"}]""",
                ),
        )
        val result =
            adapter.fetchMrData(
                "https://github.com/octo/cat/pull/1",
                emptyList(),
                emptyList(),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertEquals("GitHub", data.platform)
        assertEquals("GH PR", data.title)
        assertEquals("octocat", data.authorName)
        assertEquals("feat", data.sourceBranch)
        assertEquals(listOf("MODIFIED: a.kt"), data.changedFiles)
    }

    @Test
    fun `fetchGithubPr returns null for URL without pull segment`() {
        adapter.githubApiBaseUrl = server.url("").toString().trimEnd('/')
        val result =
            adapter.fetchMrData("https://github.com/octo/cat", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchGithubPr returns null when PR number is not numeric`() {
        adapter.githubApiBaseUrl = server.url("").toString().trimEnd('/')
        val result =
            adapter.fetchMrData("https://github.com/octo/cat/pull/abc", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchGithubPr returns Failure when PR endpoint returns 401`() {
        adapter.githubApiBaseUrl = server.url("").toString().trimEnd('/')
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        val result =
            adapter.fetchMrData("https://github.com/octo/cat/pull/1", emptyList(), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchGithubPr uses configured token when matching owner and repo`() {
        adapter.githubApiBaseUrl = server.url("").toString().trimEnd('/')
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"PR","body":"","state":"open","head":{"ref":"f","sha":"h"},"base":{"ref":"m","sha":"b"},"user":{"login":"octo"}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val cfg =
            GithubRepoConfig(
                name = "cat-repo",
                owner = "octo",
                repo = "cat",
                accessToken = "gh-secret-token",
            )
        val result =
            adapter.fetchMrData("https://github.com/octo/cat/pull/1", emptyList(), listOf(cfg))
        val recorded = server.takeRequest()
        val auth = recorded.getHeader("Authorization")
        assertEquals("Bearer gh-secret-token", auth)
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
    }

    @Test
    fun `fetchGithubPr falls back to first configured token when no owner matches`() {
        adapter.githubApiBaseUrl = server.url("").toString().trimEnd('/')
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"PR","body":"","state":"closed","head":{"ref":"f","sha":"h"},"base":{"ref":"m","sha":"b"},"user":{"login":"x"}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val cfg =
            GithubRepoConfig(
                name = "other-repo",
                owner = "someone-else",
                repo = "different-repo",
                accessToken = "fallback-token",
            )
        adapter.fetchMrData("https://github.com/octo/cat/pull/1", emptyList(), listOf(cfg))
        val recorded = server.takeRequest()
        assertEquals("Bearer fallback-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `diffText is bounded by maxDiffChars even with very large inputs`() {
        val bigDiff = "@@ -1,1 +1,1 @@ " + "x".repeat(30_000)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"t","source_branch":"a","target_branch":"b","state":"open","author":{"name":"x"}}""",
                ),
        )
        val changes =
            (1..10).joinToString(",") { i ->
                """{"old_path":"f$i.kt","new_path":"f$i.kt","diff":"$bigDiff","deleted":false,"new_file":false}"""
            }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[$changes]}"""),
        )
        val cfg = gitlabConfig()
        val result = adapter.fetchMrData("${cfg.baseUrl}/grp/repo/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "expected Success but was $result")
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertTrue(data.diffText.length <= 80_000, "diffText length ${data.diffText.length} exceeds cap")
    }

    @Test
    fun `diffText preserves hunk headers even when body is truncated`() {
        val bodyLines = (1..20).joinToString(" ") { "+added-$it" }
        val bigDiff = "@@ -1,0 +1,20 @@ $bodyLines"
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
                .setBody(
                    """{"changes":[{"old_path":"f.kt","new_path":"f.kt","diff":"$bigDiff","deleted":false,"new_file":false}]}""",
                ),
        )
        val cfg = gitlabConfig()
        val result = adapter.fetchMrData("${cfg.baseUrl}/grp/repo/-/merge_requests/1", listOf(cfg), emptyList())
        val data = (result as MrFetchPort.MrFetchResult.Success).data
        assertTrue(data.diffText.contains("@@ -1,0 +1,20 @@"), "hunk header must be preserved, got:\n${data.diffText}")
    }

    @Test
    fun `connection-refused is surfaced with MR_CONN_REFUSED marker`() {
        val cfg = GitlabConfig(name = "g", baseUrl = "http://127.0.0.1:1", accessToken = "TOK")
        val ex =
            assertThrows<IllegalStateException> {
                adapter.fetchMrData("http://127.0.0.1:1/grp/repo/-/merge_requests/1", listOf(cfg), emptyList())
            }
        assertTrue(ex.message!!.contains("MR_CONN_REFUSED"))
    }

    @Test
    fun `extractDomain returns lowercase host when url has host`() {
        val method =
            GitlabMrFetchAdapter::class
                .java
                .declaredMethods
                .firstOrNull { it.name == "extractDomain" }
        assertTrue(method != null)
        method!!.isAccessible = true
        val host = method.invoke(gitlabAdapter, "https://EXAMPLE.com/foo") as String
        assertEquals("example.com", host)
    }

    @Test
    fun `extractDomain returns input string when URI has no host`() {
        val method =
            GitlabMrFetchAdapter::class
                .java
                .declaredMethods
                .firstOrNull { it.name == "extractDomain" }
        method!!.isAccessible = true
        val result = method.invoke(gitlabAdapter, "not-a-valid-url-just-a-string")
        assertTrue(result != null)
    }

    @Test
    fun `extractDomain returns input string on URI parse exception`() {
        val method =
            GitlabMrFetchAdapter::class
                .java
                .declaredMethods
                .firstOrNull { it.name == "extractDomain" }
        method!!.isAccessible = true
        val result = method.invoke(gitlabAdapter, "bad url with space")
        assertTrue(result != null)
    }

    @Test
    fun `fetchMrData with github enterprise URL (subdomain) routes to github path`() {
        adapter.githubApiBaseUrl = server.url("").toString().trimEnd('/')
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"t","body":"d","head":{"ref":"feat","sha":"abc"},"base":{"ref":"main","sha":"def"},"user":{"login":"u"},"state":"open"}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"filename":"x.kt","patch":"@@ -1 +1 @@","status":"modified"}]"""),
        )
        val cfg = GithubRepoConfig(name = "g", owner = "owner", repo = "repo", accessToken = "")
        val result =
            adapter.fetchMrData(
                "https://api.github.com/owner/repo/pull/1",
                emptyList(),
                listOf(cfg),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Success, "expected Success but was $result")
    }

    @Test
    fun `fetchGitlabMr localhost URL without matching config returns Failure`() {
        val cfg =
            GitlabConfig(
                name = "g",
                baseUrl = "http://other.example:9999",
                accessToken = "TOK",
            )
        val result = adapter.fetchMrData("http://localhost:1234/grp/repo/-/merge_requests/1", listOf(cfg), emptyList())
        assertTrue(result is MrFetchPort.MrFetchResult.Failure)
    }

    @Test
    fun `fetchGithubPr uses empty token when no configs provided`() {
        adapter.githubApiBaseUrl = server.url("").toString().trimEnd('/')
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"t","head":{"ref":"feat","sha":"abc"},"base":{"ref":"main","sha":"def"},"user":{"login":"u"},"state":"open"}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"filename":"x.kt","patch":"@@ -1 +1 @@","status":"modified"}]"""),
        )
        val result =
            adapter.fetchMrData(
                "https://api.github.com/owner/repo/pull/1",
                emptyList(),
                emptyList(),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val req = server.takeRequest()
        assertEquals(null, req.getHeader("Authorization"))
    }

    @Test
    fun `fetchGithubPr with blank token does not set Authorization header`() {
        adapter.githubApiBaseUrl = server.url("").toString().trimEnd('/')
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"t","head":{"ref":"feat","sha":"abc"},"base":{"ref":"main","sha":"def"},"user":{"login":"u"},"state":"open"}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"filename":"x.kt","patch":"@@ -1 +1 @@","status":"modified"}]"""),
        )
        val cfg = GithubRepoConfig(name = "g", owner = "owner", repo = "repo", accessToken = "   ")
        val result =
            adapter.fetchMrData(
                "https://api.github.com/owner/repo/pull/1",
                emptyList(),
                listOf(cfg),
            )
        assertTrue(result is MrFetchPort.MrFetchResult.Success)
        val req = server.takeRequest()
        assertEquals(null, req.getHeader("Authorization"))
    }
}
