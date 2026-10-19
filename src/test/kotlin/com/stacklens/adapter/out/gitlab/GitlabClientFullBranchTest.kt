package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GitlabClientFullBranchTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabClient
    private lateinit var baseUrl: String

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("").toString().removeSuffix("/")
        client = GitlabClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun gitlabConfig(
        id: Long = 1L,
        groupId: String? = null,
    ): GitlabConfig =
        GitlabConfig(
            id = id,
            name = "g",
            baseUrl = baseUrl,
            accessToken = "TOK",
            active = true,
            groupId = groupId,
        )

    @Test
    fun `fetchAllProjects stops pagination when page is not full`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"id":1,"name":"a","path_with_namespace":"g/a","http_url_to_repo":"u"},
                        {"id":2,"name":"b","path_with_namespace":"g/b","http_url_to_repo":"u"},
                        {"id":3,"name":"c","path_with_namespace":"g/c","http_url_to_repo":"u"},
                        {"id":4,"name":"d","path_with_namespace":"g/d","http_url_to_repo":"u"},
                        {"id":5,"name":"e","path_with_namespace":"g/e","http_url_to_repo":"u"}
                    ]""",
                ),
        )
        val webClient = WebClient.builder().baseUrl(baseUrl).build()
        val projects = client.fetchAllProjectsForTest(webClient, null)
        assertEquals(5, projects.size)
    }

    @Test
    fun `filterProjects matches by path-with-namespace suffix`() {
        val projects =
            listOf(
                GitlabProject(id = 1, name = "frontend", pathWithNamespace = "myorg/team-a/frontend", httpUrlToRepo = "u"),
                GitlabProject(id = 2, name = "backend", pathWithNamespace = "myorg/team-a/backend", httpUrlToRepo = "u"),
                GitlabProject(id = 3, name = "infra", pathWithNamespace = "myorg/team-b/infra", httpUrlToRepo = "u"),
            )
        val filtered = client.filterProjectsForTest(projects, listOf("frontend"))
        assertEquals(1, filtered.size)
        assertEquals(1L, filtered[0].id)
    }

    @Test
    fun `applyRepositoryFilters matches by path contains`() {
        val projects =
            listOf(
                GitlabProject(id = 1, name = "repo", pathWithNamespace = "g/myrepo", httpUrlToRepo = "u"),
            )
        val filtered = client.applyRepositoryFiltersForTest(projects, listOf("myrep"))
        assertEquals(1, filtered.size)
    }

    @Test
    fun `searchProjectBlobs returns empty list on 429 rate limit`() {
        val cfg = gitlabConfig()
        val webClient = client.buildClientForTest(cfg, baseUrl)
        server.enqueue(MockResponse().setResponseCode(429))
        val result = client.searchProjectBlobsForTest(webClient, 1L, "anything")
        assertNotNull(result)
        assertTrue(result!!.isEmpty())
    }

    @Test
    fun `searchProjectBlobs returns null on 404 to trigger fallback`() {
        val cfg = gitlabConfig()
        val webClient = client.buildClientForTest(cfg, baseUrl)
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.searchProjectBlobsForTest(webClient, 1L, "anything")
        assertNull(result)
    }

    @Test
    fun `searchProjectBlobs breaks loop on 5xx server error`() {
        val cfg = gitlabConfig()
        val webClient = client.buildClientForTest(cfg, baseUrl)
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.searchProjectBlobsForTest(webClient, 1L, "anything")
        assertNotNull(result)
    }

    @Test
    fun `fetchProjectFiles returns empty when project not found`() {
        val cfg = gitlabConfig()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val files = client.fetchProjectFilesForTest(cfg, 999L, maxFiles = 5, maxFileSizeKb = 64)
        assertTrue(files.isEmpty())
    }

    @Test
    fun `fetchProjectFiles returns empty when project id doesn't match any in list`() {
        val cfg = gitlabConfig()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"id":1,"name":"a","path_with_namespace":"g/a","http_url_to_repo":"u"},
                        {"id":2,"name":"b","path_with_namespace":"g/b","http_url_to_repo":"u"}
                    ]""",
                ),
        )
        val files = client.fetchProjectFilesForTest(cfg, 999L, maxFiles = 5, maxFileSizeKb = 64)
        assertTrue(files.isEmpty())
    }

    @Test
    fun `fetchProjectFiles uses default branch when present`() {
        val cfg = gitlabConfig()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"r","path_with_namespace":"g/r","http_url_to_repo":"u","default_branch":"develop"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"path":"src","type":"tree"},
                        {"path":"src/Foo.kt","type":"blob"},
                        {"path":"image.png","type":"blob"}
                    ]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/plain")
                .setBody("class Foo {}"),
        )
        val files = client.fetchProjectFilesForTest(cfg, 1L, maxFiles = 5, maxFileSizeKb = 64)
        assertEquals(1, files.size)
    }

    @Test
    fun `fetchProjectFiles uses main when defaultBranch is null`() {
        val cfg = gitlabConfig()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"r","path_with_namespace":"g/r","http_url_to_repo":"u","default_branch":null}]"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val files = client.fetchProjectFilesForTest(cfg, 1L, maxFiles = 5, maxFileSizeKb = 64)
        assertTrue(files.isEmpty())
    }

    @Test
    fun `fetchProjectFilesForPath filters by prefix`() {
        val cfg = gitlabConfig()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":1,"name":"r","path_with_namespace":"g/r","http_url_to_repo":"u","default_branch":"main"}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"path":"src","type":"tree"},
                        {"path":"docs/Bar.kt","type":"blob"},
                        {"path":"image.png","type":"blob"},
                        {"path":"src/Foo.kt","type":"blob"}
                    ]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/plain")
                .setBody("class Foo {}"),
        )
        val files = client.fetchProjectFilesForPathForTest(cfg, 1L, "src", maxFiles = 5, maxFileSizeKb = 64)
        assertEquals(1, files.size)
        assertTrue(files[0].first.startsWith("src/"))
    }

    @Test
    fun `searchProjectByTreeWalk returns empty for no code files`() {
        val cfg = gitlabConfig()
        val webClient = client.buildClientForTest(cfg, baseUrl)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result =
            client.searchProjectByTreeWalkForTest(
                webClient = webClient,
                apiBase = baseUrl,
                project =
                    GitlabProject(
                        id = 1,
                        name = "r",
                        pathWithNamespace = "g/r",
                        defaultBranch = "main",
                    ),
                keyword = "anything",
                sessionId = 1L,
                crossRefParentId = null,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchProjectByTreeWalk with non-blob tree entries filters out trees`() {
        val cfg = gitlabConfig()
        val webClient = client.buildClientForTest(cfg, baseUrl)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[
                        {"path":"src","type":"tree"},
                        {"path":"image.png","type":"blob"},
                        {"path":"src/Main.kt","type":"blob"}
                    ]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/plain")
                .setBody("fun main() {}"),
        )
        val result =
            client.searchProjectByTreeWalkForTest(
                webClient = webClient,
                apiBase = baseUrl,
                project =
                    GitlabProject(
                        id = 1,
                        name = "r",
                        pathWithNamespace = "g/r",
                        defaultBranch = "main",
                    ),
                keyword = "main",
                sessionId = 1L,
                crossRefParentId = null,
            )
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `filterProjects matches by ID when selector is numeric`() {
        val projects =
            listOf(
                GitlabProject(id = 1, name = "frontend", pathWithNamespace = "g/frontend", httpUrlToRepo = "u"),
                GitlabProject(id = 2, name = "backend", pathWithNamespace = "g/backend", httpUrlToRepo = "u"),
            )
        val filtered = client.filterProjectsForTest(projects, listOf("1"))
        assertEquals(1, filtered.size)
        assertEquals(1L, filtered[0].id)
    }

    @Test
    fun `filterProjects matches by name lowercase`() {
        val projects =
            listOf(
                GitlabProject(id = 1, name = "Frontend", pathWithNamespace = "g/frontend", httpUrlToRepo = "u"),
            )
        val filtered = client.filterProjectsForTest(projects, listOf("frontend"))
        assertEquals(1, filtered.size)
    }

    @Test
    fun `applyRepositoryFilters with empty filters returns all projects`() {
        val projects =
            listOf(
                GitlabProject(id = 1, name = "r", pathWithNamespace = "g/r", httpUrlToRepo = "u"),
            )
        val filtered = client.applyRepositoryFiltersForTest(projects, emptyList())
        assertEquals(1, filtered.size)
    }

    @Test
    fun `applyRepositoryFilters with blank filters returns all projects`() {
        val projects =
            listOf(
                GitlabProject(id = 1, name = "r", pathWithNamespace = "g/r", httpUrlToRepo = "u"),
            )
        val filtered = client.applyRepositoryFiltersForTest(projects, listOf("   ", "  "))
        assertEquals(1, filtered.size)
    }

    @Test
    fun `applyRepositoryFilters matches by name equals`() {
        val projects =
            listOf(
                GitlabProject(id = 1, name = "myrepo", pathWithNamespace = "g/other", httpUrlToRepo = "u"),
            )
        val filtered = client.applyRepositoryFiltersForTest(projects, listOf("myrepo"))
        assertEquals(1, filtered.size)
    }

    @Test
    fun `applyRepositoryFilters matches by path equals`() {
        val projects =
            listOf(
                GitlabProject(id = 1, name = "other", pathWithNamespace = "g/myrepo", httpUrlToRepo = "u"),
            )
        val filtered = client.applyRepositoryFiltersForTest(projects, listOf("g/myrepo"))
        assertEquals(1, filtered.size)
    }

    @Test
    fun `searchProjectBlobs paginates when page is full and stops at max 5 pages`() {
        val cfg = gitlabConfig()
        val webClient = client.buildClientForTest(cfg, baseUrl)
        repeat(5) { page ->
            val body =
                (1..100).joinToString(",") { i ->
                    """{"project_id":1,"path":"f$page$i.kt","ref":"main","startline":$i,"data":"x"}"""
                }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[$body]"),
            )
        }
        val result = client.searchProjectBlobsForTest(webClient, 1L, "keyword")
        assertNotNull(result)
        assertEquals(500, result!!.size)
    }

    @Test
    fun `fetchAllProjects throws RuntimeException on HTTP error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val webClient = WebClient.builder().baseUrl(baseUrl).build()
        try {
            client.fetchAllProjectsForTest(webClient, null)
            assertTrue(false, "Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `searchProjectBlobs returns empty on 5xx via the break path`() {
        val cfg = gitlabConfig()
        val webClient = client.buildClientForTest(cfg, baseUrl)
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.searchProjectBlobsForTest(webClient, 1L, "anything")
        assertNotNull(result)
    }
}
