package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.SprintReviewUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.SprintReview
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sprint-review")
class SprintReviewController(
    private val useCase: SprintReviewUseCase,
    private val i18n: I18nUseCase,
) {
    private val log = LoggerFactory.getLogger(SprintReviewController::class.java)

    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @PostMapping
    fun start(
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<Map<String, Any?>> {
        val sprintName = (body["sprintName"] as? String)?.trim().orEmpty()
        if (sprintName.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to tr("sprint_review.be_sprint_name_required")))
        }
        val targetKey = (body["targetTicketKey"] as? String)?.trim().orEmpty()
        if (targetKey.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to tr("sprint_review.be_target_ticket_key_required")))
        }
        val configId = resolveConfigId(body["jiraConfigId"], useCase::firstActiveJiraConfigId)
        if (configId == null) {
            return ResponseEntity.badRequest().body(mapOf("error" to tr("sprint_review.be_no_jira_config")))
        }
        if (!useCase.jiraConfigExists(configId)) {
            return ResponseEntity.badRequest().body(mapOf("error" to tr("sprint_review.be_jira_config_not_found")))
        }
        val template = (body["templateOverride"] as? String)?.takeIf { it.isNotBlank() }
        val includeImprovement =
            when (val v = body["includeImprovement"]) {
                is Boolean -> v
                is String -> v.equals("true", ignoreCase = true)
                else -> true
            }
        val saved = useCase.startReview(sprintName, targetKey, configId, template, includeImprovement)
        return ResponseEntity.ok(mapOf("id" to saved.id, "status" to saved.status))
    }

    @GetMapping
    fun list(): List<Map<String, Any?>> =
        useCase.getAllReviews().map {
            mapOf(
                "id" to it.id,
                "sprintName" to it.sprintName,
                "targetTicketKey" to it.targetTicketKey,
                "status" to it.status,
                "totalIssues" to it.totalIssues,
                "createdAt" to it.createdAt.toString(),
                "completedAt" to it.completedAt?.toString(),
            )
        }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val e = useCase.getReview(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toFullDto(e))
    }

    @GetMapping("/{id}/status")
    fun status(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val e = useCase.getReview(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "id" to e.id,
                "status" to e.status,
                "currentStep" to e.currentStep,
                "errorMessage" to e.errorMessage,
                "totalIssues" to e.totalIssues,
            ),
        )
    }

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        useCase.cancelReview(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        useCase.deleteReview(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping
    fun deleteAll(): ResponseEntity<Void> {
        useCase.deleteAllReviews()
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/template/default")
    fun defaultTemplate(): ResponseEntity<Map<String, String>> = ResponseEntity.ok(mapOf("template" to useCase.defaultPromptTemplate()))

    @PostMapping("/{id}/post-to-jira")
    fun postToJira(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> =
        try {
            val updated = useCase.postAdfToJira(id)
            ResponseEntity.ok(toFullDto(updated))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "invalid request")))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "invalid state")))
        } catch (e: Exception) {
            log.warn("[SprintReview] postToJira failed for {}: {}", id, e.message)
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: "unknown")))
        }

    internal fun toFullDto(e: SprintReview): Map<String, Any?> =
        mapOf(
            "id" to e.id,
            "sprintName" to e.sprintName,
            "targetTicketKey" to e.targetTicketKey,
            "targetTicketUrl" to e.targetTicketUrl,
            "jiraConfigId" to e.jiraConfigId,
            "status" to e.status,
            "currentStep" to e.currentStep,
            "errorMessage" to e.errorMessage,
            "sprintId" to e.sprintId,
            "sprintState" to e.sprintState,
            "sprintUrl" to e.sprintUrl,
            "totalIssues" to e.totalIssues,
            "includeImprovement" to e.includeImprovement,
            "rowsJson" to e.rowsJson,
            "tableAdfJson" to e.tableAdfJson,
            "createdAt" to e.createdAt.toString(),
            "completedAt" to e.completedAt?.toString(),
        )
}
