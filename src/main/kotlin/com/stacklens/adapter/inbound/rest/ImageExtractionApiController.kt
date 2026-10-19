package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.analysis.StaleAnalysisKind
import com.stacklens.application.port.inbound.analysis.StaleAnalysisStatusUseCase
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionHistoryUseCase
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionRequest
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionUseCase
import com.stacklens.application.port.inbound.imageextraction.OcrEngine
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.model.ImageExtractionRecord
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/image-extraction")
class ImageExtractionApiController(
    private val service: ImageExtractionUseCase,
    private val history: ImageExtractionHistoryUseCase,
    private val i18n: I18nUseCase,
    private val staleAnalysis: StaleAnalysisStatusUseCase,
) {
    private val allowedExtensions = ImageMimeTypes.extractionExtensions
    private val allowedMimeTypes = ImageMimeTypes.extractionMimeTypes

    data class ExtractFromServerRequest(
        val sourcePath: String,
        val originalFileName: String,
        val mimeType: String,
        val fileSizeBytes: Long = 0,
        val saveToServerPath: String? = null,
        val ocrEngine: String? = null,
    )

    @PostMapping("/extract")
    fun extractFromServer(
        @RequestBody req: ExtractFromServerRequest,
    ): ResponseEntity<Any> {
        if (req.sourcePath.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("image_extraction.be_source_required")))
        }
        if (!hasAllowedExtension(req.originalFileName) && !allowedMimeTypes.contains(req.mimeType.lowercase())) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("image_extraction.be_unsupported_type")))
        }
        return try {
            val response =
                service.extractFromServerFile(
                    ImageExtractionRequest(
                        sourcePath = req.sourcePath,
                        originalFileName = req.originalFileName,
                        mimeType = req.mimeType,
                        fileSizeBytes = req.fileSizeBytes,
                        saveToServerPath = req.saveToServerPath,
                        ocrEngine = OcrEngine.fromString(req.ocrEngine),
                    ),
                )
            ResponseEntity.ok(
                mapOf(
                    "recordId" to response.recordId,
                    "status" to response.status,
                    "extractedText" to response.extractedText,
                    "outputFilePath" to response.outputFilePath,
                    "errorMessage" to response.errorMessage,
                    "engineUsed" to response.engineUsed,
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: i18n.t("common.be_unknown_error"))))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: i18n.t("common.be_unknown_error"))))
        }
    }

    @PostMapping(value = ["/extract-upload"], consumes = ["multipart/form-data"])
    fun extractFromUpload(
        @RequestPart("file") file: MultipartFile,
        @RequestPart(value = "saveToServerPath", required = false) saveToServerPath: String?,
        @RequestParam(value = "engine", required = false) engine: String?,
    ): ResponseEntity<Any> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("image_extraction.be_file_empty")))
        }
        val originalName = file.originalFilename ?: "uploaded.bin"
        val mimeType = (file.contentType ?: detectMimeType(originalName)).lowercase()
        if (!hasAllowedExtension(originalName) && !allowedMimeTypes.contains(mimeType)) {
            return ResponseEntity.badRequest().body(mapOf("error" to i18n.t("image_extraction.be_unsupported_type")))
        }
        return try {
            val response =
                service.extractFromUpload(
                    bytes = file.bytes,
                    originalFileName = originalName,
                    mimeType = mimeType,
                    saveToServerPath = saveToServerPath?.takeIf { it.isNotBlank() },
                    ocrEngine = OcrEngine.fromString(engine),
                )
            ResponseEntity.ok(
                mapOf(
                    "recordId" to response.recordId,
                    "status" to response.status,
                    "extractedText" to response.extractedText,
                    "outputFilePath" to response.outputFilePath,
                    "errorMessage" to response.errorMessage,
                    "engineUsed" to response.engineUsed,
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: i18n.t("common.be_unknown_error"))))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: i18n.t("common.be_unknown_error"))))
        }
    }

    @GetMapping("/history")
    fun listHistory(): List<Map<String, Any?>> = history.getAllRecords().map { r -> renderListRow(r) }

    @GetMapping("/history/{id}")
    fun getHistory(
        @PathVariable id: Long,
    ): ResponseEntity<Any> {
        val record = history.getRecord(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(renderFullRecord(record))
    }

    @DeleteMapping("/history/{id}")
    fun deleteHistory(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        if (history.getRecord(id) == null) return ResponseEntity.notFound().build()
        history.deleteRecord(id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/history")
    fun deleteAllHistory(): ResponseEntity<Map<String, Any>> {
        history.deleteAllRecords()
        return ResponseEntity.ok(mapOf("deleted" to true))
    }

    private fun renderListRow(r: ImageExtractionRecord): Map<String, Any?> {
        val effectiveStatus = staleAnalysis.effectiveStatus(StaleAnalysisKind.IMAGE_EXTRACTION, r.status, r.createdAt)
        return mapOf(
            "id" to r.id,
            "originalFileName" to r.originalFileName,
            "mimeType" to r.mimeType,
            "fileSizeBytes" to r.fileSizeBytes,
            "hasOutputFile" to (r.outputFilePath != null),
            "status" to effectiveStatus,
            "createdAt" to r.createdAt.toString(),
        )
    }

    private fun renderFullRecord(record: ImageExtractionRecord): Map<String, Any?> =
        mapOf(
            "id" to record.id,
            "originalFileName" to record.originalFileName,
            "sourcePath" to record.sourcePath,
            "mimeType" to record.mimeType,
            "fileSizeBytes" to record.fileSizeBytes,
            "extractedText" to record.extractedText,
            "outputFilePath" to record.outputFilePath,
            "status" to record.status,
            "errorMessage" to record.errorMessage,
            "createdAt" to record.createdAt.toString(),
        )

    private fun hasAllowedExtension(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in allowedExtensions
    }

    private fun detectMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ImageMimeTypes.mimeFor(ext) ?: "application/octet-stream"
    }
}
