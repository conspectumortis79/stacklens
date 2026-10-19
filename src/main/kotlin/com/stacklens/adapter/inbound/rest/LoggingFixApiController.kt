package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.logging.CancelOutcome
import com.stacklens.application.port.inbound.logging.DeleteOutcome
import com.stacklens.application.port.inbound.logging.LoggingFixFileWriteUseCase
import com.stacklens.application.port.inbound.logging.LoggingFixLifecycleUseCase
import com.stacklens.application.port.inbound.logging.LoggingFixStageOutputUseCase
import com.stacklens.application.port.inbound.logging.LoggingFixStartRequest
import com.stacklens.application.port.inbound.logging.LoggingFixValidationUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/logging/fix")
class LoggingFixApiController(
    private val lifecycle: LoggingFixLifecycleUseCase,
    private val stageOutput: LoggingFixStageOutputUseCase,
    private val fileWrite: LoggingFixFileWriteUseCase,
    private val validation: LoggingFixValidationUseCase,
    private val i18n: I18nUseCase,
) {
    typealias StartRequest = LoggingFixStartRequest

    @PostMapping
    fun start(
        @RequestBody req: StartRequest,
    ): ResponseEntity<Map<String, Any>> {
        if (req.logText.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("logging_fix.be_log_text_empty")))
        }
        if (req.logText.length > MAX_LOG_CHARS) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("logging_fix.be_log_text_too_long")))
        }
        val repoErrors = validation.validateStartRequest(req)
        if (repoErrors.isNotEmpty()) {
            val localized = i18n.t("logging_fix.be_log_text_unparseable")
            val reason = repoErrors.firstOrNull { it.isNotBlank() } ?: "input rejected by pre-check"
            return ResponseEntity
                .status(422)
                .body(mapOf("error" to localized, "reason" to reason))
        }
        val id =
            lifecycle.startFix(
                logText = req.logText,
                selectedCategories = req.selectedCategories,
                localRepoConfigId = req.localRepoConfigId,
                language = req.language.ifBlank { "de" },
            )
        return ResponseEntity.ok(mapOf("id" to id, "status" to "PENDING"))
    }

    @GetMapping("/{id}")
    fun status(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val analysis = lifecycle.getAnalysis(id) ?: return ResponseEntity.notFound().build()
        val stages = stageOutput.getStageOutputs(id)
        return ResponseEntity.ok(
            mapOf(
                "id" to analysis.id,
                "status" to analysis.status,
                "language" to analysis.language,
                "selectedCategories" to analysis.selectedCategories.split(",").filter { it.isNotBlank() },
                "localRepoConfigId" to analysis.localRepoConfigId,
                "errorMessage" to analysis.errorMessage,
                "currentStage" to stages?.currentStage,
                "createdAt" to analysis.createdAt.toString(),
                "completedAt" to analysis.completedAt?.toString(),
                "stages" to stages?.stages,
            ),
        )
    }

    @GetMapping
    fun list(
        @RequestParam(required = false, defaultValue = "10") limit: Int,
    ): List<Map<String, Any?>> =
        lifecycle.listRecent(limit.coerceIn(1, 100)).map {
            mapOf(
                "id" to it.id,
                "status" to it.status,
                "featureSlug" to it.featureSlug,
                "language" to it.language,
                "selectedCategories" to it.selectedCategories.split(",").filter { it.isNotBlank() },
                "localRepoConfigId" to it.localRepoConfigId,
                "createdAt" to it.createdAt.toString(),
            )
        }

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Long,
    ): ResponseEntity<Void> =
        when (lifecycle.cancel(id)) {
            CancelOutcome.Cancelled -> ResponseEntity.ok().build()
            CancelOutcome.NotFound -> ResponseEntity.notFound().build()
            CancelOutcome.AlreadyFinished -> ResponseEntity.status(409).build()
        }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Void> =
        when (lifecycle.delete(id)) {
            DeleteOutcome.Deleted -> ResponseEntity.noContent().build()
            DeleteOutcome.NotFound -> ResponseEntity.notFound().build()
            DeleteOutcome.StillRunning -> ResponseEntity.status(409).build()
        }

    @DeleteMapping("/finished")
    fun deleteAllFinished(): ResponseEntity<Map<String, Any>> = ResponseEntity.ok(mapOf("deleted" to lifecycle.deleteAllFinished()))

    @GetMapping("/{id}/write-files-preview")
    fun previewWriteFiles(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        val preview = fileWrite.getWriteFilesPreview(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "repoPath" to preview.repoPath,
                "files" to
                    preview.files.map {
                        mapOf(
                            "path" to it.path,
                            "content" to it.content,
                            "description" to it.description,
                        )
                    },
            ),
        )
    }

    @PostMapping("/{id}/write-files")
    fun writeFiles(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Any?>> {
        if (lifecycle.getAnalysis(id) == null) return ResponseEntity.notFound().build()
        val result = fileWrite.writeFiles(id)
        if (result.success) {
            return ResponseEntity.ok(mapOf("success" to true, "writtenFiles" to result.writtenFiles))
        }
        return ResponseEntity.status(500).body(mapOf("success" to false, "error" to result.error))
    }

    companion object {
        private const val MAX_LOG_CHARS = 50_000
    }
}
