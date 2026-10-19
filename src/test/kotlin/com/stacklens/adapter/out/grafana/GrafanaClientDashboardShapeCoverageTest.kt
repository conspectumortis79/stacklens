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

class GrafanaClientDashboardShapeCoverageTest {
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

    private fun config() =
        GrafanaConfig(
            name = "ops",
            baseUrl = base(),
            username = "reader",
            password = "secret",
            scanPanelQueries = true,
        )

    private fun item() = GrafanaClient.DashboardSearchItem(uid = "uid-a", title = "CPU board", url = "/d/uid-a/cpu")

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body).setResponseCode(code))
    }

    private fun scan() = client.scanDashboardPanels(webClient(), config(), item(), "cpu", 4L)

    @Test
    fun `a dashboard answer without the dashboard field yields no panels`() {
        enqueue("""{"meta":{"type":"DashDB"}}""")

        assertTrue(scan().isEmpty())
    }

    @Test
    fun `an explicit null dashboard yields no panels too`() {
        enqueue("""{"dashboard":null}""")

        assertTrue(scan().isEmpty())
    }

    @Test
    fun `panels with a title and a type keep both values and unmatched queries are skipped`() {
        enqueue(
            """
            {"dashboard":{"panels":[
              {"title":"cpu usage","type":"graph","description":"other",
               "targets":[{"expr":"avg(load)"},{"expr":"rate(cpu_seconds[5m])"}]}
            ]}}
            """.trimIndent(),
        )

        val results = scan()

        assertEquals(2, results.size)
        assertTrue(results.all { it.filePath == "cpu usage" })
        assertTrue(results.all { it.functionName == "graph" })
        assertTrue(results.any { it.codeSnippet!!.contains("cpu_seconds") })
        assertTrue(results.any { it.codeSnippet!!.contains("Panel-Titel") })
    }

    @Test
    fun `a panel without targets is only reported through its own fields`() {
        enqueue("""{"dashboard":{"panels":[{"title":"cpu only","type":""}]}}""")

        val results = scan()

        assertEquals(1, results.size)
        assertNull(results.single().functionName)
        assertEquals("Panel-Titel: cpu only", results.single().codeSnippet)
    }
}
