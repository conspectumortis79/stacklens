package com.stacklens.adapter.out.grafana

import com.stacklens.domain.model.GrafanaConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GrafanaClientScanRowPanelsTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GrafanaClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GrafanaClient()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun gitlabConfig(): GrafanaConfig =
        GrafanaConfig(
            name = "g",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            password = "p",
        )

    @Test
    fun `searchKeyword scans panels inside rows when the dashboard has row panels`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """[{"id":1,"uid":"d1","title":"My Dash","uri":"db/d1","url":"/d/d1","slug":"d1","type":"dash-db","folderId":1,"folderUid":"f1","folderTitle":"My Folder","isStarred":false}]""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"meta":{"slug":"d1"},"dashboard":{"description":"","panels":[{"id":1,"title":"Row","type":"row","panels":[{"id":2,"title":"Nested Panel","type":"graph","description":"contains keyword"}]}]}}""",
                ),
        )
        val result: List<com.stacklens.domain.model.SearchResult> =
            client.searchKeyword(
                config = gitlabConfig(),
                keyword = "keyword",
                sessionId = 1L,
                dashboardFilters = emptyList(),
            )
        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "expected nested-panel result, got $result")
    }
}
