package com.stacklens.adapter.out.grafana

import com.stacklens.adapter.out.shared.ThreadSleepSleeper
import com.stacklens.application.port.out.grafana.GrafanaProxyPort
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class GrafanaHttpClientRetryTest {
    private fun client(retryBackoffMs: Long = 0L) =
        GrafanaHttpClient(
            i18n =
                com.stacklens.application.service
                    .FakeI18n(),
            sleeper = ThreadSleepSleeper(),
            retryBackoffMs = retryBackoffMs,
        )

    private class FlakyServer(
        responses: List<Int>,
    ) {
        private val queue = ArrayDeque(responses)
        var requests = 0
            private set
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        init {
            server.createContext("/") { exchange ->
                requests++
                val status = queue.removeFirstOrNull() ?: 500
                val body = """{"status":$status}""".toByteArray()
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
        }

        val url: String get() = "http://127.0.0.1:${server.address.port}/api/dashboards/home"

        fun stop() = server.stop(0)
    }

    @Test
    fun `returns the first response when grafana answers successfully`() {
        val server = FlakyServer(listOf(200))
        try {
            val response = client().fetchGrafanaJsonWithRetry(server.url, "u", "p", "")
            assertEquals(200, response.status)
            assertEquals(1, server.requests)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `returns a client error without retrying`() {
        val server = FlakyServer(listOf(404))
        try {
            val response = client().fetchGrafanaJsonWithRetry(server.url, "u", "p", "")
            assertEquals(404, response.status)
            assertEquals(1, server.requests)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `retries server errors until grafana recovers`() {
        val server = FlakyServer(listOf(503, 200))
        try {
            val response = client().fetchGrafanaJsonWithRetry(server.url, "u", "p", "")
            assertEquals(200, response.status)
            assertEquals(2, server.requests)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `gives up after the last allowed attempt`() {
        val server = FlakyServer(listOf(500, 500, 500))
        try {
            val response = client().fetchGrafanaJsonWithRetry(server.url, "u", "p", "")
            assertEquals(500, response.status)
            assertEquals(3, server.requests)
            assertTrue(response.body.contains("500"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `the port exposes the retrying fetch`() {
        val port: GrafanaProxyPort = client()
        assertEquals(500, port.fetchGrafanaJson("http://127.0.0.1:1/nope", "", "", "").status)
    }
}
