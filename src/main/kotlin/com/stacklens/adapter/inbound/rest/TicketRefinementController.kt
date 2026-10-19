package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.RepoSelection
import com.stacklens.application.port.inbound.TicketRefinementUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.domain.model.TicketRefinement
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
@RequestMapping("/api/ticket-refinement")
class TicketRefinementController(
    private val useCase: TicketRefinementUseCase,
    private val i18n: I18nUseCase,
) {
    private val log = LoggerFactory.getLogger(TicketRefinementController::class.java)

    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @PostMapping
    fun start(
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<Map<String, Any?>> {
        val parentKey = (body["parentTicketKey"] as? String)?.trim().orEmpty()
        if (parentKey.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to tr("ticket_refinement.be_parent_ticket_key_required")))
        }
        val configId = resolveConfigId(body["jiraConfigId"], useCase::firstActiveJiraConfigId)
        if (configId == null) {
            return ResponseEntity.badRequest().body(mapOf("error" to tr("ticket_refinement.be_no_jira_config")))
        }
        if (!useCase.jiraConfigExists(configId)) {
            return ResponseEntity.badRequest().body(mapOf("error" to tr("ticket_refinement.be_jira_config_not_found")))
        }
        val template = (body["templateOverride"] as? String)?.takeIf { it.isNotBlank() }
        val repoSelections = parseRepoSelections(body["repoSelections"])
        val dbConfigIds = parseDatabaseConfigIds(body["databaseConfigIds"], body["databaseConfigId"])
        val singleOnly =
            when (val v = body["singleTicketOnly"]) {
                is Boolean -> v
                is String -> v.equals("true", ignoreCase = true)
                else -> true
            }
        val saved = useCase.startPreparation(parentKey, configId, template, repoSelections, singleOnly, dbConfigIds)
        return ResponseEntity.ok(mapOf("id" to saved.id, "status" to saved.status))
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseDatabaseConfigIds(
        idsRaw: Any?,
        singleRaw: Any?,
    ): List<Long> {
        val list = idsRaw as? List<*>
        if (!list.isNullOrEmpty()) return list.mapNotNull { (it as? Number)?.toLong() }
        val single =
            when (singleRaw) {
                is Number -> singleRaw.toLong()
                is String -> singleRaw.trim().toLongOrNull()
                else -> null
            }
        return if (single != null) listOf(single) else emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseRepoSelections(raw: Any?): List<RepoSelection> {
        val list = raw as? List<Map<String, Any?>> ?: return emptyList()
        return list.mapNotNull { m ->
            val type = (m["type"] as? String)?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val id = (m["id"] as? Number)?.toLong() ?: (m["id"] as? String)?.toLongOrNull() ?: return@mapNotNull null
            val projectId = (m["projectId"] as? Number)?.toLong() ?: (m["projectId"] as? String)?.toLongOrNull()
            if (type != "local" && type != "gitlab") return@mapNotNull null
            RepoSelection(type = type, id = id, projectId = projectId)
        }
    }

    @GetMapping
    fun list(): List<Map<String, Any?>> =
        useCase.getAllPreparations().map {
            mapOf(
                "id" to it.id,
                "parentTicketKey" to it.parentTicketKey,
                "status" to it.status,
                "createdAt" to it.createdAt.toString(),
                "completedAt" to it.completedAt?.toString(),
            )
        }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val e = useCase.getPreparation(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toFullDto(e))
    }

    @GetMapping("/{id}/status")
    fun status(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val e = useCase.getPreparation(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "id" to e.id,
                "status" to e.status,
                "currentStep" to e.currentStep,
                "errorMessage" to e.errorMessage,
            ),
        )
    }

    @PostMapping("/{id}/comment/{key}")
    fun postComment(
        @PathVariable id: Long,
        @PathVariable key: String,
    ): ResponseEntity<Map<String, Any?>> =
        try {
            val updated = useCase.postReviewAsComment(id, key)
            ResponseEntity.ok(toFullDto(updated))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }

    @PostMapping("/{id}/subtasks/{key}")
    fun createSubtasks(
        @PathVariable id: Long,
        @PathVariable key: String,
        @RequestBody body: Any?,
    ): ResponseEntity<Map<String, Any?>> {
        val drafts = parseSubtaskDrafts(body)
        if (drafts.isEmpty()) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to tr("ticket_refinement.be_subtasks_empty", "No subtasks provided.")))
        }
        return try {
            val result = useCase.createSuggestedSubtasks(id, key, drafts)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.warn("[TicketRefinement] Subtask creation failed: {}", e.message)
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: "unknown")))
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseSubtaskDrafts(body: Any?): List<SubtaskDraft> {
        val list: List<*> =
            when (body) {
                is List<*> -> body
                is Map<*, *> -> (body["subtasks"] as? List<*>) ?: return emptyList()
                else -> return emptyList()
            }
        return list.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val sum = (map["summary"] as? String)?.trim().orEmpty()
            if (sum.isBlank()) return@mapNotNull null
            val desc = (map["description"] as? String).orEmpty()
            val type = (map["issueType"] as? String)?.trim()?.ifBlank { "Subtask" } ?: "Subtask"
            val prio = (map["priority"] as? String)?.trim()?.ifBlank { "Minor" } ?: "Minor"
            val labels =
                (map["labels"] as? List<*>)
                    ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotBlank() } }
                    ?: emptyList()
            SubtaskDraft(
                summary = sum,
                description = desc,
                issueType = type,
                priority = prio,
                labels = labels,
            )
        }
    }

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        useCase.cancelPreparation(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        useCase.deletePreparation(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping
    fun deleteAll(): ResponseEntity<Void> {
        useCase.deleteAllPreparations()
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/template/default")
    fun defaultTemplate(): ResponseEntity<Map<String, String>> = ResponseEntity.ok(mapOf("template" to useCase.defaultPromptTemplate()))

    internal fun toFullDto(e: TicketRefinement): Map<String, Any?> =
        mapOf(
            "id" to e.id,
            "parentTicketKey" to e.parentTicketKey,
            "jiraConfigId" to e.jiraConfigId,
            "status" to e.status,
            "currentStep" to e.currentStep,
            "errorMessage" to e.errorMessage,
            "parentTicketJson" to e.parentTicketJson,
            "reviewsJson" to useCase.normalizeReviewsJson(e.reviewsJson),
            "subtasksCreatedJson" to e.subtasksCreatedJson,
            "repoSelectionsJson" to e.repoSelectionsJson,
            "createdAt" to e.createdAt.toString(),
            "completedAt" to e.completedAt?.toString(),
        )
}
