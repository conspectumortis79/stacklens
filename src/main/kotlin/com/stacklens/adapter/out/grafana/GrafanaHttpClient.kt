package com.stacklens.adapter.out.grafana

import com.stacklens.application.port.out.grafana.GrafanaProxyPort
import com.stacklens.application.port.out.grafana.GrafanaResponse
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.process.SleeperPort
import com.stacklens.domain.util.closingAuto
import com.stacklens.shared.utf8Reader
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Base64

class GrafanaHttpClient(
    private val i18n: I18nPort,
    private val sleeper: SleeperPort =
        com.stacklens.adapter.out.shared
            .ThreadSleepSleeper(),
    private val retryBackoffMs: Long = 2_000L,
) : GrafanaProxyPort {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    override fun basicAuthHeader(
        username: String,
        password: String,
    ): String = "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"

    override fun fetchGrafanaJson(
        targetUrl: String,
        username: String,
        password: String,
        apiToken: String,
    ): GrafanaResponse =
        try {
            val conn = openConnection(targetUrl)
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/json")
                when {
                    apiToken.isNotBlank() ->
                        conn.setRequestProperty("Authorization", "Bearer $apiToken")
                    username.isNotBlank() ->
                        conn.setRequestProperty("Authorization", basicAuthHeader(username, password))
                }
                val code = conn.responseCode
                val body = if (code < 400) readInputStreamBody(conn) else readErrorBody(conn, code)
                GrafanaResponse(code, body)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            GrafanaResponse(
                status = 500,
                body = """{"error":"${exceptionMessageSafe(e)}"}""",
            )
        }

    override fun proxyRequest(
        targetUrl: String,
        authHeader: String?,
        orgId: Long?,
    ): GrafanaResponse =
        try {
            val conn = openConnection(targetUrl)
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                if (authHeader != null) conn.setRequestProperty("Authorization", authHeader)
                if (orgId != null) conn.setRequestProperty("X-Grafana-Org-Id", orgId.toString())
                conn.setRequestProperty("Connection", "close")
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                val body = if (code < 400) readInputStreamBody(conn) else readErrorBody(conn, code)
                GrafanaResponse(code, body)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            GrafanaResponse(
                status = 500,
                body = """{"error":"${exceptionMessageSafe(e)}"}""",
            )
        }

    private fun openConnection(targetUrl: String): HttpURLConnection {
        val parsed = URI(targetUrl).toURL()
        if (parsed.host == "localhost") {
            val ipv6 =
                runCatching { InetAddress.getAllByName("localhost") }
                    .getOrNull()
                    ?.filterIsInstance<Inet6Address>()
                    ?.firstOrNull()
            if (ipv6 != null) {
                val ipv6Addr = ipv6.hostAddress
                val port = if (parsed.port != -1) parsed.port else parsed.defaultPort
                val conn =
                    URI(parsed.protocol, null, ipv6Addr, port, parsed.path, parsed.query, null)
                        .toURL()
                        .openConnection()
                return requireHttpConnection(conn)
            }
        }
        return requireHttpConnection(parsed.openConnection())
    }

    private fun requireHttpConnection(conn: java.net.URLConnection): HttpURLConnection {
        if (conn is HttpURLConnection) return conn
        throw java.io.IOException("Unexpected connection type for HTTP target: ${conn.javaClass.name}")
    }

    fun readInputStreamBody(conn: HttpURLConnection): String = conn.inputStream?.utf8Reader()?.use { it.readText() } ?: ""

    fun readErrorBody(
        conn: HttpURLConnection,
        fallbackCode: Int,
    ): String = conn.errorStream?.utf8Reader()?.closingAuto { it.readText() } ?: """{"error":"HTTP $fallbackCode"}"""

    fun exceptionMessageSafe(e: Exception): String = e.message?.replace("\"", "'") ?: tr("common.be_unknown_error")

    private fun sleepBeforeRetry() {
        if (retryBackoffMs > 0) sleeper.sleep(retryBackoffMs)
    }

    override fun fetchGrafanaJsonWithRetry(
        targetUrl: String,
        username: String,
        password: String,
        apiToken: String,
    ): GrafanaResponse {
        var response = fetchGrafanaJson(targetUrl, username, password, apiToken)
        for (attempt in 2..SERVER_ERROR_MAX_ATTEMPTS) {
            if (!isServerError(response.status)) return response
            sleepBeforeRetry()
            response = fetchGrafanaJson(targetUrl, username, password, apiToken)
        }
        return response
    }

    private fun isServerError(status: Int): Boolean = status in SERVER_ERROR_RANGE

    private companion object {
        const val SERVER_ERROR_MAX_ATTEMPTS = 3
        val SERVER_ERROR_RANGE = 500..599
    }
}
