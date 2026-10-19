package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.code.CodeQaAnalysisUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.AnalysisStatus
import jakarta.annotation.PreDestroy
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/mr-analysis")
class
CodeQaAnalysisApiController(
    private val analysisService: CodeQaAnalysisUseCase,
    private val i18n: I18nUseCase,
    internal val sseStreamer: CodeQaSseStreamer = CodeQaSseStreamer(analysisService),
) {
    private val dtoMapper = CodeQaAnalysisDtoMapper()

    @PreDestroy
    private fun shutdownSseExecutor() {
        sseStreamer.shutdown()
    }

    @PostMapping
    fun start(
        @RequestBody body: Map<String, String?>,
    ): ResponseEntity<Map<String, Any?>> {
        val mrUrl = body["mrUrl"]?.trim().orEmpty()
        if (mrUrl.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("mr_analysis.be_err_url_empty")))
        }
        val jiraTicketKey = body["jiraTicketKey"]?.trim()?.takeIf { it.isNotBlank() }
        val jiraConfigId = body["jiraConfigId"]?.trim()?.toLongOrNull()
        val gitConfigId = body["gitConfigId"]?.trim()?.toLongOrNull()
        val gitPlatform = body["gitPlatform"]?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val additionalInstructions = body["additionalInstructions"]?.trim()?.takeIf { it.isNotBlank() }
        val integrationTestEnabled = body["integrationTestEnabled"]?.trim()?.equals("true", ignoreCase = true) == true

        val analysis =
            analysisService.startAnalysis(
                mrUrl,
                jiraTicketKey,
                jiraConfigId,
                gitConfigId,
                gitPlatform,
                additionalInstructions,
                integrationTestEnabled,
            )
        return ResponseEntity.ok(dtoMapper.toStatusMap(analysis))
    }

    @GetMapping
    fun listAll(): ResponseEntity<List<Map<String, Any?>>> = ResponseEntity.ok(analysisService.getAllAnalyses().map { dtoMapper.toListItem(it) })

    @GetMapping("/{id}")
    fun getResult(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val analysis = analysisService.getAnalysis(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(dtoMapper.toFullMap(analysis))
    }

    @GetMapping("/{id}/status")
    fun getStatus(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val analysis = analysisService.getAnalysis(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(dtoMapper.toStatusMap(analysis))
    }

    @DeleteMapping("/{id}")
    fun deleteOne(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any>> {
        analysisService.getAnalysis(id) ?: return ResponseEntity.notFound().build()
        analysisService.deleteAnalysis(id)
        return ResponseEntity.ok(mapOf("deleted" to true))
    }

    @DeleteMapping("/history")
    fun deleteHistory(): ResponseEntity<Map<String, Any>> {
        analysisService.deleteAllAnalyses()
        return ResponseEntity.ok(mapOf("deleted" to true))
    }

    @PostMapping("/{id}/suggestions")
    fun postSuggestion(
        @PathVariable id: Long,
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<Map<String, Any?>> {
        val analysis = analysisService.getAnalysis(id) ?: return ResponseEntity.notFound().build()
        if (analysis.status != AnalysisStatus.COMPLETED) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("mr_analysis.be_analysis_not_completed")))
        }
        val filePath = body["filePath"]?.toString()?.trim().orEmpty()
        val lineNumber = (body["lineNumber"] as? Number)?.toInt()
        val suggestedCode = body["suggestedCode"]?.toString() ?: ""
        if (filePath.isBlank() || lineNumber == null || lineNumber < 1) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("mr_analysis.be_filepath_linenumber_required")))
        }
        return try {
            val url = analysisService.postSuggestion(id, filePath, lineNumber, suggestedCode)
            ResponseEntity.ok(mapOf("url" to url))
        } catch (e: Exception) {
            val isConnRefused =
                generateSequence(e as Throwable?) { it.cause }
                    .any { it.message?.contains("Connection refused", ignoreCase = true) == true }
            val msg =
                if (isConnRefused) {
                    "Cannot connect to the source control server. Please check that GitLab/GitHub is reachable."
                } else {
                    e.message ?: "Failed to post suggestion"
                }
            ResponseEntity.badRequest().body(mapOf("error" to msg))
        }
    }

    @GetMapping("/{id}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @PathVariable id: Long,
    ): SseEmitter = sseStreamer.openAnalysisStream(id)

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        analysisService.cancelAnalysis(id)
        val analysis = analysisService.getAnalysis(id)
        return ResponseEntity.ok(mapOf("status" to (analysis?.status ?: AnalysisStatus.FAILED)))
    }

    @GetMapping("/{id}/integration-test/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun integrationTestStream(
        @PathVariable id: Long,
    ): SseEmitter = sseStreamer.openIntegrationTestStream(id)
}
