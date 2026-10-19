package com.stacklens.adapter.inbound.rest.support

import com.stacklens.application.port.inbound.analysis.AnalysisCrudUseCase
import com.stacklens.domain.model.AnalysisRecord
import org.springframework.http.ResponseEntity

class AnalysisCrudResponder<A : AnalysisRecord>(
    private val useCase: AnalysisCrudUseCase<A>,
    private val toMap: (A) -> Map<String, Any?>,
) {
    fun status(analysisId: Long): ResponseEntity<Map<String, Any?>> =
        useCase
            .getAnalysis(analysisId)
            ?.let { ResponseEntity.ok(toMap(it)) }
            ?: ResponseEntity.notFound().build()

    fun all(): List<Map<String, Any?>> = useCase.getAllAnalyses().map { toMap(it) }

    fun delete(analysisId: Long): ResponseEntity<Void> {
        useCase.getAnalysis(analysisId) ?: return ResponseEntity.notFound().build()
        useCase.deleteAnalysis(analysisId)
        return ResponseEntity.noContent().build()
    }

    fun cancel(analysisId: Long): ResponseEntity<Map<String, Any?>> {
        useCase.cancelAnalysis(analysisId)
        val status = useCase.getAnalysis(analysisId)?.status ?: "FAILED"
        return ResponseEntity.ok(mapOf("status" to status))
    }

    fun latest(findLatest: () -> A?): ResponseEntity<Map<String, Any?>> =
        findLatest()
            ?.let { ResponseEntity.ok(toMap(it)) }
            ?: ResponseEntity.ok(mapOf("status" to "NONE"))

    fun started(analysis: A): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(
            mapOf(
                "analysisId" to analysis.id,
                "status" to analysis.status,
            ),
        )

    fun cancelled(cancelledId: Long?): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(mapOf("cancelledId" to cancelledId))
}
