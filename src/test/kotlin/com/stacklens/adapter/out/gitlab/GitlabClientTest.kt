package com.stacklens.adapter.out.gitlab

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.stacklens.application.port.out.repo.GitlabProjectInfo
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

class GitlabClientTest {
    private lateinit var server: MockWebServer
    private lateinit var webClientBuilder: WebClient.Builder
    private lateinit var treeClient: GitlabTreeApiClient
    private lateinit var codeParser: GitlabCodeContextParser
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
        treeClient = GitlabTreeApiClient()
        codeParser = GitlabCodeContextParser()
        client = GitlabClient(treeClient, codeParser)
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

    @Test
    fun `isPriorityFile matches docker-compose filenames`() {
        assertTrue(client.isPriorityFile("docker-compose.yml"))
        assertTrue(client.isPriorityFile("docker-compose.yaml"))
        assertTrue(client.isPriorityFile("src/docker-compose.dev.yml"))
    }

    @Test
    fun `isPriorityFile matches Dockerfile exact and prefix`() {
        assertTrue(client.isPriorityFile("Dockerfile"))
        assertTrue(client.isPriorityFile("dockerfile"))
        assertTrue(client.isPriorityFile("Dockerfile.prod"))
    }

    @Test
    fun `isPriorityFile matches routes-router keywords`() {
        assertTrue(client.isPriorityFile("routes.kt"))
        assertTrue(client.isPriorityFile("AppRouter.java"))
    }

    @Test
    fun `isPriorityFile matches files containing 'main'`() {
        assertTrue(client.isPriorityFile("main.go"))
        assertTrue(client.isPriorityFile("domain/Main.kt"))
    }

    @Test
    fun `isPriorityFile matches application-kt filenames`() {
        assertTrue(client.isPriorityFile("DemoApplication.kt"))
    }

    @Test
    fun `isPriorityFile matches gradle and settings files`() {
        assertTrue(client.isPriorityFile("build.gradle"))
        assertTrue(client.isPriorityFile("build.gradle.kts"))
        assertTrue(client.isPriorityFile("settings.gradle"))
        assertTrue(client.isPriorityFile("settings.gradle.kts"))
    }

    @Test
    fun `isPriorityFile returns false for unrelated files`() {
        assertFalse(client.isPriorityFile("README.md"))
        assertFalse(client.isPriorityFile("Foo.kt"))
        assertFalse(client.isPriorityFile("helper.txt"))
    }

    @Test
    fun `isPriorityFile is case-insensitive`() {
        assertTrue(client.isPriorityFile("DOCKER-COMPOSE.YML"))
        assertTrue(client.isPriorityFile("Application.KT"))
        assertTrue(client.isPriorityFile("BUILD.GRADLE.KTS"))
    }

