package com.stacklens.application.port.out.grafana

interface GrafanaDashboardMetricsPort {
    fun extractMetrics(body: String): List<Map<String, Any?>>
}
