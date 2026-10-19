package com.stacklens.adapter.out.grafana

import com.stacklens.domain.model.GrafanaConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

class GrafanaClientPanelQueryScanCoverageTest {
    private lateinit var server: MockWebServer

    private val client = GrafanaClient()

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

    private fun config(scanQueries: Boolean = true) =
        GrafanaConfig(
            name = "ops",
            baseUrl = base(),
            username = "reader",
            password = "secret",
            scanPanelQueries = scanQueries,
        )

    private fun item(uid: String) = GrafanaClient.DashboardSearchItem(uid = uid, title = "CPU board", url = "/d/$uid/cpu")

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body).setResponseCode(code))
    }

    private fun scan(
        cfg: GrafanaConfig = config(),
        keyword: String = "cpu",
    ) = client.scanDashboardPanels(webClient(), cfg, item("uid-a"), keyword, 9L)

    @Test
    fun `an erroring dashboard endpoint ends the scan without a result`() {
        enqueue("""{"message":"grafana is gone"}""", code = 500)

        assertTrue(scan().isEmpty())
    }

    @Test
    fun `a panel without title and type is reported from its matching query`() {
        enqueue(
            """
            {"dashboard":{"panels":[
              {"title":"","type":"","targets":[{"expr":"rate(cpu_seconds[5m])"}]}
            ]}}
            """.trimIndent(),
        )

        val results = scan()

        assertEquals(1, results.size)
        assertEquals("Panel (no title)", results.single().filePath)
        assertNull(results.single().functionName)
        assertTrue(results.single().codeSnippet!!.contains("cpu"))
    }

    @Test
    fun `query scanning can be switched off per configuration`() {
        enqueue(
            """
            {"dashboard":{"panels":[
              {"title":"other","type":"timeseries","targets":[{"expr":"rate(cpu_seconds[5m])"}]}
            ]}}
            """.trimIndent(),
        )

        assertTrue(scan(config(scanQueries = false)).isEmpty())
    }
}
