package com.stacklens.adapter.out.confluence

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfluenceClientSearchKeywordTest {
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

    private fun jsonResponse(
        code: Int,
        body: String,
    ): MockResponse = MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json")

    @Test
    fun `searchKeyword with spaceKeys list invokes the quoteEach lambda`() {
        val body =
            """{"results":[],"start":0,"limit":25,"size":0}"""
        server.enqueue(jsonResponse(200, body))
        val cfg =
            cfg().apply {
                spaceKeys = "DEV,OPS"
            }
        val result = client.searchKeyword(cfg, "kw", emptyList(), 1L, 0)
        assertNotNull(result)
        val recorded = server.takeRequest()
        val cql = recorded.path!!.substringAfter("cql=").substringBefore("&")
        assertTrue(
            cql.contains("space"),
            "expected CQL to include space filter, got: $cql",
        )
    }

    @Test
    fun `searchKeyword returns results for cloud config with valid response`() {
        val body =
            """
            {
              "results": [
                {
                  "id": "1",
                  "title": "My Page",
                  "type": "page",
                  "excerpt": "This is the <em>keyword</em> excerpt",
                  "space": {"key": "DEV", "name": "Dev Space"},
                  "_links": {"webui": "/spaces/DEV/pages/1"}
                }
              ],
              "start": 0,
              "limit": 25,
              "size": 1
            }
            """.trimIndent()
        server.enqueue(jsonResponse(200, body))
        val result = client.searchKeyword(cfg(), "keyword", emptyList(), 1L, 0)
        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "expected at least 1 result, got ${result.size}")
        val r = result[0]
        assertEquals("My Page", r.filePath)
        assertEquals("Dev Space", r.repoName)
    }

    @Test
    fun `searchKeyword returns empty when server returns empty results`() {
        server.enqueue(jsonResponse(200, """{"results":[],"start":0,"limit":25,"size":0}"""))
        val result = client.searchKeyword(cfg(), "nothing", emptyList(), 1L, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword handles blogpost type with CONFLUENCE_BLOGPOST result type`() {
        val body =
            """
            {
              "results": [
                {
                  "id": "1",
                  "title": "Blog Post",
                  "type": "blogpost",
                  "excerpt": "blog <em>text</em>",
                  "space": {"key": "BLOG", "name": "Blog"},
                  "_links": {"webui": "/spaces/BLOG/blogposts/1"}
                }
              ],
              "start": 0,
              "limit": 25,
              "size": 1
            }
            """.trimIndent()
        server.enqueue(jsonResponse(200, body))
        val result = client.searchKeyword(cfg(), "blog", emptyList(), 1L, 0)
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        assertEquals(com.stacklens.domain.enums.ResultType.CONFLUENCE_BLOGPOST, result[0].resultType)
    }

    @Test
    fun `searchKeyword returns empty when HTTP error occurs`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        val result = client.searchKeyword(cfg(), "kw", emptyList(), 1L, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword uses server-mode expand fields when cloudMode is false`() {
        val cfg = cfg().apply { cloudMode = false }
        val body =
            """
            {
              "results": [
                {
                  "id": "1",
                  "title": "Server Page",
                  "type": "page",
                  "excerpt": "x",
                  "space": {"key": "S", "name": "S"},
                  "_links": {"webui": "/x"}
                }
              ],
              "start": 0,
              "limit": 25,
              "size": 1
            }
            """.trimIndent()
        server.enqueue(jsonResponse(200, body))
        val result = client.searchKeyword(cfg, "kw", emptyList(), 1L, 0)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `searchKeyword with websiteFilters exercises the isNotBlank filter branches`() {
        val body =
            """{"results":[],"start":0,"limit":25,"size":0}"""
        server.enqueue(jsonResponse(200, body))
        client.searchKeyword(cfg(), "kw", listOf("  /spaces/DEV  ", "", "   "), 1L, 0)
    }

    @Test
    fun `searchKeyword with websiteFilters all non-blank exercises true branch only`() {
        val body =
            """{"results":[],"start":0,"limit":25,"size":0}"""
        server.enqueue(jsonResponse(200, body))
        client.searchKeyword(cfg(), "kw", listOf("/spaces/DEV", "/spaces/OPS"), 1L, 0)
    }
}
