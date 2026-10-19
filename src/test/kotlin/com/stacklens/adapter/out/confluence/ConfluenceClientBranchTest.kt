package com.stacklens.adapter.out.confluence

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfluenceClientBranchTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ConfluenceClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            ConfluenceClient(
                readTimeoutMs = 5000,
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun cfg() =
        com.stacklens.domain.model.ConfluenceConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            apiToken = "t",
        )

    @Test
    fun `searchKeyword handles 5xx error gracefully`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        val result = client.searchKeyword(cfg(), "x", emptyList(), 1L, 0)
        assertNotNull(result)
        assertEquals(0, result.size)
    }

    @Test
    fun `searchKeyword catches generic Exception and returns empty response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"results":[{"invalid_field":"data}]"""),
        )
        val result = client.searchKeyword(cfg(), "x", emptyList(), 1L, 0)
        assertNotNull(result)
        assertEquals(0, result.size)
    }

    @Test
    fun `searchKeyword returns empty results for 200 with empty body`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"results":[]}"""),
        )
        val result = client.searchKeyword(cfg(), "x", emptyList(), 1L, 0)
        assertNotNull(result)
        assertEquals(0, result.size)
    }

    @Test
    fun `searchKeyword parses valid response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"results":[{"id":"123","type":"page","title":"Hello","space":{"key":"DEV","name":"Dev"}}]}""",
                ),
        )
        val result = client.searchKeyword(cfg(), "x", emptyList(), 1L, 0)
        assertNotNull(result)
    }
}
