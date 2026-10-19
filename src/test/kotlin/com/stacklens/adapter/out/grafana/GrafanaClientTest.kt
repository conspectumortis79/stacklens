package com.stacklens.adapter.out.grafana

import com.stacklens.domain.model.GrafanaConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GrafanaClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GrafanaClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            GrafanaClient(
                connectTimeoutMs = 5000,
                readTimeoutMs = 5000,
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(): GrafanaConfig =
        GrafanaConfig(
            id = 1L,
            name = "g",
            baseUrl = server.url("").toString().removeSuffix("/"),
            username = "u",
            password = "p",
        )

    @Test
    fun `searchKeyword returns dashboard hit when title matches`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"uid":"u1","title":"Auth Dashboard","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"dashboard":{}}"""),
        )
        val results = client.searchKeyword(config(), "auth", sessionId = 1, dashboardFilters = emptyList())
        assertTrue(results.any { it.repoName == "Auth Dashboard" })
    }

    @Test
    fun `searchKeyword returns empty on error from searchDashboards`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        val results = client.searchKeyword(config(), "kw", sessionId = 1, dashboardFilters = emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword filters by dashboard name`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"uid":"u1","title":"Auth Dashboard","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
                ),
        )
        server.enqueue(MockResponse().setResponseCode(500))
        val results = client.searchKeyword(config(), "auth", sessionId = 1, dashboardFilters = listOf("nonexistent"))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword filters by uid`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"uid":"u1","title":"D","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"dashboard":{}}"""),
        )
        val results = client.searchKeyword(config(), "D", sessionId = 1, dashboardFilters = listOf("u1"))
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `searchKeyword filters out non-matching uid`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"uid":"u1","title":"D","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"dashboard":{}}"""),
        )
        val results = client.searchKeyword(config(), "D", sessionId = 1, dashboardFilters = listOf("u2"))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword with empty keyword lists all dashboards as hits`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"uid":"u1","title":"Dashboard","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"dashboard":{}}"""),
        )
        val results = client.searchKeyword(config(), "", sessionId = 1, dashboardFilters = emptyList())
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `searchKeyword skips dashboards where keyword not in title`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"uid":"u1","title":"Other","type":"dash-db","url":"/d/u1","folderTitle":"F"}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"dashboard":{}}"""),
        )
        val results = client.searchKeyword(config(), "missing", sessionId = 1, dashboardFilters = emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword handles dashboard with empty URL by using full base URL`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"uid":"u1","title":"Auth Dashboard","type":"dash-db","url":"","folderTitle":"F"}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"dashboard":{}}"""),
        )
        val results = client.searchKeyword(config(), "auth", sessionId = 1, dashboardFilters = emptyList())
        assertTrue(results.any { it.repoUrl.orEmpty().isNotEmpty() })
    }

    @Test
    fun `DashboardSearchItem default constructor covers all defaults`() {
        val item = GrafanaClient.DashboardSearchItem()
        assertEquals(0L, item.id)
        assertEquals("", item.uid)
        assertEquals("", item.title)
        assertEquals("", item.type)
        assertEquals("", item.url)
        assertEquals(null, item.folderTitle)
    }

    @Test
    fun `DashboardWrapper default constructor covers all defaults`() {
        val wrapper = GrafanaClient.DashboardWrapper()
        assertEquals(null, wrapper.dashboard)
        assertEquals(null, wrapper.meta)
    }

    @Test
    fun `DashboardMeta default constructor covers all defaults`() {
        val meta = GrafanaClient.DashboardMeta()
        assertEquals(null, meta.url)
        assertEquals(null, meta.folderTitle)
    }
}
