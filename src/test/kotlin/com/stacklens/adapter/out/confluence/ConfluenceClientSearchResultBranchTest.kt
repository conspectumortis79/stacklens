package com.stacklens.adapter.out.confluence

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfluenceClientSearchResultBranchTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ConfluenceClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ConfluenceClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun cfg() =
        com.stacklens.domain.model.ConfluenceConfig(
            name = "c",
            baseUrl = server.url("").toString().removeSuffix("/"),
            username = "u",
            apiToken = "t",
            active = true,
            cloudMode = true,
        )

    private fun responseWithSpace(spaceJson: String): String =
        """
        {
          "results": [
            {
              "id": "1",
              "title": "P",
              "type": "page",
              "excerpt": "x",
              "space": $spaceJson,
              "_links": {"webui": "/spaces/S/pages/1"}
            }
          ],
          "start": 0,
          "limit": 25,
          "size": 1
        }
        """.trimIndent()

    @Test
    fun `repoName uses space name when space has a non-blank name`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseWithSpace("""{"key": "DEV", "name": "Dev Space"}""")),
        )
        val result = client.searchKeyword(cfg(), "kw", emptyList(), 1L, 0)
        assertEquals(1, result.size)
        assertEquals("Dev Space", result[0].repoName)
    }

    @Test
    fun `repoName falls back to space key when name is blank`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseWithSpace("""{"key": "DEV", "name": "  "}""")),
        )
        val result = client.searchKeyword(cfg(), "kw", emptyList(), 1L, 0)
        assertEquals(1, result.size)
        assertEquals("DEV", result[0].repoName)
    }

    @Test
    fun `repoName uses Unknown Space when space is null`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseWithSpace("null")),
        )
        val result = client.searchKeyword(cfg(), "kw", emptyList(), 1L, 0)
        assertEquals(1, result.size)
        assertEquals("Unknown Space", result[0].repoName)
    }
}
