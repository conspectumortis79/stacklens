package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.JiraTicketSummaryUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.JiraTicketSummary
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ticket-summary")
class JiraTicketSummaryController(
    private val useCase: JiraTicketSummaryUseCase,
    private val i18n: I18nUseCase,
) {
    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @PostMapping
    fun start(
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<Map<String, Any?>> =
        try {
            val ticketKey = (body["ticketKey"] as? String)?.trim().orEmpty()
            val configId = resolveConfigId(body["jiraConfigId"], useCase::firstActiveJiraConfigId)
            val template = (body["templateOverride"] as? String)?.takeIf { it.isNotBlank() }
            val saved = useCase.startSummary(ticketKey, configId, template)
            ResponseEntity.ok(mapOf("id" to saved.id, "status" to saved.status))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: tr("ticket_summary.be_invalid_input"))))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: tr("ticket_summary.be_invalid_input"))))
        }

    @GetMapping
    fun list(): List<Map<String, Any?>> =
        useCase.getAllSummaries().map {
            mapOf(
                "id" to it.id,
                "ticketKey" to it.ticketKey,
                "status" to it.status,
                "createdAt" to it.createdAt.toString(),
                "completedAt" to it.completedAt?.toString(),
            )
        }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val e = useCase.getSummary(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toFullDto(e))
    }

    @GetMapping("/{id}/status")
    fun status(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val e = useCase.getSummary(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "id" to e.id,
                "status" to e.status,
                "currentStep" to e.currentStep,
                "errorMessage" to e.errorMessage,
            ),
        )
    }

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        useCase.cancelSummary(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        useCase.deleteSummary(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping
    fun deleteAll(): ResponseEntity<Void> {
        useCase.deleteAllSummaries()
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/template/default")
    fun defaultTemplate(): ResponseEntity<Map<String, String>> = ResponseEntity.ok(mapOf("template" to useCase.defaultPromptTemplate()))

    private fun toFullDto(e: JiraTicketSummary): Map<String, Any?> =
        mapOf(
            "id" to e.id,
            "ticketKey" to e.ticketKey,
            "jiraConfigId" to e.jiraConfigId,
            "status" to e.status,
            "currentStep" to e.currentStep,
            "errorMessage" to e.errorMessage,
            "originalText" to e.originalText,
            "summaryMarkdown" to e.summaryMarkdown,
            "summaryHtml" to e.summaryHtml,
            "createdAt" to e.createdAt.toString(),
            "completedAt" to e.completedAt?.toString(),
        )
}
