
package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.analysis.StaleAnalysisKind
import com.stacklens.application.port.inbound.analysis.StaleAnalysisStatusUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.sql.SqlAnalysisRecordLifecycleUseCase
import com.stacklens.application.port.inbound.sql.SqlAnalysisUseCase
import com.stacklens.application.port.inbound.sql.SqlHistoryUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sql-analyse")
class SqlAnalysisApiController(
    private val sqlAnalysisService: SqlAnalysisUseCase,
    private val sqlHistoryService: SqlHistoryUseCase,
    private val sqlRecordLifecycle: SqlAnalysisRecordLifecycleUseCase,
    private val i18n: I18nUseCase,
    private val staleAnalysis: StaleAnalysisStatusUseCase,
) {
    data class AnalyzeRequest(
        val sql: String,
        val ddl: String? = null,
    )

    data class GenerateDataRequest(
        val sql: String,
        val ddl: String? = null,
    )

    data class GenerateQueryRequest(
        val ddl: String,
        val seedData: String? = null,
        val goal: String,
    )

    data class RunTestRequest(
        val sql: String,
        val testData: String,
    )

    @PostMapping("/analyze")
    fun analyze(
        @RequestBody req: AnalyzeRequest,
    ): ResponseEntity<Any> {
        if (req.sql.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("sql_analysis.be_sql_empty")))
        }
        return try {
            ResponseEntity.ok(sqlAnalysisService.analyzeQuery(req.sql, req.ddl))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: i18n.t("common.be_unknown_error"))))
        }
    }

    @PostMapping("/generate-data")
    fun generateData(
        @RequestBody req: GenerateDataRequest,
    ): ResponseEntity<Any> {
        if (req.sql.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("sql_analysis.be_sql_empty")))
        }
        return try {
            ResponseEntity.ok(sqlAnalysisService.generateSampleData(req.sql, req.ddl))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: i18n.t("common.be_unknown_error"))))
        }
    }

    @PostMapping("/analyze-combined")
    fun analyzeCombined(
        @RequestBody req: AnalyzeRequest,
    ): ResponseEntity<Any> {
        if (req.sql.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("sql_analysis.be_sql_empty")))
        }
        return try {
            ResponseEntity.ok(sqlAnalysisService.analyzeCombined(req.sql, req.ddl))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: i18n.t("common.be_unknown_error"))))
        }
    }

    @PostMapping("/generate-query")
    fun generateQuery(
        @RequestBody req: GenerateQueryRequest,
    ): ResponseEntity<Any> {
        if (req.ddl.isBlank() || req.goal.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("sql_analysis.be_ddl_target_empty")))
        }
        return try {
            ResponseEntity.ok(sqlAnalysisService.generateQuery(req.ddl, req.seedData, req.goal))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: i18n.t("common.be_unknown_error"))))
        }
    }

    @PostMapping("/run-test")
    fun runTest(
        @RequestBody req: RunTestRequest,
    ): ResponseEntity<Any> {
        if (req.sql.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("sql_analysis.be_sql_empty")))
        }
        return try {
            ResponseEntity.ok(sqlAnalysisService.runTest(req.sql, req.testData))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: i18n.t("common.be_unknown_error"))))
        }
    }

    data class SaveHistoryRequest(
        val sql: String,
        val ddl: String? = null,
    )

    @PostMapping("/history")
    fun saveHistory(
        @RequestBody req: SaveHistoryRequest,
    ): ResponseEntity<Any> {
        if (req.sql.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("sql_analysis.be_sql_empty")))
        }
        val record = sqlRecordLifecycle.createRunningRecord(req.sql, req.ddl)
        return ResponseEntity.ok(mapOf("id" to record.id, "createdAt" to record.createdAt.toString()))
    }

    @GetMapping("/history")
    fun listHistory(): List<Map<String, Any?>> =
        sqlHistoryService.getAllAnalysisRecords().map { r ->
            val effectiveStatus = staleAnalysis.effectiveStatus(StaleAnalysisKind.SQL_ANALYSIS, r.status, r.createdAt)
            mapOf(
                "id" to r.id,
                "sql" to r.sql,
                "ddl" to r.ddl,
                "hasTestData" to (r.testData != null),
                "status" to effectiveStatus,
                "createdAt" to r.createdAt.toString(),
            )
        }

    @GetMapping("/history/{id}")
    fun getHistory(
        @PathVariable id: Long,
    ): ResponseEntity<Any> {
        val record = sqlHistoryService.getAnalysisRecord(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "id" to record.id,
                "sql" to record.sql,
                "ddl" to record.ddl,
                "testData" to record.testData,
                "analysisResult" to record.analysisResult,
                "createdAt" to record.createdAt.toString(),
            ),
        )
    }

    @DeleteMapping("/history/{id}")
    fun deleteHistory(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        if (sqlHistoryService.getAnalysisRecord(id) == null) return ResponseEntity.notFound().build()
        sqlHistoryService.deleteAnalysisRecord(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/history")
    fun deleteAllHistory(): ResponseEntity<Map<String, Any>> {
        sqlHistoryService.deleteAllAnalysisRecords()
        return ResponseEntity.ok(mapOf("deleted" to true))
    }
}
