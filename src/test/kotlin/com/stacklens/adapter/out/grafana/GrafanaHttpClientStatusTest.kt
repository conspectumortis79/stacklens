package com.stacklens.adapter.out.grafana

import com.stacklens.testsupport.FakeI18n
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GrafanaHttpClientStatusTest {
    private lateinit var server: MockWebServer

    private val client = GrafanaHttpClient(i18n = FakeI18n(), sleeper = { true }, retryBackoffMs = 0L)

    private fun url(path: String): String = server.url(path).toString().replace("//localhost:", "//127.0.0.1:")

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `an error status is read from the error stream and keeps its code`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("grafana is draining"))

        val response = client.proxyRequest(url("/api/dashboards/uid/x"), "Basic abc", 2L)

        assertEquals(503, response.status)
        assertEquals("grafana is draining", response.body)
    }

    @Test
    fun `a success status is read from the input stream`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"dashboard":{}}"""))

        val response = client.proxyRequest(url("/api/dashboards/uid/x"), null, null)

        assertEquals(200, response.status)
        assertTrue(response.body.contains("dashboard"))
    }

    @Test
    fun `the fetch helper retries a failing request and reports the last answer`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("first"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("second"))

        val response =
            client.fetchGrafanaJsonWithRetry(
                url("/api/dashboards/home"),
                "user",
                "pass",
                "",
            )

        assertEquals(200, response.status)
        assertEquals("second", response.body)
    }
}
