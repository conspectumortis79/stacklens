package com.stacklens.adapter.out.grafana

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.GrafanaConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

class GrafanaClientBranchCoverageTest {
    private lateinit var server: MockWebServer

    private val client = GrafanaClient()

    private val json = ObjectMapper()

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun base(): String = server.url("").toString().removeSuffix("/")

    private fun webClient(): WebClient =
        WebClient
            .builder()
            .baseUrl(base())
            .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(Duration.ofSeconds(5))))
            .build()

    private fun config(): GrafanaConfig =
        GrafanaConfig(
            name = "ops",
            baseUrl = base(),
            username = "reader",
            password = "secret",
        )

    private fun item(
        uid: String,
        title: String,
    ): GrafanaClient.DashboardSearchItem = GrafanaClient.DashboardSearchItem(uid = uid, title = title, url = "/d/$uid/$title")

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body).setResponseCode(code))
    }

    private fun searchBody(entries: List<Pair<String, String>>): String =
        entries.joinToString(prefix = "[", postfix = "]") { (uid, title) -> """{"uid":"$uid","title":"$title","url":"/d/$uid","type":"dash-db"}""" }

    @Test
    fun `dashboard search answers an empty list for empty and failing responses`() {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(emptyList<GrafanaClient.DashboardSearchItem>(), client.searchDashboards(webClient(), "cpu"))

        enqueue("""{"message":"grafana unavailable"}""", code = 500)
        assertEquals(emptyList<GrafanaClient.DashboardSearchItem>(), client.searchDashboards(webClient(), "cpu"))

        enqueue(searchBody(listOf("uid-a" to "CPU load")))
        assertEquals(listOf("uid-a"), client.searchDashboards(webClient(), "").map { it.uid })
    }

    @Test
    fun `panel scanning stops for missing dashboards`() {
        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(client.scanDashboardPanels(webClient(), config(), item("uid-a", "CPU"), "cpu", 1L).isEmpty())

        enqueue("""{"dashboard":null}""")
        assertTrue(client.scanDashboardPanels(webClient(), config(), item("uid-a", "CPU"), "cpu", 1L).isEmpty())
    }

    @Test
    fun `panel scanning walks descriptions rows and plain panels`() {
        enqueue(
            """
            {"dashboard":{
              "description":"Dashboard about cpu usage",
              "panels":[
                {"title":"cpu load","type":"graph","description":"cpu description","panels":[{"title":"row child cpu","type":"table"}]},
                {"title":"memory","type":"text"}
              ]
            }}
            """.trimIndent(),
        )
        val results = client.scanDashboardPanels(webClient(), config(), item("uid-a", "CPU"), "cpu", 1L)
        assertTrue(results.size >= 3)
        assertTrue(results.all { it.sourceType.name == "GRAFANA" })
        assertTrue(results.any { it.codeSnippet?.contains("Beschreibung") == true })
    }

    @Test
    fun `keyword search normalises filters and scans every dashboard`() {
        enqueue(searchBody(listOf("uid-a" to "CPU overview")))
        enqueue(searchBody(listOf("uid-a" to "CPU overview", "uid-b" to "Disk overview")))
        enqueue("""{"dashboard":{"description":"mentions CPU"}}""")

        val results = client.searchKeyword(config(), "cpu", 3L, listOf("  ", "uid-a"))
        assertTrue(results.any { it.repoName == "CPU overview" })
        assertTrue(results.none { it.repoName == "Disk overview" })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `keyword search skips the full listing for large result sets`() {
        val many = (1..200).map { index -> "uid$index" to "CPU dashboard $index" }
        enqueue(searchBody(many))
        repeat(100) { server.enqueue(MockResponse().setResponseCode(200)) }

        val results = client.searchKeyword(config(), "cpu", 4L, emptyList())
        assertEquals(200, results.count { it.resultType?.name == "GRAFANA_DASHBOARD" })
    }

    @Test
    fun `dashboard filters match by url, title, uid and folder path`() {
        val dash = item("beta", "Beta Board")
        assertTrue(client.matchesDashboardFilter(dash, emptyList()))
        assertTrue(client.matchesDashboardFilter(dash, listOf("/d/beta")))
        assertTrue(client.matchesDashboardFilter(dash, listOf("beta board")))
        assertTrue(client.matchesDashboardFilter(dash, listOf("beta")))
        assertTrue(client.matchesDashboardFilter(dash, listOf("x/beta/")))
        assertTrue(client.matchesDashboardFilter(dash, listOf("/d/x/beta/y")))
        assertEquals(false, client.matchesDashboardFilter(dash, listOf("gamma")))
    }

    @Test
    fun `result keys are stable for duplicate suppression`() {
        val first =
            com.stacklens.domain.model.SearchResult(
                sessionId = 1L,
                keyword = "cpu",
                sourceType = com.stacklens.domain.enums.SourceType.GRAFANA,
                resultType = com.stacklens.domain.enums.ResultType.GRAFANA_DASHBOARD,
                configName = "ops",
                repoName = "board",
                repoUrl = "https://grafana/d/1",
                filePath = null,
                codeSnippet = "snippet",
                createdAt = java.time.LocalDateTime.now(),
            )
        assertEquals("board|null|snippet", client.resultKey(first))
    }
}
