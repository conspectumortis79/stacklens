package com.stacklens.adapter.out.grafana

import com.stacklens.adapter.out.shared.ThreadSleepSleeper
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.HttpURLConnection

class GrafanaHttpClientTest {
    private fun client(
        i18n: I18nPort =
            com.stacklens.application.service
                .FakeI18n(),
    ): GrafanaHttpClient = GrafanaHttpClient(i18n = i18n, sleeper = ThreadSleepSleeper(), retryBackoffMs = 0L)

    @Test
    fun `tr uses i18n translation when provided`() {
        val i18n = mock<I18nPort>()
        whenever(i18n.t("common.be_unknown_error")).thenReturn("Unknown error")
        val out = client(i18n = i18n)
        val ex = RuntimeException()
        val result = out.exceptionMessageSafe(ex)
        assertEquals("Unknown error", result)
    }

    @Test
    fun `exceptionMessageSafe returns the message when it is not null`() {
        val i18n = mock<I18nPort>()
        whenever(i18n.t("common.be_unknown_error")).thenReturn("Unknown error")
        val out = client(i18n = i18n)
        val ex = RuntimeException("Boom!")
        val result = out.exceptionMessageSafe(ex)
        assertEquals("Boom!", result)
    }

    @Test
    fun `exceptionMessageSafe replaces double quotes with single quotes`() {
        val out = client()
        val ex = RuntimeException("""message with "double quotes"""")
        val result = out.exceptionMessageSafe(ex)
        assertTrue(result.contains("'double quotes'"))
    }

    @Test
    fun `basicAuthHeader encodes credentials with Base64`() {
        val out = client()
        val header = out.basicAuthHeader("user", "pass")
        assertTrue(header.startsWith("Basic "))
        val decoded =
            String(
                java.util.Base64
                    .getDecoder()
                    .decode(header.removePrefix("Basic ")),
                Charsets.UTF_8,
            )
        assertEquals("user:pass", decoded)
    }

    @Test
    fun `basicAuthHeader encodes empty credentials`() {
        val out = client()
        val header = out.basicAuthHeader("", "")
        assertTrue(header.startsWith("Basic "))
    }

    @Test
    fun `readInputStreamBody returns empty string when stream is null`() {
        val conn = mock<HttpURLConnection>()
        whenever(conn.inputStream).thenReturn(null)
        val out = client()
        val method = GrafanaHttpClient::class.java.getDeclaredMethod("readInputStreamBody", HttpURLConnection::class.java)
        method.isAccessible = true
        val result = method.invoke(out, conn) as String
        assertEquals("", result)
    }

    @Test
    fun `readErrorBody returns the fallback body when stream is null`() {
        val conn = mock<HttpURLConnection>()
        whenever(conn.errorStream).thenReturn(null)
        val out = client()
        val method = GrafanaHttpClient::class.java.getDeclaredMethod("readErrorBody", HttpURLConnection::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        val result = method.invoke(out, conn, 500) as String
        assertEquals("""{"error":"HTTP 500"}""", result)
    }
}
