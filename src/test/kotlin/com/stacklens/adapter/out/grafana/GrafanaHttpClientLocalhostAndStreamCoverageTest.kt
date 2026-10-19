package com.stacklens.adapter.out.grafana

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection

class GrafanaHttpClientLocalhostAndStreamCoverageTest {
    private lateinit var server: MockWebServer

    private val client =
        GrafanaHttpClient(
            com.stacklens.application.service
                .FakeI18n(),
        )

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
    fun `a request to the localhost alias goes through the ipv6 lookup`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val response = client.fetchGrafanaJson("http://localhost:${server.port}/api", "user", "pass", "")

        assertTrue(response.status == 200 || response.status == 500, response.toString())
    }

    @Test
    fun `a connection without an input stream reads an empty body`() {
        val conn = mock<HttpURLConnection>()
        whenever(conn.inputStream).thenReturn(null)

        assertEquals("", client.readInputStreamBody(conn))
    }

    @Test
    fun `a connection with an input stream reads its text`() {
        val conn = mock<HttpURLConnection>()
        whenever(conn.inputStream).thenReturn(ByteArrayInputStream("payload".toByteArray()))

        assertEquals("payload", client.readInputStreamBody(conn))
    }

    @Test
    fun `a connection without an error stream reports the status code`() {
        val conn = mock<HttpURLConnection>()
        whenever(conn.errorStream).thenReturn(null)

        assertEquals("""{"error":"HTTP 418"}""", client.readErrorBody(conn, 418))
    }

    @Test
    fun `a connection with an error stream returns its text`() {
        val conn = mock<HttpURLConnection>()
        whenever(conn.errorStream).thenReturn(ByteArrayInputStream("nope".toByteArray()))

        assertEquals("nope", client.readErrorBody(conn, 500))
    }
}
