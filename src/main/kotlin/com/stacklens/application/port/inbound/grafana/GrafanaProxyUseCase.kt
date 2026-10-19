package com.stacklens.application.port.inbound.grafana

sealed interface GrafanaProxyOutcome {
    data object ConfigNotFound : GrafanaProxyOutcome

    data class Rejected(
        val messageKey: String? = null,
        val message: String = "",
    ) : GrafanaProxyOutcome

    data class Upstream(
        val status: Int,
        val body: String,
    ) : GrafanaProxyOutcome
}

sealed interface GrafanaMetricsOutcome {
    data object ConfigNotFound : GrafanaMetricsOutcome

    data class Rejected(
        val messageKey: String,
    ) : GrafanaMetricsOutcome

    data class Upstream(
        val status: Int,
        val body: String,
    ) : GrafanaMetricsOutcome

    data class Parsed(
        val metrics: List<Map<String, Any?>>,
    ) : GrafanaMetricsOutcome
}

interface GrafanaProxyUseCase {
    fun dashboardMetrics(
        configId: Long,
        dashboardUid: String,
    ): GrafanaMetricsOutcome

    fun proxyApi(
        configId: Long,
        requestPath: String,
        queryString: String?,
    ): GrafanaProxyOutcome

    fun queryDemoPrometheus(
        query: String,
        time: Long?,
        prometheusUrl: String,
    ): GrafanaProxyOutcome

    fun fetchDashboardJson(
        grafanaUrl: String,
        username: String,
        password: String,
        apiToken: String,
        configId: Long? = null,
    ): GrafanaProxyOutcome

    fun homeDashboard(configId: Long): GrafanaProxyOutcome

    fun demoDashboardJson(): String
}
