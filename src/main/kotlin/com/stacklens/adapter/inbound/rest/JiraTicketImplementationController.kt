
package com.stacklens.adapter.inbound.rest

import com.fasterxml.jackson.annotation.JsonRawValue
import com.stacklens.application.port.inbound.ticket.JiraTicketImplementationUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ticket-implementation")
class JiraTicketImplementationController(
    private val analysisService: JiraTicketImplementationUseCase,
) {
    data class AnalysisDetailDto(
        val id: Long,
        val ticketKey: String,
        val status: String,
        val completedAt: String?,
        @JsonRawValue val ticket: String?,
        @JsonRawValue val codeImplementation: String?,
        @JsonRawValue val monorepoAnalysis: String?,
    )

    @GetMapping
    fun list(): List<Map<String, Any?>> =
        analysisService.getAllAnalyses().map { a ->
            mapOf(
                "id" to a.id,
                "ticketKey" to a.ticketKey,
                "status" to a.status,
                "createdAt" to a.createdAt.toString(),
                "completedAt" to a.completedAt?.toString(),
            )
        }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): ResponseEntity<AnalysisDetailDto> {
        val a = analysisService.findAnalysisById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            AnalysisDetailDto(
                id = a.id,
                ticketKey = a.ticketKey,
                status = a.status,
                completedAt = a.completedAt?.toString(),
                ticket = a.ticketJson,
                codeImplementation = a.codeImplementationJson,
                monorepoAnalysis = a.monorepoAnalysisJson,
            ),
        )
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<*> {
        val exists = analysisService.findAnalysisById(id) != null
        if (!exists) return ResponseEntity.notFound().build<Void>()
        analysisService.deleteAnalysis(id)
        return ResponseEntity.noContent().build<Void>()
    }

    @DeleteMapping("/history")
    fun deleteAll(): ResponseEntity<Map<String, Any>> {
        analysisService.deleteAllFinishedAnalyses()
        return ResponseEntity.ok(mapOf("deleted" to true))
    }
}
