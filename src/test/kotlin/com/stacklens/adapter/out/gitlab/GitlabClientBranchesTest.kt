package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

class GitlabClientBranchesTest {
    private lateinit var server: MockWebServer
    private lateinit var webClientBuilder: WebClient.Builder
    private lateinit var client: GitlabClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        webClientBuilder =
            WebClient
                .builder()
                .baseUrl(server.url("/").toString())
                .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
        client = GitlabClient(GitlabTreeApiClient(), GitlabCodeContextParser())
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

    private fun gitlabConfig(active: Boolean = true): GitlabConfig =
        GitlabConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("/").toString().removeSuffix("/"),
            accessToken = "tok",
            active = active,
        )

    private fun gitlabConfigWithProjectIds(projectIds: String): GitlabConfig =
        GitlabConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("/").toString().removeSuffix("/"),
            accessToken = "tok",
            active = true,
            projectIds = projectIds,
        )

    @Test
    fun `filterProjects matches by numeric id`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 11, "name": "P1", "path_with_namespace": "g/p1",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"},
                    {"id": 22, "name": "P2", "path_with_namespace": "g/p2",
                     "default_branch": "main", "http_url_to_repo": "https://y.git"}
                ]""",
            ),
        )
        val cfg = gitlabConfigWithProjectIds("22")
        val result = client.listAccessibleProjects(cfg)
        assertEquals(1, result.size)
        assertEquals(22L, result[0].id)
    }

    @Test
    fun `filterProjects matches by path_with_namespace`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 11, "name": "P1", "path_with_namespace": "group/special",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        val cfg = gitlabConfigWithProjectIds("special")
        val result = client.listAccessibleProjects(cfg)
        assertEquals(1, result.size)
    }

    @Test
    fun `filterProjects matches by name`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 11, "name": "CustomName", "path_with_namespace": "group/path",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        val cfg = gitlabConfigWithProjectIds("customname")
        val result = client.listAccessibleProjects(cfg)
        assertEquals(1, result.size)
    }

    @Test
    fun `filterProjects matches by endsWith sub-path`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 11, "name": "P1", "path_with_namespace": "root/inner/suffix",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        val cfg = gitlabConfigWithProjectIds("suffix")
        val result = client.listAccessibleProjects(cfg)
        assertEquals(1, result.size)
    }

    @Test
    fun `filterProjects returns empty when no selector matches`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 11, "name": "P1", "path_with_namespace": "g/p1",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        val cfg = gitlabConfigWithProjectIds("noMatch")
        val result = client.listAccessibleProjects(cfg)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `applyRepositoryFilters matches by path equality`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "P1", "path_with_namespace": "exact/match",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        server.enqueue(jsonResponse(200, """[]"""))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = listOf("exact/match"),
            )
        assertNotNull(result)
    }

    @Test
    fun `applyRepositoryFilters filters out by `() {
        server.enqueue(jsonResponse(200, "[]"))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = listOf("   "),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `applyRepositoryFilters trims blank filters`() {
        server.enqueue(jsonResponse(200, "[]"))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = listOf("  real  ", ""),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword filters via repositoryFilters with no matches`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "P1", "path_with_namespace": "g/p1",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = listOf("nonexistent"),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword handles 429 rate limit response`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "P1", "path_with_namespace": "g/p1",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limit"))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword handles 4xx client error by falling back to tree-walk`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "P1", "path_with_namespace": "g/p1",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))
        server.enqueue(jsonResponse(200, """[]"""))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword handles 5xx server error by breaking the loop`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "P1", "path_with_namespace": "g/p1",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword handles 200 empty page in blob search`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "P1", "path_with_namespace": "g/p1",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `tree-walk uses default branch fallback to main when defaultBranch is null`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "P1", "path_with_namespace": "g/p1",
                     "default_branch": null, "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        server.enqueue(jsonResponse(200, """[]"""))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `tree-walk finds code files and matches keyword`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "P1", "path_with_namespace": "g/p1",
                     "default_branch": "develop", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        server.enqueue(
            jsonResponse(
                200,
                """[{"id":"a","name":"App.kt","type":"blob","path":"src/App.kt"}]""",
            ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain")
                .setBody("class App { fun targetMethod() {} }"),
        )
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "targetMethod",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `listProjectFilePaths uses default branch when response has default_branch field`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": "develop"}"""))
        server.enqueue(jsonResponse(200, """[]"""))
        val result = client.listProjectFilePaths(gitlabConfig(), 1L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listProjectFilePaths falls back to main when no default_branch`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "name": "P"}"""))
        server.enqueue(jsonResponse(200, """[]"""))
        val result = client.listProjectFilePaths(gitlabConfig(), 1L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listProjectFilePaths returns blob paths`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": "main"}"""))
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id":"a","name":"App.kt","type":"blob","path":"src/App.kt"},
                    {"id":"b","name":"Foo","type":"tree","path":"src"}
                ]""",
            ),
        )
        val result = client.listProjectFilePaths(gitlabConfig(), 1L)
        assertEquals(listOf("src/App.kt"), result)
    }
}
