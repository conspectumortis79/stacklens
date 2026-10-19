package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.TicketCreationUseCase
import com.stacklens.application.port.inbound.analysis.StaleAnalysisKind
import com.stacklens.application.port.inbound.analysis.StaleAnalysisStatusUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.ticket.TicketAttachmentOutcome
import com.stacklens.application.port.inbound.ticket.TicketAttachmentReadUseCase
import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.domain.model.IssueType
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClientResponseException

@RestController
@RequestMapping("/api/ticket-creation")
class TicketCreationController(
    private val useCase: TicketCreationUseCase,
    private val i18n: I18nUseCase,
    private val staleAnalysis: StaleAnalysisStatusUseCase,
    private val attachmentRead: TicketAttachmentReadUseCase,
) {
    private val log = LoggerFactory.getLogger(TicketCreationController::class.java)

    private val customFieldId = Regex("^customfield_\\d+$")

    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    @PostMapping("/generate")
    fun generate(
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<Map<String, Any?>> {
        val inputText = (body["inputText"] as? String)?.trim().orEmpty()
        if (inputText.isBlank()) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to tr("ticket_creation.be_input_required", "Input text is required.")))
        }
        val attachmentSection = (body["attachmentSection"] as? String).orEmpty()

        val issueType =
            IssueType.fromString(body["issueType"] as? String) ?: IssueType.DEFAULT

        return try {
            val result = useCase.generateTicket(inputText, attachmentSection, issueType)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.warn("[TicketCreation] Generation failed: {}", e.message)
            val errorMsg = tr("ticket_creation.be_generation_failed", e.message ?: "")
            ResponseEntity
                .internalServerError()
                .body(mapOf("error" to errorMsg))
        }
    }

    @PostMapping("/create-jira")
    fun createJira(
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<Map<String, Any?>> {
        val configId = parseConfigId(body["jiraConfigId"])
        if (configId == null) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to tr("ticket_creation.be_no_jira_config", "Please select a Jira configuration.")))
        }
        if (!useCase.jiraConfigExists(configId)) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to tr("ticket_creation.be_jira_config_not_found", "Jira configuration not found.")))
        }

        val projectKey = (body["projectKey"] as? String)?.trim()?.uppercase().orEmpty()
        if (projectKey.isBlank()) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to tr("ticket_creation.be_project_key_required", "Project key is required.")))
        }
        val summary = (body["summary"] as? String)?.trim().orEmpty()
        if (summary.isBlank()) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to tr("ticket_creation.be_summary_required", "Summary is required.")))
        }

        val description = (body["description"] as? String).orEmpty()
        val issueType = (body["issueType"] as? String)?.trim()?.ifBlank { "Story" } ?: "Story"
        val priority = (body["priority"] as? String)?.trim()?.ifBlank { "Minor" } ?: "Minor"

        @Suppress("UNCHECKED_CAST")
        val labels = (body["labels"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        val historyId =
            when (val raw = body["historyId"]) {
                is Number -> raw.toLong()
                is String -> raw.trim().toLongOrNull()
                else -> null
            }

        val subtasks =
            (body["subtasks"] as? List<*>)
                ?.mapNotNull { raw ->
                    val map = raw as? Map<*, *> ?: return@mapNotNull null
                    val sum = (map["summary"] as? String)?.trim().orEmpty()
                    if (sum.isBlank()) return@mapNotNull null
                    val desc = (map["description"] as? String).orEmpty()
                    val type = (map["issueType"] as? String)?.trim()?.ifBlank { "Subtask" } ?: "Subtask"
                    val prio = (map["priority"] as? String)?.trim()?.ifBlank { priority } ?: priority

                    @Suppress("UNCHECKED_CAST")
                    val sub = (map["labels"] as? List<*>)?.mapNotNull { it as? String } ?: labels
                    SubtaskDraft(
                        summary = sum,
                        description = desc,
                        issueType = type,
                        priority = prio,
                        labels = sub,
                    )
                } ?: emptyList()

        val customFields = parseCustomFields(body["customFields"])

        return try {
            val result =
                useCase.createJiraTicket(
                    configId,
                    projectKey,
                    summary,
                    description,
                    issueType,
                    labels,
                    historyId,
                    priority,
                    subtasks,
                    customFields,
                )
            ResponseEntity.ok(result)
        } catch (e: WebClientResponseException) {
            val errBody = e.responseBodyAsString.take(400)
            log.warn("[TicketCreation] Jira API error {}: {}", e.statusCode, errBody)
            ResponseEntity
                .status(e.statusCode)
                .body(mapOf("error" to "Jira responded with HTTP ${e.statusCode.value()}: $errBody"))
        } catch (e: Exception) {
            log.warn("[TicketCreation] Create failed: {}", e.message)
            ResponseEntity
                .internalServerError()
                .body(mapOf("error" to tr("ticket_creation.be_create_failed", e.message ?: "")))
        }
    }

    @GetMapping("/required-fields")
    fun requiredFields(
        @RequestParam jiraConfigId: Long,
        @RequestParam projectKey: String,
        @RequestParam issueType: String,
    ): ResponseEntity<Any> {
        val key = projectKey.trim().uppercase()
        if (key.isBlank() || issueType.isBlank()) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to tr("ticket_creation.be_project_key_required", "Project key is required.")))
        }
        if (!useCase.jiraConfigExists(jiraConfigId)) {
            return ResponseEntity
                .badRequest()
                .body(mapOf("error" to tr("ticket_creation.be_jira_config_not_found", "Jira configuration not found.")))
        }
        return try {
            ResponseEntity.ok(useCase.requiredJiraFields(jiraConfigId, key, issueType.trim()))
        } catch (e: WebClientResponseException) {
            val errBody = e.responseBodyAsString.take(400)
            log.warn("[TicketCreation] Jira createmeta error {}: {}", e.statusCode, errBody)
            ResponseEntity
                .status(e.statusCode)
                .body(mapOf("error" to "Jira responded with HTTP ${e.statusCode.value()}: $errBody"))
        }
    }

    private fun parseCustomFields(raw: Any?): Map<String, Any> =
        (raw as? Map<*, *>)
            ?.entries
            ?.mapNotNull { (k, v) ->
                val fieldId = (k as? String)?.takeIf { customFieldId.matches(it) } ?: return@mapNotNull null
                val value: Any =
                    when (v) {
                        is String -> v.trim().takeIf { it.isNotEmpty() }
                        is List<*> -> v.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }.takeIf { it.isNotEmpty() }
                        else -> null
                    } ?: return@mapNotNull null
                fieldId to value
            }?.toMap()
            .orEmpty()

    @PostMapping("/history/{id}/cancel")
    fun cancelHistoryItem(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val cancelled = useCase.cancelRecord(id)
        return if (cancelled) {
            ResponseEntity.ok(mapOf("status" to "CANCELLED"))
        } else {
            val record = useCase.getRecord(id)
            if (record != null) {
                ResponseEntity.ok(record.toMap())
            } else {
                ResponseEntity.notFound().build()
            }
        }
    }

    @GetMapping("/history")
    fun history(): List<Map<String, Any?>> =
        useCase.getAllRecords().map { r ->
            val effectiveStatus = staleAnalysis.effectiveStatus(StaleAnalysisKind.TICKET_CREATION, r.status, r.createdAt)
            if (effectiveStatus != r.status) r.toMap() + ("status" to effectiveStatus) else r.toMap()
        }

    @GetMapping("/history/{id}")
    fun historyItem(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val record = useCase.getRecord(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(record.toMap())
    }

    @DeleteMapping("/history/{id}")
    fun deleteHistoryItem(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        useCase.deleteRecord(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/history")
    fun deleteAllHistory(): ResponseEntity<Void> {
        useCase.deleteAllRecords()
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/read-binary-file")
    fun readBinaryFile(
        @RequestParam path: String,
    ): ResponseEntity<Map<String, Any?>> =
        when (val outcome = attachmentRead.readAttachment(path)) {
            TicketAttachmentOutcome.AccessDenied ->
                ResponseEntity
                    .badRequest()
                    .body(mapOf("error" to tr("ticket_creation.be_access_denied", "Access denied.")))
            TicketAttachmentOutcome.NotFound ->
                ResponseEntity
                    .badRequest()
                    .body(mapOf("error" to tr("ticket_creation.be_file_not_found", "File not found.")))
            TicketAttachmentOutcome.ImageTooLarge ->
                ResponseEntity
                    .badRequest()
                    .body(mapOf("error" to tr("ticket_creation.be_file_too_large", "File exceeds 10 MB limit.")))
            TicketAttachmentOutcome.PdfTooLarge ->
                ResponseEntity
                    .badRequest()
                    .body(mapOf("error" to tr("ticket_creation.be_file_too_large_pdf", "File exceeds 24 MB limit.")))
            is TicketAttachmentOutcome.Unsupported ->
                ResponseEntity
                    .badRequest()
                    .body(mapOf("error" to "Unsupported file type: .${outcome.extension}"))
            is TicketAttachmentOutcome.Image ->
                ResponseEntity.ok(
                    mapOf(
                        "type" to "image",
                        "dataUrl" to "data:${outcome.mimeType};base64,${java.util.Base64.getEncoder().encodeToString(outcome.bytes)}",
                        "name" to outcome.name,
                    ),
                )
            is TicketAttachmentOutcome.Pdf ->
                if (outcome.extractionError) {
                    log.warn("[TicketCreation] PDF text extraction failed for {}", outcome.name)
                    ResponseEntity.ok(
                        mapOf(
                            "type" to "pdf",
                            "text" to "",
                            "extractionError" to tr("ticket_creation.be_pdf_extract_failed", "Could not extract text from this PDF."),
                            "name" to outcome.name,
                        ),
                    )
                } else {
                    ResponseEntity.ok(
                        mapOf(
                            "type" to "pdf",
                            "text" to outcome.text,
                            "name" to outcome.name,
                        ),
                    )
                }
        }
}