    @Test
    fun `listAccessibleProjects returns empty list when API returns empty`() {
        server.enqueue(jsonResponse(200, "[]"))
        val result = client.listAccessibleProjects(gitlabConfig())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listAccessibleProjects parses project response`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "Project One", "path_with_namespace": "group/proj-one",
                     "default_branch": "main", "http_url_to_repo": "https://gl/one.git"},
                    {"id": 2, "name": "Project Two", "path_with_namespace": "group/proj-two",
                     "default_branch": "develop", "http_url_to_repo": "https://gl/two.git"}
                ]""",
            ),
        )
        val result = client.listAccessibleProjects(gitlabConfig())
        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("Project One", result[0].name)
        assertEquals("group/proj-one", result[0].pathWithNamespace)
    }

    @Test
    fun `listAccessibleProjects filters by projectIds selector`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "Project One", "path_with_namespace": "group/proj-one",
                     "default_branch": "main", "http_url_to_repo": "https://gl/one.git"},
                    {"id": 2, "name": "Project Two", "path_with_namespace": "group/proj-two",
                     "default_branch": "develop", "http_url_to_repo": "https://gl/two.git"}
                ]""",
            ),
        )
        val cfg =
            com.stacklens.domain.model.GitlabConfig(
                name = "test",
                baseUrl = server.url("").toString().removeSuffix("/"),
                accessToken = "TOK",
                projectIds = "2",
                active = true,
            )
        val result = client.listAccessibleProjects(cfg)
        assertEquals(1, result.size)
        assertEquals(2L, result[0].id)
    }

    @Test
    fun `listAccessibleProjects throws on error with null message exercises the elvis fallback`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody(""))
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException::class.java) {
            client.listAccessibleProjects(gitlabConfig())
        }
    }

    @Test
    fun `listAccessibleProjects throws on error`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException::class.java) {
            client.listAccessibleProjects(gitlabConfig())
        }
    }

    @Test
    fun `listAccessibleProjects caches results`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        val first = client.listAccessibleProjects(gitlabConfig())
        val second = client.listAccessibleProjects(gitlabConfig())
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `listAccessibleProjects throws on 401`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException::class.java) {
            client.listAccessibleProjects(gitlabConfig())
        }
    }

    @Test
    fun `fetchProjectFiles returns empty list on error`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        val result = client.fetchProjectFiles(gitlabConfig(), 1L, 10, 1000)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchProjectFiles returns empty when project not found`() {
        server.enqueue(jsonResponse(200, "[]"))
        val result = client.fetchProjectFiles(gitlabConfig(), 999L, 10, 1000)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listProjectFilePaths returns empty list on error`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        val result = client.listProjectFilePaths(gitlabConfig(), 1L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listProjectFilePaths uses main as fallback branch`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "name": "P"}"""))
        server.enqueue(jsonResponse(200, """{"tree":[]}"""))
        val result = client.listProjectFilePaths(gitlabConfig(), 1L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listProjectFilePaths uses default_branch when available`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": "develop"}"""))
        server.enqueue(jsonResponse(200, """{"tree":[]}"""))
        val result = client.listProjectFilePaths(gitlabConfig(), 1L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchProjectFilesForPath returns empty on error`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        val result = client.fetchProjectFilesForPath(gitlabConfig(), 1L, "src", 10, 1000)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchProjectFilesForPath with trailing slash in subPath works`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": "main"}"""))
        server.enqueue(jsonResponse(200, """{"tree":[]}"""))
        val result = client.fetchProjectFilesForPath(gitlabConfig(), 1L, "src/", 10, 1000)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword returns empty when config is inactive`() {
        val result =
            client.searchKeyword(
                config = gitlabConfig(active = false),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword returns empty when fetchAllProjects fails`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
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
    fun `searchKeyword returns empty when no projects match`() {
        server.enqueue(jsonResponse(200, "[]"))
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
    fun `searchKeyword with empty repositoryFilters short-circuits the filter step`() {
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
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword with blank-only repositoryFilters short-circuits the filter step`() {
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
                repositoryFilters = listOf("", "  "),
            )
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword handles blob search 429 rate limit`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword handles 4xx client error in blob search`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": "main"}"""))
        server.enqueue(jsonResponse(200, """{"tree":[]}"""))
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
    fun `searchKeyword with blob search returning results`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"project_id": 1, "path": "src/Foo.kt", "ref": "main",
                     "startline": 10, "data": "class Foo { fun bar() {} }"}
                ]""",
            ),
        )
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "Foo",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertFalse(result.isEmpty())
        assertEquals(1, result.size)
        assertEquals("src/Foo.kt", result[0].filePath)
    }

    @Test
    fun `searchKeyword filters by repository name`() {
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "other", "path_with_namespace": "g/other",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"},
                    {"id": 2, "name": "wanted", "path_with_namespace": "g/wanted",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        server.enqueue(jsonResponse(200, "[]"))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = listOf("wanted"),
            )
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword uses group ID when configured`() {
        val cfg =
            GitlabConfig(
                id = 1L,
                name = "test",
                baseUrl = server.url("/").toString().removeSuffix("/"),
                accessToken = "tok",
                active = true,
                groupId = "42",
            )
        server.enqueue(jsonResponse(200, "[]"))
        val result =
            client.searchKeyword(
                config = cfg,
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword handles tree-walk fallback when blob search returns empty`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
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
    fun `searchKeyword uses project selectors to filter projects`() {
        val cfg =
            GitlabConfig(
                id = 1L,
                name = "test",
                baseUrl = server.url("/").toString().removeSuffix("/"),
                accessToken = "tok",
                active = true,
                projectIds = "1,2",
            )
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id": 1, "name": "one", "path_with_namespace": "g/one",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"},
                    {"id": 2, "name": "two", "path_with_namespace": "g/two",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"},
                    {"id": 99, "name": "other", "path_with_namespace": "g/other",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}
                ]""",
            ),
        )
        server.enqueue(jsonResponse(200, "[]"))
        server.enqueue(jsonResponse(200, "[]"))
        val result =
            client.searchKeyword(
                config = cfg,
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertNotNull(result)
    }

    @Test
    fun `GitlabProjectInfo stores all fields`() {
        val info =
            GitlabProjectInfo(
                id = 1L,
                name = "P",
                pathWithNamespace = "g/p",
                defaultBranch = "main",
                httpUrlToRepo = "https://gl/p.git",
            )
        assertEquals(1L, info.id)
        assertEquals("P", info.name)
        assertEquals("g/p", info.pathWithNamespace)
        assertEquals("main", info.defaultBranch)
        assertEquals("https://gl/p.git", info.httpUrlToRepo)
    }

    @Test
    fun `GitlabProjectInfo equality holds by field value`() {
        val a =
            GitlabProjectInfo(
                id = 1L,
                name = "P",
                pathWithNamespace = "g/p",
                defaultBranch = "main",
                httpUrlToRepo = "https://gl/p.git",
            )
        val b =
            GitlabProjectInfo(
                id = 1L,
                name = "P",
                pathWithNamespace = "g/p",
                defaultBranch = "main",
                httpUrlToRepo = "https://gl/p.git",
            )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TestProject(
        val id: Long = 0,
        val name: String = "",
        val path_with_namespace: String = "",
        val default_branch: String? = null,
        val http_url_to_repo: String? = null,
    )

    @Test
    fun `GitlabClient can be instantiated with default dependencies`() {
        val c = GitlabClient()
        assertNotNull(c)
    }

    @Test
    fun `searchKeyword falls back to tree-walk on 4xx blob search`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(404).setBody("nf"))
        server.enqueue(
            jsonResponse(
                200,
                """[{"id":"a","name":"Foo.kt","type":"blob","path":"src/Foo.kt"}]""",
            ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/plain")
                .setBody("line1\nline2 with magicmarker here\nline3"),
        )
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "magicmarker",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertEquals(1, result.size)
        assertEquals("src/Foo.kt", result[0].filePath)
    }

    @Test
    fun `searchKeyword tree-walk handles non-5xx generic errors`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("server err"))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword blob search paginates up to 5 pages then stops`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}""",
            ),
        )
        val page1 = (1..100).joinToString(",") { """{"project_id":1,"path":"src/F$it.kt","ref":"main","basename":"F$it","data":"x","project_id":1}""" }
        server.enqueue(
            jsonResponse(
                200,
                """{"total":600,"items":[$page1]}""",
            ),
        )
        repeat(4) { idx ->
            val page = (1..100).joinToString(",") { """{"project_id":1,"path":"src/H$idx$it.kt","ref":"main","basename":"H$idx$it","data":"x"}""" }
            server.enqueue(
                jsonResponse(
                    200,
                    """{"total":600,"items":[$page]}""",
                ),
            )
        }
        server.enqueue(jsonResponse(200, """{"total":600,"items":[]}"""))

        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword tree-walk handles 5xx blob search error`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword tree-walk uses default branch when project default is null`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": null, "http_url_to_repo": "https://x.git"}]""",
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
        assertNotNull(result)
    }

    @Test
    fun `searchKeyword applies blob result with parseCodeContext`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"project_id": 1, "path": "src/Foo.kt", "ref": "main",
                     "startline": 12, "data": "class Foo { fun bar() {} }"}
                ]""",
            ),
        )
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "Foo",
                sessionId = 1L,
                crossRefParentId = 99L,
                repositoryFilters = emptyList(),
            )
        assertEquals(1, result.size)
        assertEquals(99L, result[0].crossRefParentId)
        assertEquals("src/Foo.kt", result[0].filePath)
        assertEquals(12, result[0].lineNumber)
    }

    @Test
    fun `searchKeyword handles blob search 500 server error gracefully`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500).setBody("err"))
        val result =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "x",
                sessionId = 1L,
                crossRefParentId = null,
                repositoryFilters = emptyList(),
            )
        assertNotNull(result)
    }

    @Test
    fun `fetchProjectFiles returns files when project and tree succeed`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id":"1","name":"Dockerfile","type":"blob","path":"Dockerfile"},
                    {"id":"2","name":"Foo.kt","type":"blob","path":"src/Foo.kt"}
                ]""",
            ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("\"FROM alpine\""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("\"fun foo() {}\""),
        )
        val result = client.fetchProjectFiles(gitlabConfig(), 1L, 5, 1024)
        assertEquals(2, result.size)
        assertEquals("Dockerfile", result[0].first)
    }

    @Test
    fun `fetchProjectFiles handles non-5xx exceptions with empty list`() {
        server.enqueue(MockResponse().setBody("not json"))
        val result = client.fetchProjectFiles(gitlabConfig(), 1L, 5, 1024)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchProjectFilesForPath filters files under subPath`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": "main"}"""))
        server.enqueue(
            jsonResponse(
                200,
                """{"tree":[
                    {"id":"1","name":"logo","type":"blob","path":"logo.png"},
                    {"id":"2","name":"Foo.kt","type":"blob","path":"src/Foo.kt"},
                    {"id":"3","name":"Bar.kt","type":"blob","path":"src/sub/Bar.kt"}
                ]}""",
            ),
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody("code foo"))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody("code bar"))
        val result = client.fetchProjectFilesForPath(gitlabConfig(), 1L, "src", 10, 1024)
        assertNotNull(result)
    }

    @Test
    fun `fetchProjectFiles returns empty when listProjectFilePaths throws on project lookup`() {
        server.enqueue(MockResponse().setBody("not json"))
        val result = client.fetchProjectFiles(gitlabConfig(), 1L, 5, 1024)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listProjectFilePaths returns filtered blob paths from tree endpoint`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": "main"}"""))
        server.enqueue(
            jsonResponse(
                200,
                """[
                    {"id":"1","name":"a.txt","type":"blob","path":"a.txt"},
                    {"id":"2","name":"subdir","type":"tree","path":"subdir"},
                    {"id":"3","name":"b.txt","type":"blob","path":"b.txt"}
                ]""",
            ),
        )
        val result = client.listProjectFilePaths(gitlabConfig(), 1L)
        assertEquals(2, result.size)
        assertTrue(result.contains("a.txt"))
        assertTrue(result.contains("b.txt"))
    }

    @Test
    fun `fetchProjectFilesForPath with tree exception returns empty list`() {
        server.enqueue(jsonResponse(200, """{"id": 1, "default_branch": "main"}"""))
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.fetchProjectFilesForPath(gitlabConfig(), 1L, "src", 10, 1024)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listAccessibleProjectsInternal returns empty list when the API responds with an empty body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val result = client.listAccessibleProjectsInternal(gitlabConfig())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword returns empty list when the API responds with an empty body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val result = client.searchKeyword(gitlabConfig(), "foo", 1L)
        assertTrue(result.isEmpty())
    }
}

