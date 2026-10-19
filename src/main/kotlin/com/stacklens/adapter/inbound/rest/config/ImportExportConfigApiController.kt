package com.stacklens.adapter.inbound.rest.config

import com.stacklens.adapter.inbound.rest.ExportRequestDto
import com.stacklens.adapter.inbound.rest.ImportFromPathRequest
import com.stacklens.adapter.inbound.rest.SaveConfigToPathRequest
import com.stacklens.application.port.inbound.config.ConfigExportUseCase
import com.stacklens.application.port.inbound.config.ConfigImportUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/config")
class ImportExportConfigApiController(
    private val configExport: ConfigExportUseCase,
    private val configImport: ConfigImportUseCase,
    private val workspaceFiles: WorkspaceFileUseCase,
    private val i18n: I18nUseCase,
    private val exportDtoMapper: ConfigExportDtoMapper,
    private val importMapper: ConfigImportMapper,
    private val envelopeCodec: ConfigEnvelopeCodec,
) {
    private val log = LoggerFactory.getLogger(ImportExportConfigApiController::class.java)

    @GetMapping("/any")
    fun hasAnyConfig(): Map<String, Boolean> = mapOf("configured" to configImport.hasAnyConfig())

    @PostMapping("/export")
    fun exportConfig(
        @RequestBody req: ExportRequestDto,
    ): ResponseEntity<ByteArray> {
        if (req.password.isBlank()) {
            return ResponseEntity.badRequest().body(i18n.t("config.be_password_empty").toByteArray())
        }
        val snapshot = configExport.snapshot(req.uiPreferences)
        val envelopeJson = envelopeCodec.encrypt(exportDtoMapper.toDto(snapshot), req.password)
        val filename = "stacklens-config-${snapshot.exportedAt.toLocalDate()}.json"
        return ResponseEntity
            .ok()
            .header("Content-Disposition", "attachment; filename=\"$filename\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(envelopeJson)
    }

    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importConfig(
        @RequestPart("file") file: MultipartFile,
        @RequestPart("password", required = false) password: String?,
    ): ResponseEntity<Map<String, Any>> =
        try {
            importConfigFromBytes(file.bytes, password?.takeIf { it.isNotBlank() }, false)
        } catch (e: Exception) {
            log.error("Config import (multipart) failed: ${e.javaClass.simpleName}: ${e.message}", e)
            internalImportError(e)
        }

    @PostMapping("/import-from-path")
    fun importConfigFromPath(
        @RequestBody req: ImportFromPathRequest,
    ): ResponseEntity<Map<String, Any>> =
        try {
            val bytes =
                workspaceFiles.readBytes(req.path)
                    ?: return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_file_not_found")))
            importConfigFromBytes(bytes, req.password?.takeIf { it.isNotBlank() }, req.skipIfNotEmpty)
        } catch (e: Exception) {
            log.error("Config import from path failed: ${e.javaClass.simpleName}: ${e.message}", e)
            internalImportError(e)
        }

    @PostMapping("/save-to-path")
    fun saveConfigToPath(
        @RequestBody req: SaveConfigToPathRequest,
    ): ResponseEntity<Map<String, Any>> {
        val result =
            workspaceFiles.saveText(
                TextFileSaveRequest(
                    directory = req.path,
                    filename = req.filename,
                    content = req.content,
                ),
            )
        return when (result) {
            is WorkspaceWriteResult.Saved -> ResponseEntity.ok(mapOf("savedPath" to result.path))
            is WorkspaceWriteResult.Rejected ->
                when (result.reason) {
                    WorkspaceWriteRejection.DIRECTORY_NOT_FOUND ->
                        ResponseEntity.badRequest().body(mapOf("error" to i18n.t("config.be_dir_not_found")))
                    else ->
                        ResponseEntity.internalServerError().body(mapOf("error" to i18n.t("config.be_save_error", "")))
                }
        }
    }

    private fun importConfigFromBytes(
        bytes: ByteArray,
        password: String?,
        skipIfNotEmpty: Boolean,
    ): ResponseEntity<Map<String, Any>> =
        when (val envelope = envelopeCodec.decode(bytes, password)) {
            is ConfigEnvelopeOutcome.Rejected ->
                ResponseEntity.badRequest().body(mapOf("error" to rejectionMessage(envelope)))
            is ConfigEnvelopeOutcome.Loaded -> {
                val stats = configImport.importConfigs(importMapper.toRequest(envelope.dto), skipIfNotEmpty)
                ResponseEntity.ok(mapOf("success" to true, "imported" to stats))
            }
        }

    private fun rejectionMessage(rejection: ConfigEnvelopeOutcome.Rejected): String =
        when (rejection.reason) {
            ConfigEnvelopeRejection.NEEDS_PASSWORD -> i18n.t("config.be_encrypted_need_password")
            ConfigEnvelopeRejection.WRONG_PASSWORD -> i18n.t("config.be_wrong_password")
            ConfigEnvelopeRejection.DECRYPT_FAILED -> i18n.t("config.be_decrypt_failed", rejection.detail)
            ConfigEnvelopeRejection.INVALID_JSON -> i18n.t("config.be_invalid_json", rejection.detail)
        }

    private fun internalImportError(e: Exception): ResponseEntity<Map<String, Any>> =
        ResponseEntity.internalServerError().body(
            mapOf(
                "error" to
                    i18n.t(
                        "config.be_import_failed",
                        e.javaClass.simpleName,
                        e.message?.take(500) ?: i18n.t("config.be_unknown_error"),
                    ),
            ),
        )
}
