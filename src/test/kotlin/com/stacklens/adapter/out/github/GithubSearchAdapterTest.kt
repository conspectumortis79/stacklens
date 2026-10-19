package com.stacklens.adapter.out.github

import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.GithubRepoConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GithubSearchAdapterTest {
    private lateinit var mockServer: MockWebServer

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    private fun adapter(defaultApiBase: String = mockServer.url("/").toString().trimEnd('/')) = GithubSearchAdapter(WebClient.builder(), defaultApiBase = defaultApiBase)

    private fun config(
        apiBase: String? = mockServer.url("/").toString().trimEnd('/'),
        accessToken: String? = null,
        active: Boolean = true,
        owner: String = "acme",
        repo: String = "svc",
        branch: String? = "main",
    ) = GithubRepoConfig(
        name = "test",
        owner = owner,
        repo = repo,
        apiBase = apiBase,
        accessToken = accessToken,
        active = active,
        branch = branch,
    )

    private val successBody =
        """
        {
          "total_count": 1,
          "items": [{
            "path": "src/Foo.kt",
            "html_url": "https://github.com/acme/svc/blob/main/src/Foo.kt",
            "text_matches": [{"fragment": "some matching code"}]
          }]
        }
        """.trimIndent()

    private val emptyBody = """{"total_count":0,"items":[]}"""

    @Test
    fun `searchKeyword returns empty list when config is inactive`() {
        val results = adapter().searchKeyword(config(active = false), "keyword", 1L)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword returns mapped results on successful response`() {
        mockServer.enqueue(MockResponse().setBody(successBody).addHeader("Content-Type", "application/json"))

        val results = adapter().searchKeyword(config(), "findMe", 42L)

        assertEquals(1, results.size)
        val r = results[0]
        assertEquals(42L, r.sessionId)
        assertEquals("findMe", r.keyword)
        assertEquals(SourceType.GITHUB, r.sourceType)
        assertEquals("src/Foo.kt", r.filePath)
        assertEquals("some matching code", r.codeSnippet)
        assertEquals("https://github.com/acme/svc/blob/main/src/Foo.kt", r.repoUrl)
        assertEquals("acme/svc", r.repoName)
        assertEquals("main", r.branch)
    }

    @Test
    fun `searchKeyword returns empty list on HTTP error response`() {
        mockServer.enqueue(MockResponse().setResponseCode(503))

        val results = adapter().searchKeyword(config(), "keyword", 1L)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword sends Authorization header when access token is set`() {
        mockServer.enqueue(MockResponse().setBody(emptyBody).addHeader("Content-Type", "application/json"))

        adapter().searchKeyword(config(accessToken = "ghp_token123"), "kw", 1L)

        val request = mockServer.takeRequest()
        assertEquals("Bearer ghp_token123", request.getHeader("Authorization"))
    }

    @Test
    fun `searchKeyword does not send Authorization header when access token is null`() {
        mockServer.enqueue(MockResponse().setBody(emptyBody).addHeader("Content-Type", "application/json"))

        adapter().searchKeyword(config(accessToken = null), "kw", 1L)

        val request = mockServer.takeRequest()
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `searchKeyword uses defaultApiBase when config apiBase is null`() {
        mockServer.enqueue(MockResponse().setBody(emptyBody).addHeader("Content-Type", "application/json"))

        adapter().searchKeyword(config(apiBase = null), "kw", 1L)

        assertNotNull(mockServer.takeRequest())
    }

    @Test
    fun `searchKeyword uses defaultApiBase when config apiBase is blank`() {
        mockServer.enqueue(MockResponse().setBody(emptyBody).addHeader("Content-Type", "application/json"))

        adapter().searchKeyword(config(apiBase = "  "), "kw", 1L)

        assertNotNull(mockServer.takeRequest())
    }

    @Test
    fun `searchKeyword maps result with no text matches to null codeSnippet`() {
        val noMatchesBody =
            """
            {
              "total_count": 1,
              "items": [{
                "path": "src/Bar.kt",
                "html_url": "https://github.com/acme/svc/blob/main/src/Bar.kt",
                "text_matches": []
              }]
            }
            """.trimIndent()
        mockServer.enqueue(MockResponse().setBody(noMatchesBody).addHeader("Content-Type", "application/json"))

        val results = adapter().searchKeyword(config(), "bar", 7L)

        assertEquals(1, results.size)
        assertNull(results[0].codeSnippet)
    }

    @Test
    fun `searchKeyword returns empty list when server returns 204 no content`() {
        mockServer.enqueue(MockResponse().setResponseCode(204))

        val results = adapter().searchKeyword(config(), "keyword", 1L)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword does not send Authorization header when access token is blank`() {
        mockServer.enqueue(MockResponse().setBody(emptyBody).addHeader("Content-Type", "application/json"))

        adapter().searchKeyword(config(accessToken = ""), "kw", 1L)

        val request = mockServer.takeRequest()
        assertNull(request.getHeader("Authorization"))
    }
}
