package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GitlabMrAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: GitlabMrAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = GitlabMrAdapter()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(): GitlabConfig = GitlabConfig(name = "g", baseUrl = server.url("").toString().removeSuffix("/"), accessToken = "TOK")

    @Test
    fun `fetchProjectDetail returns parsed detail`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"id":42,"name":"repo","path_with_namespace":"g/repo","default_branch":"main","web_url":"https://gl/repo"}""",
                ),
        )
        val detail = adapter.fetchProjectDetail(config(), 42L)
        assertNotNull(detail)
        assertEquals(42L, detail.id)
        assertEquals("repo", detail.name)
        assertEquals("main", detail.defaultBranch)
    }

    @Test
    fun `fetchProjectDetail falls back to main branch when default_branch missing`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"u"}"""),
        )
        val detail = adapter.fetchProjectDetail(config(), 1L)
        assertEquals("main", detail.defaultBranch)
    }

    @Test
    fun `fetchProjectDetail returns empty detail on HTTP error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val detail = adapter.fetchProjectDetail(config(), 1L)
        assertEquals(0L, detail.id)
        assertEquals("", detail.name)
    }

    @Test
    fun `fetchProjectDetail returns empty detail on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val detail = adapter.fetchProjectDetail(config(), 1L)
        assertEquals(0L, detail.id)
    }

    @Test
    fun `createBranch uses POST to branches endpoint`() {
        server.enqueue(MockResponse().setResponseCode(201))
        adapter.createBranch(config(), 1L, "feat", "main")
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path.orEmpty().startsWith("/api/v4/projects/1/repository/branches"))
    }

    @Test
    fun `createBranch sends ref and branch in body`() {
        server.enqueue(MockResponse().setResponseCode(201))
        adapter.createBranch(config(), 1L, "new-branch", "develop")
        val body = server.takeRequest().body.readUtf8()
        assertEquals(true, body.contains("\"branch\":\"new-branch\""))
        assertEquals(true, body.contains("\"ref\":\"develop\""))
    }

    @Test
    fun `commitFiles sends POST to repository commits`() {
        server.enqueue(MockResponse().setResponseCode(201))
        val actions = listOf(mapOf("action" to "create", "file_path" to "x.kt", "content" to "y"))
        adapter.commitFiles(config(), 1L, "feat", "msg", actions)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path.orEmpty().startsWith("/api/v4/projects/1/repository/commits"))
    }

    @Test
    fun `createMergeRequest returns web_url from response`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"web_url":"https://gl/grp/repo/-/merge_requests/99"}"""),
        )
        val url = adapter.createMergeRequest(config(), 1L, "feat", "main", "T", "D")
        assertEquals("https://gl/grp/repo/-/merge_requests/99", url)
    }

    @Test
    fun `createMergeRequest throws on HTTP error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows(RuntimeException::class.java) {
            adapter.createMergeRequest(config(), 1L, "feat", "main", "T", "D")
        }
    }

    @Test
    fun `createMergeRequest returns empty string when web_url missing`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":1}"""),
        )
        val url = adapter.createMergeRequest(config(), 1L, "feat", "main", "T", "D")
        assertEquals("", url)
    }

    @Test
    fun `createMergeRequest truncates description longer than 10_000 chars`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"web_url":"https://gl/grp/repo/-/merge_requests/1"}"""),
        )
        val longDescription = "x".repeat(15_000)
        adapter.createMergeRequest(config(), 1L, "feat", "main", "T", longDescription)
        val body = server.takeRequest().body.readUtf8()
        val expectedTruncated = "x".repeat(10_000)
        assertTrue(body.contains(expectedTruncated), "truncated description should be in body")
    }

    @Test
    fun `commitFiles handles empty actions list`() {
        server.enqueue(MockResponse().setResponseCode(201))
        adapter.commitFiles(config(), 1L, "feat", "msg", emptyList())
        val req = server.takeRequest()
        assertEquals("POST", req.method)
    }

    @Test
    fun `fetchProjectDetail works with baseUrl having trailing slash`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":1,"name":"r","path_with_namespace":"g/r","web_url":"u"}"""),
        )
        val cfg = GitlabConfig(name = "g", baseUrl = server.url("").toString(), accessToken = "TOK")
        val detail = adapter.fetchProjectDetail(cfg, 1L)
        assertEquals(1L, detail.id)
    }
}
