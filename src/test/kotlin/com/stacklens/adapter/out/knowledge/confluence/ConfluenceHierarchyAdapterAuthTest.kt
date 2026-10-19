package com.stacklens.adapter.out.knowledge.confluence

import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.Base64

class ConfluenceHierarchyAdapterAuthTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: ConfluenceHierarchyAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = ConfluenceHierarchyAdapter(ReactiveHttpClientFactory())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(
        baseUrl: String = server.url("").toString().removeSuffix("/"),
        username: String = "user",
        apiToken: String = "token",
    ) = ConfluenceConfig(
        name = "conf",
        baseUrl = baseUrl,
        username = username,
        apiToken = apiToken,
        cloudMode = true,
    )

    private fun pageJson(
        id: String,
        spaceKey: String,
        title: String,
        body: String = "<p>hello</p>",
    ): String =
        """
        {
          "id": "$id",
          "title": "$title",
          "space": { "key": "$spaceKey" },
          "body": { "view": { "value": "$body" }, "storage": { "value": "$body" } }
        }
        """.trimIndent()

    @Test
    fun `walkHierarchy sends Basic auth header for cloud config with username`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(pageJson(id = "51744614", spaceKey = "F42", title = "Team binit")),
        )

        val snapshots =
            adapter
                .walkHierarchy(
                    config = config(username = "[email protected]", apiToken = "secret-token"),
                    rootPageUrl = "https://banana.atlassian.net/wiki/spaces/F42/pages/51744614/Team+binit",
                    includeChildren = false,
                    maxDepth = 1,
                ).toList()

        assertEquals(1, snapshots.size, "root page must be yielded")
        assertEquals("Team binit", snapshots.first().title)

        val recorded = server.takeRequest()
        assertTrue(
            recorded.path?.startsWith("/wiki/rest/api/content/51744614") == true,
            "expected /wiki/rest/api/content/51744614... but was ${recorded.path}",
        )
        val auth = recorded.getHeader("Authorization")
        assertNotNull(auth, "Authorization header must be set on the page fetch")
        val expected =
            "Basic " + Base64.getEncoder().encodeToString("[email protected]:secret-token".toByteArray())
        assertEquals(expected, auth)
    }

    @Test
    fun `walkHierarchy sends Bearer auth header when username is blank (PAT)`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(pageJson(id = "100", spaceKey = "F42", title = "Root")),
        )

        val snapshots =
            adapter
                .walkHierarchy(
                    config = config(username = "", apiToken = "pat-xyz"),
                    rootPageUrl = "https://example.com/wiki/spaces/F42/pages/100/Root",
                    includeChildren = false,
                    maxDepth = 1,
                ).toList()

        assertEquals(1, snapshots.size)
        val auth = server.takeRequest().getHeader("Authorization")
        assertEquals("Bearer pat-xyz", auth)
    }

    @Test
    fun `walkHierarchy throws with the root cause when the API returns 401 on the root page`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"message":"Unauthorized"}"""),
        )

        val ex =
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
                adapter
                    .walkHierarchy(
                        config = config(),
                        rootPageUrl = "https://example.com/wiki/spaces/F42/pages/1/X",
                        includeChildren = false,
                        maxDepth = 1,
                    ).toList()
            }
        assertTrue(
            ex.message!!.contains("root page fetch failed"),
            "expected root-fetch error, got: ${ex.message}",
        )
        val recorded = server.takeRequest()
        assertNotNull(recorded.getHeader("Authorization"), "Authorization must still be sent on the wire")
    }

    @Test
    fun `walkHierarchy surfaces a connection-refused error from the root page instead of returning zero pages`() {
        val unreachableConfig =
            ConfluenceConfig(
                name = "down",
                baseUrl = "http://127.0.0.1:1",
                username = "u",
                apiToken = "t",
            )
        val ex =
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
                adapter
                    .walkHierarchy(
                        config = unreachableConfig,
                        rootPageUrl = "http://127.0.0.1:1/wiki/spaces/F42/pages/1/X",
                        includeChildren = false,
                        maxDepth = 1,
                    ).toList()
            }
        assertTrue(
            ex.message!!.contains("root page fetch failed"),
            "expected root-fetch error, got: ${ex.message}",
        )
        assertTrue(
            ex.cause != null,
            "expected underlying transport cause to be preserved, got: ${ex.cause}",
        )
    }
}
