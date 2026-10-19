package com.stacklens.adapter.out.confluence

import com.stacklens.domain.model.ConfluenceConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfluenceClientSearchRootFilterTest {
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

    private fun cfg(searchRootUrl: String = "") =
        ConfluenceConfig(
            name = "c",
            baseUrl = server.url("").toString().removeSuffix("/"),
            username = "u",
            apiToken = "t",
            active = true,
            cloudMode = true,
            searchRootUrl = searchRootUrl,
        )

    private fun searchResponse(): String =
        """
        {
          "results": [
            {
              "id": "1",
              "title": "Page 1",
              "type": "page",
              "excerpt": "excerpt",
              "space": {"key": "S", "name": "S"},
              "_links": {"webui": "/spaces/S/pages/1"}
            },
            {
              "id": "2",
              "title": "Page 2",
              "type": "page",
              "excerpt": "excerpt",
              "space": {"key": "S", "name": "S"},
              "_links": {"webui": "/some/other/path"}
            }
          ],
          "start": 0,
          "limit": 25,
          "size": 2
        }
        """.trimIndent()

    @Test
    fun `searchKeyword with searchRootUrl filters out non-matching pages`() {
        val mixedResponse =
            """
            {
              "results": [
                {
                  "id": "1",
                  "title": "Page S",
                  "type": "page",
                  "excerpt": "excerpt",
                  "space": {"key": "S", "name": "S"},
                  "_links": {"webui": "/spaces/S/pages/1"}
                },
                {
                  "id": "2",
                  "title": "Page OTHER",
                  "type": "page",
                  "excerpt": "excerpt",
                  "space": {"key": "OTHER", "name": "OTHER"},
                  "_links": {"webui": "/spaces/OTHER/pages/2"}
                }
              ],
              "start": 0,
              "limit": 25,
              "size": 2
            }
            """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(mixedResponse))
        val wikiBase = server.url("").toString().removeSuffix("/") + "/wiki"
        val result = client.searchKeyword(cfg(searchRootUrl = "$wikiBase/spaces/S"), "kw", emptyList(), 1L, 0)
        assertEquals(1, result.size)
    }

    @Test
    fun `searchKeyword with websiteFilters filters out non-matching pages`() {
        val mixedResponse =
            """
            {
              "results": [
                {
                  "id": "1",
                  "title": "Page S",
                  "type": "page",
                  "excerpt": "excerpt",
                  "space": {"key": "S", "name": "S"},
                  "_links": {"webui": "/spaces/S/pages/1"}
                },
                {
                  "id": "2",
                  "title": "Page OTHER",
                  "type": "page",
                  "excerpt": "excerpt",
                  "space": {"key": "OTHER", "name": "OTHER"},
                  "_links": {"webui": "/spaces/OTHER/pages/2"}
                }
              ],
              "start": 0,
              "limit": 25,
              "size": 2
            }
            """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(mixedResponse))
        val wikiBase = server.url("").toString().removeSuffix("/") + "/wiki"
        val result = client.searchKeyword(cfg(), "kw", listOf("$wikiBase/spaces/S"), 1L, 0)
        assertEquals(1, result.size)
    }

    @Test
    fun `searchKeyword with blank searchRootUrl accepts all pages`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(searchResponse()))
        val result = client.searchKeyword(cfg(searchRootUrl = ""), "kw", emptyList(), 1L, 0)
        assertEquals(2, result.size)
    }

    @Test
    fun `searchKeyword with blank websiteFilters accepts all pages`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(searchResponse()))
        val result = client.searchKeyword(cfg(), "kw", emptyList(), 1L, 0)
        assertEquals(2, result.size)
        assertTrue(result.size >= 1)
    }
}
