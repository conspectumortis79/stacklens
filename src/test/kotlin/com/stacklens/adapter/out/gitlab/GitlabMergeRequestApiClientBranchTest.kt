package com.stacklens.adapter.out.gitlab

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GitlabMergeRequestApiClientBranchTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitlabMergeRequestApiClient
    private lateinit var webClient: WebClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        webClient = WebClient.builder().baseUrl(server.url("/").toString()).build()
        client = GitlabMergeRequestApiClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `searchMrs returns empty on error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.searchMrs(webClient, 1, "test")
        assertEquals(0, result.size)
    }

    @Test
    fun `searchMrs parses valid response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"iid":1,"title":"test","web_url":"https://example.com/mr/1"}]"""),
        )
        val result = client.searchMrs(webClient, 1, "test")
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchMrChanges returns empty on error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.fetchMrChanges(webClient, 1, 1)
        assertEquals(0, result.size)
    }

    @Test
    fun `fetchMrChanges parses valid response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changes":[{"old_path":"a","new_path":"b"}]}"""),
        )
        val result = client.fetchMrChanges(webClient, 1, 1)
        assertEquals(1, result.size)
    }

    @Test
    fun `searchMrs returns empty when server is down and connection fails`() {
        server.shutdown()
        val result = client.searchMrs(webClient, 1, "test")
        assertEquals(0, result.size)
    }

    @Test
    fun `fetchMrChanges returns empty when server is down and connection fails`() {
        server.shutdown()
        val result = client.fetchMrChanges(webClient, 1, 1)
        assertEquals(0, result.size)
    }
}
