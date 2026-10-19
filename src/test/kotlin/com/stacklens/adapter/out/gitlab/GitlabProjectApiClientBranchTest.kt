package com.stacklens.adapter.out.gitlab

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GitlabProjectApiClientBranchTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabProjectApiClient
    private lateinit var webClient: WebClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        webClient = WebClient.builder().baseUrl(server.url("/").toString()).build()
        client = GitlabProjectApiClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchProjects returns empty list when server returns 500`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        val result = client.fetchProjects(webClient, null)
        assertEquals(0, result.size)
    }

    @Test
    fun `fetchProjects returns empty list when server returns 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.fetchProjects(webClient, null)
        assertEquals(0, result.size)
    }

    @Test
    fun `fetchProjects parses valid response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"name":"test","path_with_namespace":"grp/test","default_branch":"main"}]""",
                ),
        )
        val result = client.fetchProjects(webClient, null)
        assertEquals(1, result.size)
        assertEquals("test", result[0].name)
    }

    @Test
    fun `fetchProjectDtoById returns null when server returns 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.fetchProjectDtoById(webClient, 999)
        assertEquals(null, result)
    }

    @Test
    fun `fetchProjectDetail returns empty dto when server returns 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.fetchProjectDetail(webClient, 999)
        assertNotNull(result)
    }

    @Test
    fun `fetchProjectDetail parses valid response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":1,"name":"test","default_branch":"main"}"""),
        )
        val result = client.fetchProjectDetail(webClient, 1)
        assertEquals(1L, result.id)
    }
}
