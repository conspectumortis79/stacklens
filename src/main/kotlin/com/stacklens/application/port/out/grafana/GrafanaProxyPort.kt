package com.stacklens.application.port.out.grafana

data class GrafanaResponse(
    val status: Int,
    val body: String,
)

interface GrafanaProxyPort {
    fun basicAuthHeader(
        username: String,
        password: String,
    ): String

    fun fetchGrafanaJson(
        targetUrl: String,
        username: String,
        password: String,
        apiToken: String,
    ): GrafanaResponse

    fun proxyRequest(
        targetUrl: String,
        authHeader: String?,
        orgId: Long?,
    ): GrafanaResponse

    fun fetchGrafanaJsonWithRetry(
        targetUrl: String,
        username: String,
        password: String,
        apiToken: String,
    ): GrafanaResponse
}
