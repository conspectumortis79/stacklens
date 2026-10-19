package com.stacklens.adapter.out.gitlab

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GitlabRepoFileApiClientBranchTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabRepoFileApiClient
    private lateinit var webClient: WebClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        webClient = WebClient.builder().baseUrl(server.url("/").toString()).build()
        client = GitlabRepoFileApiClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchRepoTree returns empty list on error`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        val result = client.fetchRepoTree(webClient, 1)
        assertEquals(0, result.size)
    }

    @Test
    fun `fetchRepoTree parses valid response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":"a1","name":"foo.kt","type":"blob","path":"src/foo.kt"}]""",
                ),
        )
        val result = client.fetchRepoTree(webClient, 1)
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchRawFile returns null on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.fetchRawFile(webClient, 1, "missing.kt")
        assertEquals(null, result)
    }

    @Test
    fun `fetchRawFile returns null on 500`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.fetchRawFile(webClient, 1, "missing.kt")
        assertEquals(null, result)
    }

    @Test
    fun `fetchRepoTree returns empty list when server is down and connection fails`() {
        server.shutdown()
        val result = client.fetchRepoTree(webClient, 1)
        assertEquals(0, result.size)
    }

    @Test
    fun `fetchRepoTree handles synchronous exception via try-catch via bad URI`() {
        val badClient =
            WebClient
                .builder()
                .baseUrl("http://")
                .build()
        val result = client.fetchRepoTree(badClient, 1)
        assertEquals(0, result.size)
    }
}
