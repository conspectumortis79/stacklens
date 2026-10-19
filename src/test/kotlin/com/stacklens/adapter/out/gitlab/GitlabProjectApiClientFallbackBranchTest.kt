package com.stacklens.adapter.out.gitlab

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

class GitlabProjectApiClientFallbackBranchTest {
    private lateinit var server: MockWebServer

    private val api = GitlabProjectApiClient()

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun client(): WebClient =
        WebClient
            .builder()
            .baseUrl(server.url("").toString().removeSuffix("/"))
            .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(Duration.ofSeconds(5))))
            .build()

    private fun brokenClient(): WebClient {
        val broken = mock<WebClient>()
        whenever(broken.get()).thenThrow(IllegalStateException("connection reset"))
        return broken
    }

    private fun enqueue(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun project(
        id: Long,
        name: String,
        path: String,
    ): String = """{"id":$id,"name":"$name","path_with_namespace":"$path"}"""

    private fun pageOf(count: Int): String = (1..count).joinToString(prefix = "[", postfix = "]") { index -> project(index.toLong(), "auto$index", "group/auto$index") }

    @Test
    fun `project listing degrades to an empty list when the transport breaks`() {
        assertEquals(emptyList<JiraBridgeGitlabProjectDto>(), api.fetchProjects(brokenClient(), null))
    }

    @Test
    fun `project listing treats an empty body as no projects`() {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(emptyList<JiraBridgeGitlabProjectDto>(), api.fetchProjects(client(), "group"))
    }

    @Test
    fun `project listing walks every page while results stay full`() {
        enqueue(pageOf(100))
        server.enqueue(MockResponse().setResponseCode(200))
        val projects = api.fetchProjects(client(), "team")
        assertEquals(100, projects.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `project detail falls back to an empty dto on errors and empty bodies`() {
        assertEquals("main", api.fetchProjectDetail(brokenClient(), 3L).defaultBranch)
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(0L, api.fetchProjectDetail(client(), 3L).id)
        enqueue("""{"id":9,"default_branch":"release"}""")
        assertEquals("release", api.fetchProjectDetail(client(), 9L).defaultBranch)
    }

    @Test
    fun `project lookup by id reports broken transports`() {
        assertNull(api.fetchProjectDtoById(brokenClient(), 3L))
    }

    @Test
    fun `project lookup by id treats empty bodies as missing projects`() {
        server.enqueue(MockResponse().setResponseCode(200))
        assertNull(api.fetchProjectDtoById(client(), 3L))
    }

    @Test
    fun `name resolution uses the direct path lookup first`() {
        enqueue(project(42L, "Deep", "team/deep"))
        val resolved = api.resolveProjectByNameOrPath(client(), null, "team/deep")
        assertEquals(42L, resolved?.id)
    }

    @Test
    fun `name resolution keeps browsing when the direct lookup breaks`() {
        assertNull(api.resolveProjectByNameOrPath(brokenClient(), null, "team/repo"))

        enqueue("[${project(7L, "nomatch", "group/other")}]")
        assertNull(api.resolveProjectByNameOrPath(client(), null, "zeta"))

        enqueue("[${project(8L, "ALPHA", "group/alpha")}]")
        assertEquals(8L, api.resolveProjectByNameOrPath(client(), "grp", "alpha")?.id)

        enqueue("[${project(9L, "beta", "team/beta")}]")
        assertEquals(9L, api.resolveProjectByNameOrPath(client(), null, "BETA")?.id)

        server.enqueue(MockResponse().setResponseCode(200))
        enqueue("[${project(10L, "delta", "team/delta")}]")
        assertEquals(10L, api.resolveProjectByNameOrPath(client(), null, "org/deep/delta")?.id)

        server.enqueue(MockResponse().setResponseCode(200))
        enqueue("[${project(11L, "nope", "team/delta")}]")
        assertEquals(11L, api.resolveProjectByNameOrPath(client(), null, "org/delta")?.id)

        server.enqueue(MockResponse().setResponseCode(200))
        enqueue("[${project(12L, "other", "team/other")}]")
        assertNull(api.resolveProjectByNameOrPath(client(), null, "org/missing")?.id)
    }

    @Test
    fun `name resolution matches a namespace suffix without a separator`() {
        server.enqueue(MockResponse().setResponseCode(200))
        enqueue("[${project(13L, "app", "xgroup/app")}]")
        assertEquals(13L, api.resolveProjectByNameOrPath(client(), null, "group/app")?.id)
    }
}
