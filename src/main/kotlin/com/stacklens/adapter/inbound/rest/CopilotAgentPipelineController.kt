package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.inbound.rest.pipeline.MockupFileAccess
import com.stacklens.adapter.inbound.rest.pipeline.PipelineSsePublisher
import com.stacklens.adapter.inbound.rest.pipeline.stageIconOf
import com.stacklens.adapter.inbound.rest.pipeline.stageLabelOf
import com.stacklens.application.port.inbound.pipeline.CopilotAgentPipelineUseCase
import com.stacklens.application.port.inbound.pipeline.PipelineWriteError
import com.stacklens.application.port.inbound.pipeline.PipelineWriteErrorCode
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.platform.SystemGuidanceUseCase
import com.stacklens.application.port.inbound.ticket.JiraTicketImplementationUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.MockupImage
import com.stacklens.application.port.model.pipeline.PipelineStatus
import com.stacklens.domain.text.AgentTextUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/agent-pipeline")
open class CopilotAgentPipelineController
    @Autowired
    internal constructor(
        private val pipelineService: CopilotAgentPipelineUseCase,
        private val jiraAnalysisService: JiraTicketImplementationUseCase,
        private val i18n: I18nUseCase,
        private val mockupReader: MockupFileAccess,
        private val ssePublisher: PipelineSsePublisher,
        private val systemGuidance: SystemGuidanceUseCase,
        private val workspaceFileUseCase: WorkspaceFileUseCase,
    ) {
        private val log = LoggerFactory.getLogger(CopilotAgentPipelineController::class.java)

        companion object {
            fun parseFinnOutput(output: String): List<AgentTextUtils.ParsedFile> = AgentTextUtils.parseAgentOutput(output)
        }

        data class MockupImageRequest(
            val base64: String,
            val mimeType: String,
        )

        data class StartRequest(
            val rawText: String,
            val localRepoConfigId: Long? = null,
            val databaseConfigIds: List<Long> = emptyList(),
            val mockupBase64: String? = null,
            val mockupMimeType: String? = null,
            val mockupImages: List<MockupImageRequest> = emptyList(),
        )

        data class StartFromJiraRequest(
            val ticketKey: String,
            val jiraConfigId: Long,
            val localRepoConfigId: Long? = null,
            val databaseConfigIds: List<Long> = emptyList(),
            val mockupBase64: String? = null,
            val mockupMimeType: String? = null,
            val mockupImages: List<MockupImageRequest> = emptyList(),
        )

        @PostMapping
        fun start(
            @RequestBody req: StartRequest,
        ): ResponseEntity<Map<String, Any>> {
            if (req.rawText.isBlank()) {
                return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("pipeline.be_raw_text_empty")))
            }
            if (req.rawText.length > 50_000) {
                return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("pipeline.be_raw_text_too_long")))
            }
            val mockupValidation = mockupReader.validateMockup(req.mockupBase64, req.mockupMimeType)
            if (mockupValidation != null) return ResponseEntity.badRequest().body(mapOf("error" to mockupValidation))
            for (img in req.mockupImages) {
                val imgValidation = mockupReader.validateMockup(img.base64, img.mimeType)
                if (imgValidation != null) return ResponseEntity.badRequest().body(mapOf("error" to imgValidation))
            }
            val id =
                pipelineService.start(
                    req.rawText,
                    req.localRepoConfigId,
                    req.databaseConfigIds,
                    req.mockupBase64,
                    req.mockupMimeType,
                    mockupImages = req.mockupImages.map { MockupImage(it.base64, it.mimeType) },
                )
            return ResponseEntity.ok(mapOf("id" to id, "status" to "PENDING"))
        }

        @PostMapping("/from-jira")
        fun startFromJira(
            @RequestBody req: StartFromJiraRequest,
        ): ResponseEntity<Map<String, Any>> {
            if (req.ticketKey.isBlank()) {
                return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("pipeline.be_ticket_key_empty")))
            }
            val mockupValidation = mockupReader.validateMockup(req.mockupBase64, req.mockupMimeType)
            if (mockupValidation != null) return ResponseEntity.badRequest().body(mapOf("error" to mockupValidation))
            for (img in req.mockupImages) {
                val imgValidation = mockupReader.validateMockup(img.base64, img.mimeType)
                if (imgValidation != null) return ResponseEntity.badRequest().body(mapOf("error" to imgValidation))
            }
            val ticketKey = req.ticketKey.trim().uppercase()
            val ticketData =
                try {
                    jiraAnalysisService.fetchTicketAsText(ticketKey, req.jiraConfigId)
                } catch (e: Exception) {
                    log.warn("Jira-Ticket fetch failed for key={} configId={}: {}", ticketKey, req.jiraConfigId, e.message)
                    return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("pipeline.be_ticket_fetch_failed")))
                }
            val id =
                pipelineService.startFromJira(
                    ticketData,
                    ticketKey,
                    req.localRepoConfigId,
                    req.databaseConfigIds,
                    req.mockupBase64,
                    req.mockupMimeType,
                    mockupImages = req.mockupImages.map { MockupImage(it.base64, it.mimeType) },
                )
            return ResponseEntity.ok(mapOf("id" to id, "status" to "PENDING"))
        }

        @GetMapping
        fun list(): List<Map<String, Any?>> =
            pipelineService.allRuns().map { run ->
                val effectiveStatus = pipelineService.effectiveStatus(run)
                mapOf(
                    "id" to run.id,
                    "status" to effectiveStatus,
                    "featureSlug" to run.featureSlug,
                    "createdAt" to run.createdAt,
                    "errorMessage" to run.errorMessage,
                )
            }

        @GetMapping("/{id}")
        fun status(
            @PathVariable id: Long,
        ): ResponseEntity<Map<String, Any?>> {
            val run = pipelineService.get(id) ?: return ResponseEntity.notFound().build()
            return ResponseEntity.ok(run.toStatusMap())
        }

        @PostMapping("/{id}/cancel")
        fun cancel(
            @PathVariable id: Long,
        ): ResponseEntity<Void> {
            val run = pipelineService.get(id) ?: return ResponseEntity.notFound().build()
            if (run.status != PipelineStatus.RUNNING && run.status != PipelineStatus.PENDING) {
                return ResponseEntity.status(409).build()
            }
            pipelineService.cancel(id)
            return ResponseEntity.ok().build()
        }

        @DeleteMapping("/{id}")
        fun delete(
            @PathVariable id: Long,
        ): ResponseEntity<Void> {
            val run = pipelineService.get(id)
            if (run != null && (run.status == PipelineStatus.RUNNING || run.status == PipelineStatus.PENDING)) {
                return ResponseEntity.status(409).build()
            }
            pipelineService.delete(id)
            return ResponseEntity.noContent().build()
        }

        @DeleteMapping
        fun deleteAll(): ResponseEntity<Map<String, Any>> {
            pipelineService.deleteAll()
            return ResponseEntity.ok(mapOf("deleted" to true))
        }

        @GetMapping("/{id}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
        fun stream(
            @PathVariable id: Long,
        ): SseEmitter = ssePublisher.stream(id)

        @PostMapping("/{id}/chat", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
        fun chat(
            @PathVariable id: Long,
            @RequestBody body: Map<String, String>,
        ): SseEmitter = ssePublisher.chat(id, body)

        @GetMapping("/{id}/check-repo-access")
        fun checkRepoAccess(
            @PathVariable id: Long,
        ): ResponseEntity<Map<String, Any?>> {
            val run = pipelineService.get(id) ?: return ResponseEntity.notFound().build()
            val repoPath =
                run.repoPath
                    ?: return ResponseEntity.ok(mapOf("ok" to false, "reason" to i18n.t("config.be_no_repo_linked")))
            val view = workspaceFileUseCase.probeAccess(repoPath)
            val probeError = view.probeError
            if (!view.exists && probeError != null) {
                return ResponseEntity.ok(
                    mapOf("ok" to false, "reason" to i18n.t("config.be_invalid_path_msg", probeError)),
                )
            }
            val info =
                mutableMapOf<String, Any?>(
                    "path" to view.path,
                    "exists" to view.exists,
                    "isDir" to view.isDirectory,
                    "canRead" to view.canRead,
                    "canWrite" to view.canWrite,
                    "javaUser" to System.getProperty("user.name"),
                )
            if (!view.exists) {
                info["ok"] = false
                info["reason"] = i18n.t("config.be_dir_not_exists")
            } else if (!view.canWrite) {
                info["ok"] = false
                info["reason"] = i18n.t("config.be_write_access_denied")
            } else {
                probeError?.let { info["probeError"] = it }
                info["ok"] = view.probeOk
                if (!view.probeOk) info["reason"] = i18n.t("config.be_write_probe_failed")
            }
            return ResponseEntity.ok(info)
        }

        @GetMapping("/{id}/write-files")
        fun previewWriteFiles(
            @PathVariable id: Long,
        ): ResponseEntity<Map<String, Any?>> {
            val preview = pipelineService.previewWriteFiles(id) ?: return ResponseEntity.notFound().build()
            preview.errorKey?.let { return ResponseEntity.badRequest().body(mapOf("error" to i18n.t(it))) }
            return ResponseEntity.ok(
                mapOf(
                    "repoPath" to preview.repoPath,
                    "files" to preview.files.map { mapOf("path" to it.path, "size" to it.size) },
                ),
            )
        }

        @PostMapping("/{id}/write-files")
        fun writeFiles(
            @PathVariable id: Long,
        ): ResponseEntity<Map<String, Any?>> {
            val outcome = pipelineService.writeFiles(id, null) ?: return ResponseEntity.notFound().build()
            outcome.errorKey?.let { key ->
                val text = if (outcome.errorArg != null) i18n.t(key, outcome.errorArg) else i18n.t(key)
                val hint = if (outcome.showWritePermissionHint) systemGuidance.writePermissionHint() else ""
                return ResponseEntity.badRequest().body(mapOf("error" to text + hint))
            }
            val body = mutableMapOf<String, Any?>()
            body["written"] = outcome.written
            outcome.deleted?.let { body["deleted"] = it }
            body["errors"] = outcome.errors.map { formatWriteError(it) }
            outcome.infoKey?.let { body["info"] = i18n.t(it) }
            return ResponseEntity.ok(body)
        }

        private fun formatWriteError(error: PipelineWriteError): String =
            when (error.code) {
                PipelineWriteErrorCode.PATH_TRAVERSAL -> "${error.path}: ${i18n.t("config.be_path_traversal_denied")}"
                PipelineWriteErrorCode.DIR_CREATE_FAILED -> "${error.path}: ${i18n.t("config.be_dir_create_failed", error.detail ?: "")}"
                PipelineWriteErrorCode.SECURITY_DENIED -> "${error.path}: Zugriff verweigert — ${error.detail}"
                PipelineWriteErrorCode.WRITE_FAILED -> "${error.path}: ${error.detail}"
                null -> error.message ?: ""
            }

        @GetMapping("/mockup-file")
        fun readMockupFile(
            @RequestParam path: String,
        ): ResponseEntity<Map<String, Any?>> = mockupReader.readMockupFile(path)
    }

internal fun AgentPipelineRun.toStatusMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "status" to status.name,
        "jiraMode" to (jiraTicketData != null),
        "currentStage" to currentStage,
        "errorMessage" to errorMessage,
        "repoPath" to repoPath,
        "featureSlug" to featureSlug,
        "gitBranch" to gitBranch,
        "gitLog" to gitLog,
        "createdAt" to createdAt,
        "stages" to
            stages.map { s ->
                mapOf(
                    "name" to s.name,
                    "label" to stageLabelOf(s.name),
                    "icon" to stageIconOf(s.name),
                    "status" to s.status.name,
                    "output" to s.output,
                )
            },
    )
