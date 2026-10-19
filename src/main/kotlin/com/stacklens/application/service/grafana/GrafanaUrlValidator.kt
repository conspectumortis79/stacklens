package com.stacklens.application.service.grafana

import java.net.URI
import java.net.URISyntaxException

object GrafanaUrlValidator {
    sealed interface PrometheusUrlResult {
        data class Ok(
            val uri: URI,
        ) : PrometheusUrlResult

        data class Err(
            val reason: PrometheusUrlError,
        ) : PrometheusUrlResult
    }

    fun parsePrometheusQueryUrl(prometheusUrl: String): PrometheusUrlResult {
        val u =
            try {
                URI(prometheusUrl)
            } catch (_: URISyntaxException) {
                return PrometheusUrlResult.Err(PrometheusUrlError.MALFORMED)
            }
        if (u.scheme?.lowercase() !in HTTP_SCHEMES) {
            return PrometheusUrlResult.Err(PrometheusUrlError.NON_HTTP_SCHEME)
        }
        val host = u.host?.lowercase()
        if (host == null || host !in LOOPBACK_HOSTS) {
            return PrometheusUrlResult.Err(PrometheusUrlError.NON_LOOPBACK_HOST)
        }
        return PrometheusUrlResult.Ok(u)
    }

    sealed interface HttpUrlResult {
        data class Ok(
            val uri: URI,
        ) : HttpUrlResult

        data class Err(
            val reason: HttpUrlError,
        ) : HttpUrlResult
    }

    fun parseHttpUrl(url: String): HttpUrlResult {
        val parsedUri =
            try {
                URI(url.trim())
            } catch (_: URISyntaxException) {
                return HttpUrlResult.Err(HttpUrlError.MALFORMED)
            }
        if (parsedUri.scheme?.lowercase() !in HTTP_SCHEMES) {
            return HttpUrlResult.Err(HttpUrlError.NON_HTTP_SCHEME)
        }
        return HttpUrlResult.Ok(parsedUri)
    }

    fun baseUrl(uri: URI): String {
        val port = uri.port
        return "${uri.scheme}://${uri.host}${if (port != -1) ":$port" else ""}"
    }

    enum class PrometheusUrlError {
        MALFORMED,
        NON_HTTP_SCHEME,
        NON_LOOPBACK_HOST,
    }

    enum class HttpUrlError {
        MALFORMED,
        NON_HTTP_SCHEME,
    }

    private val HTTP_SCHEMES = listOf("http", "https")
    private val LOOPBACK_HOSTS = listOf("localhost", "127.0.0.1", "[::1]")
}
