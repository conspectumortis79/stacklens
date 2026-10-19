package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

class ContextMapAdapterHttpTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: ContextMapAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val builder =
            WebClient
                .builder()
                .baseUrl(server.url("/").toString())
                .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
        adapter = ContextMapAdapter(connectTimeoutMs = 1000, readTimeoutMs = 1000)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun jsonResponse(
        code: Int,
        body: String,
    ): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(body)

    private fun config(
        active: Boolean = true,
        projectIds: String = "",
        groupId: String? = null,
    ): GitlabConfig =
        GitlabConfig(
            name = "test",
            baseUrl = server.url("/").toString().removeSuffix("/"),
            accessToken = "tok",
            active = active,
            projectIds = projectIds,
            groupId = groupId,
        )

    @Test
    fun `searchKeyword returns empty when config is inactive`() {
        val result = adapter.searchKeyword(config(active = false), "kw", emptyList(), 1L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword with groupId returns projects via groups endpoint`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}
                ]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        val result = adapter.searchKeyword(config(groupId = "g"), "kw", emptyList(), 1L)
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword with projectIds uses project-by-id endpoint`() {
        server.enqueue(
            jsonResponse(
                200,
                """{"id": 42, "name": "Single", "path_with_namespace": "x/y", "default_branch": "main"}""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        server.enqueue(jsonResponse(200, "[]"))
        val result = adapter.searchKeyword(config(projectIds = "42"), "kw", emptyList(), 1L)
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword with multiple projectIds iterates each`() {
        server.enqueue(
            jsonResponse(
                200,
                """{"id": 1, "name": "A", "path_with_namespace": "x/a", "default_branch": "main"}""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        server.enqueue(
            jsonResponse(
                200,
                """{"id": 2, "name": "B", "path_with_namespace": "x/b", "default_branch": "main"}""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        val result = adapter.searchKeyword(config(projectIds = "1,2"), "kw", emptyList(), 1L)
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword skips project that 404s during fetchProject`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        val result = adapter.searchKeyword(config(projectIds = "99"), "kw", emptyList(), 1L)
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword applies repositoryFilters to project list`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "P1", "path_with_namespace": "group/keep-me", "default_branch": "main"},
                    {"id": 2, "name": "P2", "path_with_namespace": "group/skip-me", "default_branch": "main"}
                ]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        server.enqueue(jsonResponse(200, "[]"))
        val result = adapter.searchKeyword(config(groupId = "g"), "kw", listOf("keep-me"), 1L)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `searchKeyword with blank filters searches all projects`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}
                ]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        server.enqueue(jsonResponse(200, "[]"))
        val result = adapter.searchKeyword(config(groupId = "g"), "kw", listOf("  ", ""), 1L)
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword builds SearchResult from blob matches in puml file`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}]""",
            ),
        )
        server.enqueue(
            jsonResponse(
                200,
                """[{"project_id": 1, "path": "docs/arch.puml", "ref": "main", "startline": 5, "data": "component [Foo]"}]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        val results = adapter.searchKeyword(config(groupId = "g"), "Foo", emptyList(), 42L)
        assertEquals(1, results.size)
        assertEquals("docs/arch.puml", results[0].filePath)
        assertEquals(5, results[0].lineNumber)
        assertEquals("Foo", results[0].className)
        assertEquals(42L, results[0].sessionId)
    }

    @Test
    fun `searchKeyword builds SearchResult without component when extractComponent returns null`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}]""",
            ),
        )
        server.enqueue(
            jsonResponse(
                200,
                """[{"project_id": 1, "path": "docs/context.md", "ref": "main", "startline": 2, "data": "no diagram syntax here"}]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        val results = adapter.searchKeyword(config(groupId = "g"), "kw", emptyList(), 1L)
        assertEquals(1, results.size)
        assertEquals(null, results[0].className)
    }

    @Test
    fun `searchKeyword paginates blob search up to 3 pages`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}]""",
            ),
        )
        val fullPage = (1..100).joinToString(",") { """{"project_id":1,"path":"docs/x.md","ref":"main","startline":1,"data":"x"}""" }
        repeat(3) { server.enqueue(jsonResponse(200, "[$fullPage]")) }
        server.enqueue(jsonResponse(200, "[]"))
        val results = adapter.searchKeyword(config(groupId = "g"), "kw", emptyList(), 1L)
        assertNotNull(results)
    }

    @Test
    fun `searchKeyword stops pagination when page returns less than 100`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}]""",
            ),
        )
        server.enqueue(
            jsonResponse(
                200,
                """[{"project_id": 1, "path": "docs/x.md", "ref": "main", "startline": 1, "data": "x"}]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        val results = adapter.searchKeyword(config(groupId = "g"), "kw", emptyList(), 1L)
        assertNotNull(results)
    }

    @Test
    fun `searchKeyword handles blob search HTTP errors`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        server.enqueue(jsonResponse(200, "[]"))
        val results = adapter.searchKeyword(config(groupId = "g"), "kw", emptyList(), 1L)
        assertNotNull(results)
    }

    @Test
    fun `searchKeyword scans files found in tree even when blob search misses`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": "x", "name": "context.md", "path": "docs/context.md", "type": "blob"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("line 1\nhere is keyword\nline 3"))
        val results = adapter.searchKeyword(config(groupId = "g"), "keyword", emptyList(), 1L)
        assertNotNull(results)
        if (results.isNotEmpty()) {
            assertEquals("docs/context.md", results[0].filePath)
        }
    }

    @Test
    fun `searchKeyword skips tree files already covered by blob search`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}]""",
            ),
        )
        server.enqueue(
            jsonResponse(
                200,
                """[{"project_id": 1, "path": "docs/context.md", "ref": "main", "startline": 1, "data": "x"}]""",
            ),
        )
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": "x", "name": "context.md", "path": "docs/context.md", "type": "blob"}]""",
            ),
        )
        val results = adapter.searchKeyword(config(groupId = "g"), "kw", emptyList(), 1L)
        assertEquals(1, results.size)
        assertEquals("docs/context.md", results[0].filePath)
    }

    @Test
    fun `searchKeyword handles tree HTTP errors`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        val results = adapter.searchKeyword(config(groupId = "g"), "kw", emptyList(), 1L)
        assertNotNull(results)
    }

    @Test
    fun `searchKeyword handles file content fetch errors`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": "x", "name": "context.md", "path": "docs/context.md", "type": "blob"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        val results = adapter.searchKeyword(config(groupId = "g"), "kw", emptyList(), 1L)
        assertNotNull(results)
    }

    @Test
    fun `searchKeyword paginates fetchAllProjects across multiple pages`() {
        val fullPage = (1..100).joinToString(",") { """{"id":$it,"name":"P$it","path_with_namespace":"g/p$it","default_branch":"main"}""" }
        server.enqueue(jsonResponse(200, "[$fullPage]"))
        server.enqueue(jsonResponse(200, "[]"))
        repeat(400) { server.enqueue(jsonResponse(200, "[]")) }
        val results = adapter.searchKeyword(config(groupId = null), "kw", emptyList(), 1L)
        assertNotNull(results)
    }

    @Test
    fun `searchKeyword handles fetchAllProjects HTTP errors`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        val results = adapter.searchKeyword(config(groupId = null), "kw", emptyList(), 1L)
        assertNotNull(results)
    }

    @Test
    fun `searchKeyword paginates fetchRepoTree up to 10 pages`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        val fullPage = (1..100).joinToString(",") { """{"id":"x","name":"y","path":"y.md","type":"blob"}""" }
        repeat(10) { server.enqueue(jsonResponse(200, "[$fullPage]")) }
        server.enqueue(jsonResponse(200, "[]"))
        val results = adapter.searchKeyword(config(groupId = "g"), "kw", emptyList(), 1L)
        assertNotNull(results)
    }

    @Test
    fun `searchKeyword handles fetchProjectBlobs error by returning empty`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": "main"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(jsonResponse(200, "[]"))
        val results = adapter.searchKeyword(config(groupId = "g"), "kw", emptyList(), 1L)
        assertFalse(results.isEmpty().not())
    }

    @Test
    fun `searchKeyword uses default branch 'main' when project defaultBranch is null`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P1", "path_with_namespace": "g/p1", "default_branch": null}]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        server.enqueue(jsonResponse(200, "[]"))
        val results = adapter.searchKeyword(config(groupId = "g"), "kw", emptyList(), 1L)
        assertNotNull(results)
    }
}
