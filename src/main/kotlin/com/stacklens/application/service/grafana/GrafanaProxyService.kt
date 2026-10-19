package com.stacklens.application.service.grafana

import com.stacklens.application.port.inbound.grafana.GrafanaMetricsOutcome
import com.stacklens.application.port.inbound.grafana.GrafanaProxyOutcome
import com.stacklens.application.port.inbound.grafana.GrafanaProxyUseCase
import com.stacklens.application.port.out.grafana.GrafanaConfigInfo
import com.stacklens.application.port.out.grafana.GrafanaConfigLookupPort
import com.stacklens.application.port.out.grafana.GrafanaDashboardMetricsPort
import com.stacklens.application.port.out.grafana.GrafanaDemoDashboardPort
import com.stacklens.application.port.out.grafana.GrafanaProxyPort
import com.stacklens.application.stereotype.ApplicationService
import java.net.URLEncoder

@ApplicationService
class GrafanaProxyService(
    private val grafanaAnalysisUseCase: GrafanaConfigLookupPort,
    private val proxyPort: GrafanaProxyPort,
    private val metricsParser: GrafanaDashboardMetricsPort,
    private val demoDashboard: GrafanaDemoDashboardPort,
) : GrafanaProxyUseCase {
    override fun dashboardMetrics(
        configId: Long,
        dashboardUid: String,
    ): GrafanaMetricsOutcome {
        val config = grafanaAnalysisUseCase.findConfig(configId) ?: return GrafanaMetricsOutcome.ConfigNotFound
        if (dashboardUid.isBlank()) return GrafanaMetricsOutcome.Rejected(messageKey = KEY_DASHBOARD_UID_EMPTY)
        val response = proxyPort.proxyRequest(dashboardUrl(config, dashboardUid), authHeader(config), config.orgId)
        if (response.status !in SUCCESS_RANGE) return GrafanaMetricsOutcome.Upstream(response.status, response.body)
        return GrafanaMetricsOutcome.Parsed(metricsParser.extractMetrics(response.body))
    }

    override fun proxyApi(
        configId: Long,
        requestPath: String,
        queryString: String?,
    ): GrafanaProxyOutcome {
        val config = grafanaAnalysisUseCase.findConfig(configId) ?: return GrafanaProxyOutcome.ConfigNotFound
        val suffix = requestPath.removePrefix(PROXY_ROUTE_PREFIX + configId)
        if (!suffix.startsWith("/api/")) return GrafanaProxyOutcome.Rejected(messageKey = KEY_ONLY_API_PATHS)
        if (suffix.split("/").any { it == ".." || it == "." }) {
            return GrafanaProxyOutcome.Rejected(message = INVALID_PATH_CHARACTERS)
        }
        val url = config.baseUrl.trimEnd('/') + suffix + (queryString?.let { "?$it" } ?: "")
        val response = proxyPort.proxyRequest(url, authHeader(config), config.orgId)
        return GrafanaProxyOutcome.Upstream(response.status, response.body)
    }

    override fun queryDemoPrometheus(
        query: String,
        time: Long?,
        prometheusUrl: String,
    ): GrafanaProxyOutcome {
        val parsed =
            when (val result = GrafanaUrlValidator.parsePrometheusQueryUrl(prometheusUrl)) {
                is GrafanaUrlValidator.PrometheusUrlResult.Ok -> result.uri.toString()
                is GrafanaUrlValidator.PrometheusUrlResult.Err ->
                    return when (result.reason) {
                        GrafanaUrlValidator.PrometheusUrlError.MALFORMED -> GrafanaProxyOutcome.Rejected(message = INVALID_PROMETHEUS_URL)
                        GrafanaUrlValidator.PrometheusUrlError.NON_HTTP_SCHEME -> GrafanaProxyOutcome.Rejected(messageKey = KEY_ONLY_HTTP_URLS)
                        GrafanaUrlValidator.PrometheusUrlError.NON_LOOPBACK_HOST -> GrafanaProxyOutcome.Rejected(messageKey = KEY_ONLY_LOCALHOST)
                    }
            }
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val timeParam = time?.let { "&time=$it" } ?: ""
        val response = proxyPort.proxyRequest("${parsed.trimEnd('/')}/api/v1/query?query=$encodedQuery$timeParam", null, null)
        return GrafanaProxyOutcome.Upstream(response.status, response.body)
    }

    override fun fetchDashboardJson(
        grafanaUrl: String,
        username: String,
        password: String,
        apiToken: String,
        configId: Long?,
    ): GrafanaProxyOutcome {
        if (grafanaUrl.isBlank()) return GrafanaProxyOutcome.Rejected(messageKey = KEY_ENTER_GRAFANA_URL)
        val stored =
            if (configId != null) {
                grafanaAnalysisUseCase.findConfig(configId) ?: return GrafanaProxyOutcome.ConfigNotFound
            } else {
                null
            }
        val effectiveUsername = username.ifBlank { stored?.username ?: "" }
        val effectivePassword = password.ifBlank { stored?.password ?: "" }
        val effectiveApiToken = apiToken.ifBlank { stored?.apiToken ?: "" }
        val parsed =
            when (val result = GrafanaUrlValidator.parseHttpUrl(grafanaUrl)) {
                is GrafanaUrlValidator.HttpUrlResult.Ok -> result.uri
                is GrafanaUrlValidator.HttpUrlResult.Err ->
                    return when (result.reason) {
                        GrafanaUrlValidator.HttpUrlError.MALFORMED -> GrafanaProxyOutcome.Rejected(message = INVALID_URL)
                        GrafanaUrlValidator.HttpUrlError.NON_HTTP_SCHEME -> GrafanaProxyOutcome.Rejected(messageKey = KEY_ONLY_HTTP_URLS)
                    }
            }
        val uidMatch = DASHBOARD_UID_REGEX.find(parsed.path.orEmpty())
        val apiPath = uidMatch?.groupValues?.get(1)?.let { "/api/dashboards/uid/$it" } ?: "/api/dashboards/home"
        val response =
            proxyPort.fetchGrafanaJson(
                GrafanaUrlValidator.baseUrl(parsed) + apiPath,
                effectiveUsername,
                effectivePassword,
                effectiveApiToken,
            )
        return GrafanaProxyOutcome.Upstream(response.status, response.body)
    }

    override fun homeDashboard(configId: Long): GrafanaProxyOutcome {
        val config = grafanaAnalysisUseCase.findConfig(configId) ?: return GrafanaProxyOutcome.ConfigNotFound
        val baseUrl = config.baseUrl.trimEnd('/')
        val home =
            proxyPort.fetchGrafanaJsonWithRetry(
                "$baseUrl/api/dashboards/home",
                config.username,
                config.password,
                config.apiToken,
            )
        val redirectUrl =
            if (home.status in SUCCESS_RANGE) {
                REDIRECT_UID_REGEX
                    .find(home.body)
                    ?.groupValues
                    ?.get(1)
                    ?.let { "$baseUrl/api/dashboards/uid/$it" }
            } else {
                null
            }
        val response =
            redirectUrl
                ?.let { proxyPort.fetchGrafanaJson(it, config.username, config.password, config.apiToken) }
                ?: home
        return GrafanaProxyOutcome.Upstream(response.status, response.body)
    }

    override fun demoDashboardJson(): String = demoDashboard.demoDashboardJson()

    private fun dashboardUrl(
        config: GrafanaConfigInfo,
        dashboardUid: String,
    ): String = "${config.baseUrl.trimEnd('/')}/api/dashboards/uid/$dashboardUid"

    private fun authHeader(config: GrafanaConfigInfo): String = proxyPort.basicAuthHeader(config.username, config.password)

    companion object {
        const val PROXY_ROUTE_PREFIX: String = "/api/grafana-analyse/proxy/"
        const val KEY_DASHBOARD_UID_EMPTY: String = "grafana_analysis.be_dashboard_uid_empty"
        const val KEY_ONLY_API_PATHS: String = "grafana_analysis.be_only_api_paths"
        const val KEY_ONLY_HTTP_URLS: String = "grafana_analysis.be_only_http_urls"
        const val KEY_ONLY_LOCALHOST: String = "grafana_analysis.be_only_localhost"
        const val KEY_ENTER_GRAFANA_URL: String = "grafana_analysis.be_enter_grafana_url"
        const val INVALID_PATH_CHARACTERS: String = "Invalid path characters"
        const val INVALID_PROMETHEUS_URL: String = "Invalid Prometheus URL"
        const val INVALID_URL: String = "Invalid URL"

        private val SUCCESS_RANGE: IntRange = 200..299
        private val DASHBOARD_UID_REGEX = Regex("/d/([^/?#]+)")
        private val REDIRECT_UID_REGEX = Regex(""""redirectUri"\s*:\s*"/d/([^/"]+)""")
    }
}
