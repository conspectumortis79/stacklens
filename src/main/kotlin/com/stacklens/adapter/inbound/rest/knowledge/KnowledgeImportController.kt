package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeBundleExportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundleImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundlePreview
import com.stacklens.application.port.inbound.knowledge.KnowledgeExportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeLocalFileImportUseCase
import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportPreview
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.model.KnowledgeImportResult
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

@RestController
@RequestMapping("/api/knowledge")
class KnowledgeImportController(
    private val exportUseCase: KnowledgeExportUseCase,
    private val importUseCase: KnowledgeImportUseCase,
    private val bundleExportUseCase: KnowledgeBundleExportUseCase,
    private val bundleImportUseCase: KnowledgeBundleImportUseCase,
    private val localFileImportUseCase: KnowledgeLocalFileImportUseCase,
) {
    @PostMapping("/bases/{id}/export")
    fun exportBase(
        @PathVariable id: Long,
        @RequestBody body: ExportBaseRequest,
    ): ResponseEntity<ByteArray> {
        val bytes =
            exportUseCase.exportBase(
                knowledgeBaseId = id,
                options =
                    KnowledgeExportOptions(
                        includeVectors = body.includeVectors ?: true,
                        includeSourceDefinitions = body.includeSourceDefinitions ?: true,
                        includeEmbeddingProfile = body.includeEmbeddingProfile ?: true,
                    ),
                password = body.password,
            )
        val filename = "stacklens-knowledge-$id-${LocalDate.now()}.slknowledge"
        return ResponseEntity
            .ok()
            .header("Content-Disposition", "attachment; filename=\"$filename\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(bytes)
    }

    @PostMapping("/export-all")
    fun exportAll(
        @RequestBody body: ExportAllRequest,
    ): ResponseEntity<ByteArray> {
        val bytes =
            bundleExportUseCase.exportAll(
                options = KnowledgeExportOptions(),
                password = body.password,
            )
        val filename = "stacklens-knowledge-all-${LocalDate.now()}.slbundle"
        return ResponseEntity
            .ok()
            .header("Content-Disposition", "attachment; filename=\"$filename\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(bytes)
    }

    @PostMapping(value = ["/import-bundle"], consumes = ["multipart/form-data"])
    fun importBundle(
        @RequestPart("file") file: MultipartFile,
        @RequestPart(value = "password", required = false) password: String?,
        @RequestPart(value = "mode", required = false) mode: String?,
    ): List<KnowledgeImportResult> {
        val parsedMode = parseMode(mode)
        return bundleImportUseCase.import(
            bundleBytes = file.bytes,
            password = requirePassword(password),
            mode = parsedMode,
        )
    }

    @PostMapping(value = ["/import-bundle-local"], consumes = ["application/json"])
    fun importBundleLocal(
        @RequestBody body: LocalBundleImportRequest,
    ): List<KnowledgeImportResult> = localFileImportUseCase.importFromLocalFile(body.path, body.password, parseMode(body.mode))

    @PostMapping(value = ["/import-bundle/inspect"], consumes = ["multipart/form-data"])
    fun inspectBundleImport(
        @RequestPart("file") file: MultipartFile,
    ): KnowledgeBundlePreview = bundleImportUseCase.inspect(file.bytes)

    @PostMapping("/import/inspect")
    fun inspectImport(
        @RequestPart("file") file: MultipartFile,
        @RequestPart(value = "password", required = false) password: String?,
    ): KnowledgeImportPreview = exportUseCase.inspect(file.bytes, password?.takeIf { it.isNotBlank() })

    @PostMapping(value = ["/import"], consumes = ["multipart/form-data"])
    fun importBase(
        @RequestPart("file") file: MultipartFile,
        @RequestPart(value = "password", required = false) password: String?,
        @RequestPart(value = "mode", required = false) mode: String?,
        @RequestPart(value = "targetKnowledgeBaseId", required = false) targetKnowledgeBaseId: Long?,
        @RequestPart(value = "includeVectors", required = false) includeVectors: String?,
    ): KnowledgeImportResult {
        val parsedMode = parseMode(mode)
        val includeVecs = includeVectors?.toBoolean() ?: true
        return importUseCase.import(
            KnowledgeImportRequest(
                archiveBytes = file.bytes,
                password = password?.takeIf { it.isNotBlank() },
                mode = parsedMode,
                targetKnowledgeBaseId = targetKnowledgeBaseId,
                includeVectors = includeVecs,
            ),
        )
    }

    @PostMapping("/import-local")
    fun importLocal(
        @RequestBody body: LocalImportRequest,
    ): KnowledgeImportResult = localFileImportUseCase.importAggregatedFromLocalFile(body.path, body.password, parseMode(body.mode)).first()

    private fun parseMode(mode: String?): KnowledgeImportMode =
        KnowledgeImportMode.entries.firstOrNull { it.name.equals(mode, ignoreCase = true) }
            ?: KnowledgeImportMode.NEW_COPY

    private fun requirePassword(password: String?): String =
        password?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Passwort ist erforderlich")
}

data class ExportBaseRequest(
    val password: String,
    val includeVectors: Boolean? = null,
    val includeSourceDefinitions: Boolean? = null,
    val includeEmbeddingProfile: Boolean? = null,
)

data class ExportAllRequest(
    val password: String,
)

data class LocalImportRequest(
    val path: String,
    val password: String?,
    val mode: String?,
)

data class LocalBundleImportRequest(
    val path: String,
    val password: String?,
    val mode: String?,
)
