package com.stacklens.adapter.out.grafana

import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.testsupport.FlakyInputStream
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress

class GrafanaHttpClientLocalhostShapeCoverageTest {
    private val client = GrafanaHttpClient(i18n = mock<I18nPort>())

    private fun openConnection(url: String): HttpURLConnection = PrivateInvocation.invoke(client, "openConnection", url) as HttpURLConnection

    @Test
    fun `a failing localhost lookup keeps the plain url`() {
        Mockito.mockStatic(InetAddress::class.java).use { lookup ->
            lookup
                .`when`<Array<InetAddress>>({ InetAddress.getAllByName("localhost") })
                .thenThrow(IllegalStateException("resolver is down"))

            val connection = openConnection("http://localhost:1/dashboard")

            assertEquals("localhost", connection.url.host)
        }
    }

    @Test
    fun `a localhost without any ipv6 record keeps the plain url`() {
        val ipv4 = Inet4Address.getByAddress(byteArrayOf(127, 0, 0, 1))
        Mockito.mockStatic(InetAddress::class.java).use { lookup ->
            lookup
                .`when`<Array<InetAddress>>({ InetAddress.getAllByName("localhost") })
                .thenReturn(arrayOf(ipv4))

            val connection = openConnection("http://localhost:1/dashboard")

            assertEquals("localhost", connection.url.host)
        }
    }

    @Test
    fun `an ipv6 rewrite keeps the query string out of the path`() {
        val connection = openConnection("http://localhost:1/api/v1/query?query=up")

        assertEquals("/api/v1/query", connection.url.path)
        assertEquals("query=up", connection.url.query)
    }

    @Test
    fun `a body reader survives a missing stream`() {
        val connection = mock<HttpURLConnection>()

        assertEquals("", client.readInputStreamBody(connection))
        assertEquals("""{"error":"HTTP 503"}""", client.readErrorBody(connection, 503))
    }

    @Test
    fun `a body reader survives a stream that breaks while reading`() {
        val connection = mock<HttpURLConnection>()
        whenever(connection.inputStream).thenReturn(FlakyInputStream(failOnRead = true))
        whenever(connection.errorStream).thenReturn(ByteArrayInputStream("{}".toByteArray()))

        assertThrows(java.io.IOException::class.java) { client.readInputStreamBody(connection) }
        assertEquals("{}", client.readErrorBody(connection, 500))
    }

    @Test
    fun `a non http target is refused`() {
        val failure =
            assertThrows(java.io.IOException::class.java) {
                PrivateInvocation.invoke(client, "requireHttpConnection", mock<java.net.URLConnection>())
            }

        assertTrue(failure.message!!.contains("Unexpected connection type"), failure.message)
    }
}
