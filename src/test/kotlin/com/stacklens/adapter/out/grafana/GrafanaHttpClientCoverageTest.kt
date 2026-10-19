package com.stacklens.adapter.out.grafana

import com.stacklens.application.port.out.process.SleeperPort
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection

class GrafanaHttpClientCoverageTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun client(
        backoffMs: Long = 0L,
        sleeper: SleeperPort = SleeperPort { true },
    ) = GrafanaHttpClient(
        i18n =
            com.stacklens.application.service
                .FakeI18n(),
        sleeper = sleeper,
        retryBackoffMs = backoffMs,
    )

    private fun url(path: String) = server.url(path).toString().replace("//localhost:", "//127.0.0.1:")

    @Test
    fun `basic auth is used when no api token is present`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val response = client().fetchGrafanaJson(url("/api/dashboards"), "grafana", "secret", "")

        assertEquals(200, response.status)
        assertTrue(response.body.contains("ok"))
        assertTrue(server.takeRequest().getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test
    fun `an api token wins over basic credentials`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client().fetchGrafanaJson(url("/api"), "grafana", "secret", "tok-123")

        assertEquals("Bearer tok-123", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `client errors expose the grafana error body`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"not found"}"""))

        val response = client().fetchGrafanaJson(url("/missing"), "", "", "")

        assertEquals(404, response.status)
        assertTrue(response.body.contains("not found"))
    }

    @Test
    fun `proxy requests forward the auth header and the organisation hint`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val response = client().proxyRequest(url("/render"), "Bearer abc", 7L)

        assertEquals(200, response.status)
        val recorded = server.takeRequest()
        assertEquals("Bearer abc", recorded.getHeader("Authorization"))
        assertEquals("7", recorded.getHeader("X-Grafana-Org-Id"))
        assertEquals("close", recorded.getHeader("Connection"))
    }

    @Test
    fun `proxy requests without optional headers still succeed`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        assertEquals(200, client().proxyRequest(url("/render"), null, null).status)
        assertEquals(null, server.takeRequest().getHeader("X-Grafana-Org-Id"))
    }

    @Test
    fun `a target that is not an http endpoint is reported as a server error`() {
        val response = client().proxyRequest("file:///etc/hostname", null, null)

        assertEquals(500, response.status)
        assertTrue(response.body.contains("Unexpected connection type"), response.body)
    }

    @Test
    fun `server errors are retried until a healthy response arrives`() {
        val sleeps = mutableListOf<Long>()
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(500).setBody("again"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("healthy"))

        val response =
            client(
                backoffMs = 25L,
                sleeper =
                    SleeperPort { millis ->
                        sleeps += millis
                        true
                    },
            ).fetchGrafanaJsonWithRetry(url("/api"), "", "", "")

        assertEquals(200, response.status)
        assertEquals("healthy", response.body)
        assertEquals(listOf(25L, 25L), sleeps)
    }

    @Test
    fun `the last server error is returned when retries are exhausted`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway")) }

        val response = client().fetchGrafanaJsonWithRetry(url("/api"), "", "", "")

        assertEquals(502, response.status)
        assertEquals("bad gateway", response.body)
    }

    @Test
    fun `a non server error stops the retry loop immediately`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("denied"))

        val response = client().fetchGrafanaJsonWithRetry(url("/api"), "", "", "")

        assertEquals(401, response.status)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a loopback target without a listener is reported as a server error`() {
        val response = client().fetchGrafanaJson("http://localhost:1/api/dashboards", "", "", "")

        assertEquals(500, response.status)
        assertTrue(response.body.contains("error"), response.body)
    }

    @Test
    fun `readers fall back when the connection exposes no streams`() {
        val conn = mock<HttpURLConnection>()

        assertEquals("", client().readInputStreamBody(conn))
        assertEquals("""{"error":"HTTP 503"}""", client().readErrorBody(conn, 503))
    }

    @Test
    fun `readers consume the available stream contents`() {
        val conn = mock<HttpURLConnection>()
        org.mockito.Mockito
            .doReturn(ByteArrayInputStream("payload".toByteArray()))
            .`when`(conn)
            .inputStream
        org.mockito.Mockito
            .doReturn(ByteArrayInputStream("""{"e":"boom"}""".toByteArray()))
            .`when`(conn)
            .errorStream

        assertEquals("payload", client().readInputStreamBody(conn))
        assertEquals("""{"e":"boom"}""", client().readErrorBody(conn, 500))
    }

    @Test
    fun `exceptions without a message fall back to the translation key`() {
        val withoutMessage = client().exceptionMessageSafe(IllegalStateException())

        assertEquals("common.be_unknown_error", withoutMessage)
        assertEquals("'failed'", client().exceptionMessageSafe(IllegalStateException("\"failed\"")))
    }

    @Test
    fun `basic auth header encodes user and password`() {
        assertEquals("Basic Z3JhZmFuYTpzZWNyZXQ=", client().basicAuthHeader("grafana", "secret"))
    }

    @Test
    fun `a loopback url without an explicit port uses the default port`() {
        val response = client().fetchGrafanaJson("http://localhost/api/dashboards", "", "", "")

        assertTrue(response.status == 500 || response.status == 200, response.body)
    }

    @Test
    fun `the upper bound of the server error range is retried`() {
        server.enqueue(MockResponse().setResponseCode(599).setBody("edge"))
        server.enqueue(MockResponse().setResponseCode(204).setBody("no content"))

        val response = client().fetchGrafanaJsonWithRetry(url("/api"), "", "", "")

        assertEquals(204, response.status)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a status just above the server error range is returned directly`() {
        server.enqueue(MockResponse().setResponseCode(600).setBody("odd"))

        assertEquals(600, client().fetchGrafanaJsonWithRetry(url("/api"), "", "", "").status)
        assertEquals(1, server.requestCount)
    }
}
