package com.stacklens.adapter.out.grafana

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.grafana.GrafanaDashboardMetricsPort

class DashboardMetricsParser(
    private val objectMapper: ObjectMapper,
) : GrafanaDashboardMetricsPort {
    override fun extractMetrics(body: String): List<Map<String, Any?>> = extractMetricsList(body)

    fun extractMetricsList(dashboardJson: String): List<Map<String, Any?>> {
        val dashboardNode = objectMapper.readTree(dashboardJson)
        val panels = dashboardNode.path("dashboard").path("panels")
        val metrics = mutableListOf<Map<String, Any?>>()
        panels.forEach { panel ->
            val panelTitle = panel.path("title").asText("")
            val panelType = panel.path("type").asText("")
            val panelId = panel.path("id").asInt()
            panel.path("targets").forEach { target ->
                val datasourceType = resolveDatasourceType(target)
                val entry = extractMetricEntry(target, datasourceType, panelTitle, panelType, panelId)
                if (entry != null) metrics.add(entry)
            }
            panel.path("panels").forEach { subPanel ->
                val subTitle = subPanel.path("title").asText(panelTitle)
                val subType = subPanel.path("type").asText("")
                val subId = subPanel.path("id").asInt()
                subPanel.path("targets").forEach { target ->
                    val datasourceType = resolveDatasourceType(target)
                    val entry = extractMetricEntry(target, datasourceType, subTitle, subType, subId)
                    if (entry != null) metrics.add(entry)
                }
            }
        }
        return metrics
    }

    fun resolveDatasourceType(target: JsonNode): String =
        target.path("datasource").path("type").asText(
            target.path("datasource").asText(""),
        )

    fun extractMetricEntry(
        target: JsonNode,
        datasourceType: String,
        panelTitle: String,
        panelType: String,
        panelId: Int,
    ): Map<String, Any?>? {
        val refId = target.path("refId").asText("")
        val legendFormat = target.path("legendFormat").asText("")
        val measurement = target.path("measurement").asText("")
        val query = target.path("query").asText(target.path("rawQuery").asText(target.path("rawSql").asText("")))
        val expr = target.path("expr").asText("")
        val metricName = target.path("target").asText(target.path("metricName").asText(""))
        val metric =
            when {
                measurement.isNotBlank() -> measurement
                expr.isNotBlank() -> expr
                metricName.isNotBlank() -> metricName
                query.isNotBlank() -> query.lines().first { it.isNotBlank() }.trim()
                else -> return null
            }
        return mapOf(
            "metric" to metric,
            "query" to (expr.ifBlank { query.ifBlank { metricName } }).ifBlank { null },
            "datasourceType" to datasourceType.ifBlank { "unknown" },
            "refId" to refId.ifBlank { null },
            "legendFormat" to legendFormat.ifBlank { null },
            "panelTitle" to panelTitle,
            "panelType" to panelType,
            "panelId" to panelId,
        )
    }
}