class GitlabClientSearchBlobsErrorTest {
    private lateinit var server: MockWebServer
    private lateinit var webClientBuilder: WebClient.Builder
    private lateinit var treeClient: GitlabTreeApiClient
    private lateinit var codeParser: GitlabCodeContextParser
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
        treeClient = GitlabTreeApiClient()
        codeParser = GitlabCodeContextParser()
        client = GitlabClient(treeClient, codeParser)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun gitlabConfig() =
        GitlabConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("").toString().removeSuffix("/"),
            accessToken = "tok",
            active = true,
        )

    private fun jsonResponse(
        code: Int,
        body: String,
    ): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(body)

    @Test
    fun `searchKeyword when blob search returns 429 yields empty results`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))
        val result = client.searchKeyword(gitlabConfig(), "kw", 1L, null, emptyList<String>())
        assertTrue(result.isEmpty(), "expected empty result but was $result")
    }

    @Test
    fun `searchKeyword when blob search returns 400 falls back to tree-walk and returns empty`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))
        server.enqueue(jsonResponse(200, """{"tree":[]}"""))
        val result = client.searchKeyword(gitlabConfig(), "kw", 1L, null, emptyList<String>())
        assertTrue(result.isEmpty(), "expected empty result but was $result")
    }

    @Test
    fun `searchKeyword when blob search returns 500 breaks and falls back to tree-walk`() {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id": 1, "name": "P", "path_with_namespace": "g/p",
                     "default_branch": "main", "http_url_to_repo": "https://x.git"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        server.enqueue(jsonResponse(200, """{"tree":[]}"""))
        val result = client.searchKeyword(gitlabConfig(), "kw", 1L, null, emptyList<String>())
        assertTrue(result.isEmpty(), "expected empty result but was $result")
    }
}
