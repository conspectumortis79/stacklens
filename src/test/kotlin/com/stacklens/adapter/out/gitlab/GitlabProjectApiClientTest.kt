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
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

class GitlabProjectApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabProjectApiClient
    private lateinit var webClientBuilder: WebClient.Builder

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val connector =
            ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(2)),
            )
        webClientBuilder = WebClient.builder().clientConnector(connector)
        client = GitlabProjectApiClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun gitlabConfig(): GitlabConfig =
        GitlabConfig(
            name = "g",
            baseUrl = server.url("").toString().removeSuffix("/"),
            accessToken = "TOK",
            active = true,
        )

    private fun directClient(): WebClient {
        val connector =
            ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(2)),
            )
        return WebClient
            .builder()
            .baseUrl(server.url("").toString().removeSuffix("/"))
            .clientConnector(connector)
            .build()
    }

    @Test
    fun `buildClient creates a WebClient with the correct baseUrl and PRIVATE-TOKEN header`() {
        val built = client.buildClient(gitlabConfig(), apiBase = server.url("").toString().removeSuffix("/"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}").setHeader("Content-Type", "application/json"))
        try {
            built
                .get()
                .uri("/test")
                .retrieve()
                .toEntity(String::class.java)
                .block()
        } catch (_: Exception) {
        }
        val req = server.takeRequest()
        assertEquals("TOK", req.getHeader("PRIVATE-TOKEN"))
    }

    @Test
    fun `buildClient uses the apiBase parameter as baseUrl`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val built = client.buildClient(gitlabConfig(), apiBase = server.url("").toString().removeSuffix("/"))
        try {
            built
                .get()
                .uri("/test")
                .retrieve()
                .toEntity(String::class.java)
                .block()
        } catch (_: Exception) {
        }
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("/test"))
    }

    @Test
    fun `fetchProjects without groupId queries membership projects endpoint`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"name":"r1","path_with_namespace":"g/r1"},{"id":2,"name":"r2","path_with_namespace":"g/r2"}]""",
                ),
        )
        val result = client.fetchProjects(directClient(), groupId = null)
        assertEquals(2, result.size)
        assertEquals("r1", result[0].name)
        assertEquals("g/r1", result[0].pathWithNamespace)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("membership=true"))
        assertTrue(req.path!!.contains("per_page=100"))
    }

    @Test
    fun `fetchProjects with groupId queries group projects endpoint with subgroups`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"r","path_with_namespace":"g/r"}]"""),
        )
        val result = client.fetchProjects(directClient(), groupId = "my-group/sub")
        assertEquals(1, result.size)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("groups"))
        assertTrue(req.path!!.contains("include_subgroups=true"))
    }

    @Test
    fun `fetchProjects handles empty response`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val result = client.fetchProjects(directClient(), groupId = null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchProjects paginates until fewer than 100 results`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[" + (1..100).joinToString(",") { """{"id":$it,"name":"r$it","path_with_namespace":"g/r$it"}""" } + "]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[" + (1..50).joinToString(",") { """{"id":$it,"name":"p$it","path_with_namespace":"g/p$it"}""" } + "]"),
        )
        val result = client.fetchProjects(directClient(), groupId = null)
        assertEquals(150, result.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `fetchProjects caps at 5 pages even if all pages return 100`() {
        repeat(5) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[" + (1..100).joinToString(",") { """{"id":$it,"name":"r$it","path_with_namespace":"g/r$it"}""" } + "]"),
            )
        }
        client.fetchProjects(directClient(), groupId = null)
        assertEquals(5, server.requestCount)
    }

    @Test
    fun `fetchProjects swallows server errors and returns partial results`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"r","path_with_namespace":"g/r"}]"""),
        )
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.fetchProjects(directClient(), groupId = null)
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchProjects returns empty when server returns error on first call`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.fetchProjects(directClient(), groupId = null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchProjectDetail returns the project detail with default branch`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":42,"default_branch":"develop"}"""),
        )
        val result = client.fetchProjectDetail(directClient(), projectId = 42L)
        assertEquals(42L, result.id)
        assertEquals("develop", result.defaultBranch)
    }

    @Test
    fun `fetchProjectDetail returns default DTO on error`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.fetchProjectDetail(directClient(), projectId = 99L)
        assertEquals(0L, result.id)
        assertEquals("main", result.defaultBranch)
    }

    @Test
    fun `fetchProjectDetail returns default DTO when response body is empty`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("null"))
        val result = client.fetchProjectDetail(directClient(), projectId = 42L)
        assertEquals(0L, result.id)
    }

    @Test
    fun `fetchProjectDtoById returns the project DTO`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":42,"name":"my-repo","path_with_namespace":"g/my-repo"}"""),
        )
        val result = client.fetchProjectDtoById(directClient(), projectId = 42L)
        assertNotNull(result)
        assertEquals("my-repo", result!!.name)
        assertEquals("g/my-repo", result.pathWithNamespace)
    }

    @Test
    fun `fetchProjectDtoById returns null on error`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.fetchProjectDtoById(directClient(), projectId = 99L)
        assertNull(result)
    }

    @Test
    fun `resolveProjectByNameOrPath returns null for empty repoName`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val result = client.resolveProjectByNameOrPath(directClient(), groupId = null, repoName = "")
        assertNull(result)
    }

    @Test
    fun `resolveProjectByNameOrPath matches by name`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"name":"foo","path_with_namespace":"g/foo"},{"id":2,"name":"bar","path_with_namespace":"g/bar"}]""",
                ),
        )
        val result = client.resolveProjectByNameOrPath(directClient(), groupId = null, repoName = "foo")
        assertNotNull(result)
        assertEquals("foo", result!!.name)
    }

    @Test
    fun `resolveProjectByNameOrPath matches by pathWithNamespace suffix`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"x","path_with_namespace":"g/my-repo"}]"""),
        )
        val result = client.resolveProjectByNameOrPath(directClient(), groupId = null, repoName = "my-repo")
        assertNotNull(result)
        assertEquals("x", result!!.name)
    }

    @Test
    fun `resolveProjectByNameOrPath tries direct path lookup for slash names`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":1,"name":"my-repo","path_with_namespace":"g/my-repo"}"""),
        )
        val result = client.resolveProjectByNameOrPath(directClient(), groupId = null, repoName = "g/my-repo")
        assertNotNull(result)
        assertEquals("my-repo", result!!.name)
        val req = server.takeRequest()
        assertTrue(
            req.path!!.contains("/api/v4/projects/"),
            "expected path to contain /api/v4/projects/ but was ${req.path}",
        )
    }

    @Test
    fun `resolveProjectByNameOrPath falls back to last-segment match for slash names`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"my-repo","path_with_namespace":"g/sub/my-repo"}]"""),
        )
        val result = client.resolveProjectByNameOrPath(directClient(), groupId = null, repoName = "g/my-repo")
        assertNotNull(result)
        assertEquals("my-repo", result!!.name)
    }

    @Test
    fun `resolveProjectByNameOrPath returns null when no match`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"name":"foo","path_with_namespace":"g/foo"}]"""),
        )
        val result = client.resolveProjectByNameOrPath(directClient(), groupId = null, repoName = "bar")
        assertNull(result)
    }

    @Test
    fun `findProjectById returns GitlabProjectInfo when found`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":42,"name":"foo","path_with_namespace":"g/foo"}"""),
        )
        val result = client.findProjectById(gitlabConfig(), gitlabProjectId = 42L)
        assertNotNull(result)
        assertEquals(42L, result!!.id)
        assertEquals("foo", result.name)
        assertEquals("g/foo", result.pathWithNamespace)
        assertNull(result.defaultBranch)
        assertEquals("", result.httpUrlToRepo)
    }

    @Test
    fun `findProjectById returns null when not found`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.findProjectById(gitlabConfig(), gitlabProjectId = 99L)
        assertNull(result)
    }
}
