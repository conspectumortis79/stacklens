package com.stacklens.adapter.inbound.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.inbound.grafana.GrafanaAnalysisUseCase
import com.stacklens.application.port.inbound.grafana.GrafanaMetricsOutcome
import com.stacklens.application.port.inbound.grafana.GrafanaProxyOutcome
import com.stacklens.application.port.inbound.grafana.GrafanaProxyUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/grafana-analyse")
open class GrafanaAnalyseApiController(
    private val grafanaAnalysisUseCase: GrafanaAnalysisUseCase,
    private val grafanaProxyUseCase: GrafanaProxyUseCase,
    private val objectMapper: ObjectMapper,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    internal fun exceptionMessage(e: Exception): String = e.message ?: ""

    private fun rejected(
        messageKey: String?,
        message: String,
    ): String = messageKey?.let { tr(it) } ?: message

    @GetMapping("/metrics/{configId}")
    fun getDashboardMetrics(
        @PathVariable configId: Long,
        @RequestParam dashboardUid: String,
    ): ResponseEntity<*> =
        when (val outcome = grafanaProxyUseCase.dashboardMetrics(configId, dashboardUid)) {
            is GrafanaMetricsOutcome.ConfigNotFound -> ResponseEntity.notFound().build<Any>()
            is GrafanaMetricsOutcome.Rejected ->
                ResponseEntity
                    .badRequest()
                    .body(mapOf("error" to tr(outcome.messageKey)))
            is GrafanaMetricsOutcome.Upstream -> ResponseEntity.status(outcome.status).body(outcome.body)
            is GrafanaMetricsOutcome.Parsed -> ResponseEntity.ok(outcome.metrics)
        }

    @GetMapping("/proxy/{configId}/**")
    fun proxyGrafanaApi(
        @PathVariable configId: Long,
        request: HttpServletRequest,
    ): ResponseEntity<String> =
        when (val outcome = grafanaProxyUseCase.proxyApi(configId, request.requestURI, request.queryString)) {
            is GrafanaProxyOutcome.ConfigNotFound -> ResponseEntity.notFound().build()
            is GrafanaProxyOutcome.Rejected ->
                jsonErrorBody(rejected(outcome.messageKey, outcome.message))
                    .let { ResponseEntity.badRequest().body(it) }
            is GrafanaProxyOutcome.Upstream -> ResponseEntity.status(outcome.status).body(outcome.body)
        }

    @GetMapping("/demo-prometheus/query")
    fun demoprometheusQuery(
        @RequestParam query: String,
        @RequestParam(required = false) time: Long?,
        @RequestParam(required = false, defaultValue = "http://localhost:9090") prometheusUrl: String,
    ): ResponseEntity<String> =
        when (val outcome = grafanaProxyUseCase.queryDemoPrometheus(query, time, prometheusUrl)) {
            is GrafanaProxyOutcome.ConfigNotFound -> ResponseEntity.notFound().build()
            is GrafanaProxyOutcome.Rejected ->
                ResponseEntity
                    .badRequest()
                    .body(jsonErrorBody(rejected(outcome.messageKey, outcome.message)))
            is GrafanaProxyOutcome.Upstream -> ResponseEntity.status(outcome.status).body(outcome.body)
        }

    data class GenerateDummyDataRequest(
        val dashboardJson: String = "",
    )

    @PostMapping("/generate-dummy-data")
    fun generateDummyData(
        @RequestBody req: GenerateDummyDataRequest,
    ): ResponseEntity<*> {
        if (req.dashboardJson.isBlank()) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to tr("grafana_analysis.be_dashboard_json_required")),
            )
        }
        try {
            objectMapper.readTree(req.dashboardJson)
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to tr("grafana_analysis.be_dashboard_json_invalid", exceptionMessage(e))),
            )
        }
        return try {
            ResponseEntity.ok(grafanaAnalysisUseCase.generateDummyData(req.dashboardJson))
        } catch (
            e: Exception,
        ) {
            ResponseEntity.internalServerError().body(
                mapOf("error" to tr("grafana_analysis.be_ai_generation_failed", e.message ?: "")),
            )
        }
    }

    data class RebuildDashboardRequest(
        val dashboardJson: String = "",
        val instructions: String = "",
    )

    @PostMapping("/rebuild-dashboard")
    fun rebuildDashboard(
        @RequestBody req: RebuildDashboardRequest,
    ): ResponseEntity<*> {
        if (req.dashboardJson.isBlank()) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to tr("grafana_analysis.be_dashboard_json_required")),
            )
        }
        if (req.instructions.isBlank()) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to tr("grafana_analysis.be_instructions_required")),
            )
        }
        try {
            objectMapper.readTree(req.dashboardJson)
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to tr("grafana_analysis.be_dashboard_json_invalid", exceptionMessage(e))),
            )
        }
        return try {
            ResponseEntity.ok(grafanaAnalysisUseCase.rebuildDashboard(req.dashboardJson, req.instructions))
        } catch (
            e: Exception,
        ) {
            ResponseEntity.internalServerError().body(
                mapOf("error" to tr("grafana_analysis.be_ai_rebuild_failed", e.message ?: "")),
            )
        }
    }

    data class FetchDashboardJsonRequest(
        val grafanaUrl: String = "",
        val username: String = "",
        val password: String = "",
        val apiToken: String = "",
        val configId: Long? = null,
    )

    @PostMapping("/fetch-dashboard-json")
    fun fetchDashboardJson(
        @RequestBody req: FetchDashboardJsonRequest,
    ): ResponseEntity<String> =
        when (
            val outcome =
                grafanaProxyUseCase.fetchDashboardJson(
                    req.grafanaUrl,
                    req.username,
                    req.password,
                    req.apiToken,
                    req.configId,
                )
        ) {
            is GrafanaProxyOutcome.ConfigNotFound -> ResponseEntity.notFound().build()
            is GrafanaProxyOutcome.Rejected ->
                ResponseEntity
                    .badRequest()
                    .body(jsonErrorBody(rejected(outcome.messageKey, outcome.message)))
            is GrafanaProxyOutcome.Upstream -> ResponseEntity.status(outcome.status).body(outcome.body)
        }

    @GetMapping("/home-dashboard/{configId}")
    fun homeDashboard(
        @PathVariable configId: Long,
    ): ResponseEntity<String> =
        when (val outcome = grafanaProxyUseCase.homeDashboard(configId)) {
            is GrafanaProxyOutcome.ConfigNotFound -> ResponseEntity.notFound().build()
            is GrafanaProxyOutcome.Rejected ->
                ResponseEntity
                    .badRequest()
                    .body(jsonErrorBody(rejected(outcome.messageKey, outcome.message)))
            is GrafanaProxyOutcome.Upstream -> ResponseEntity.status(outcome.status).body(outcome.body)
        }

    @GetMapping("/demo-dashboard-json")
    fun demoDashboardJson(): ResponseEntity<String> =
        ResponseEntity
            .ok()
            .header("Content-Type", "application/json")
            .body(grafanaProxyUseCase.demoDashboardJson())

    private fun jsonErrorBody(message: String): String = """{"error":"$message"}"""
}
