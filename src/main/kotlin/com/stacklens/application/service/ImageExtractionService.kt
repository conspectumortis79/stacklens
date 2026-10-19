package com.stacklens.application.service

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionRequest
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionResponse
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionUseCase
import com.stacklens.application.port.out.ImageExtractionPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.file.FileReadWritePort
import com.stacklens.application.service.imageextraction.ImageExtractionAi
import com.stacklens.application.service.startup.StaleWorkHealer
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.AiUsageKind
import com.stacklens.domain.model.ImageExtractionRecord
import org.slf4j.LoggerFactory
import java.io.File

@ApplicationService
class ImageExtractionService(
    private val aiService: ImageExtractionAi,
    private val aiUsageContext: AiUsageContextPort,
    private val repo: ImageExtractionPort,
    private val startupHealer: StartupRecovery,
    private val fileSystem: FileReadWritePort,
) : ImageExtractionUseCase,
    StaleWorkHealer {
    private val log = LoggerFactory.getLogger(ImageExtractionService::class.java)

    override fun heal() {
        resetStaleExtractions()
    }

    fun resetStaleExtractions() =
        startupHealer.heal(
            logTag = "Image-Extraktion",
            failedStatus = AnalysisStatus.CANCELLED,
            findStale = {
                repo.findAllByOrderByCreatedAtDesc().filter {
                    it.status == AnalysisStatus.RUNNING || it.status == AnalysisStatus.PENDING
                }
            },
            markFailed = { },
            save = { old ->
                repo.saveImageExtraction(
                    ImageExtractionRecord(
                        id = old.id,
                        originalFileName = old.originalFileName,
                        sourcePath = old.sourcePath,
                        mimeType = old.mimeType,
                        fileSizeBytes = old.fileSizeBytes,
                        extractedText = old.extractedText,
                        outputFilePath = old.outputFilePath,
                        status = AnalysisStatus.CANCELLED,
                        errorMessage = old.errorMessage,
                        createdAt = old.createdAt,
                    ),
                )
            },
        )

    override fun extractFromServerFile(req: ImageExtractionRequest): ImageExtractionResponse {
        val file = File(req.sourcePath)
        if (!fileSystem.exists(file) || !fileSystem.isFile(file)) {
            throw IllegalArgumentException("Quelldatei nicht gefunden: ${req.sourcePath}")
        }
        val bytes = fileSystem.readBytes(file)
        return extractInternal(
            bytes = bytes,
            originalFileName = req.originalFileName,
            mimeType = req.mimeType,
            fileSizeBytes = req.fileSizeBytes,
            sourcePath = req.sourcePath,
            saveToServerPath = req.saveToServerPath,
            ocrEngine = req.ocrEngine,
        )
    }

    override fun extractFromUpload(
        bytes: ByteArray,
        originalFileName: String,
        mimeType: String,
        saveToServerPath: String?,
        ocrEngine: com.stacklens.application.port.inbound.imageextraction.OcrEngine,
    ): ImageExtractionResponse =
        extractInternal(
            bytes = bytes,
            originalFileName = originalFileName,
            mimeType = mimeType,
            fileSizeBytes = bytes.size.toLong(),
            sourcePath = "(upload) $originalFileName",
            saveToServerPath = saveToServerPath,
            ocrEngine = ocrEngine,
        )

    private fun extractInternal(
        bytes: ByteArray,
        originalFileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        sourcePath: String,
        saveToServerPath: String?,
        ocrEngine: com.stacklens.application.port.inbound.imageextraction.OcrEngine,
    ): ImageExtractionResponse {
        val record = createRunningRecord(originalFileName, sourcePath, mimeType, fileSizeBytes)

        val prompt = aiService.buildExtractPrompt(originalFileName, mimeType)
        val useLocalOcrOnly = ocrEngine == com.stacklens.application.port.inbound.imageextraction.OcrEngine.TESSERACT_ONLY
        val engineLabel = if (useLocalOcrOnly) "tesseract" else "ai"
        val rawResponse =
            try {
                if (useLocalOcrOnly) {
                    aiService.extractTextFromImage(prompt, bytes, mimeType, useLocalOcrOnly = true)
                } else {
                    aiUsageContext.withContext(AiUsageKind.IMAGE_EXTRACTION.id, record.id) {
                        aiService.extractTextFromImage(prompt, bytes, mimeType, useLocalOcrOnly = false)
                    }
                }
            } catch (e: Exception) {
                log.warn("Image extraction failed for {}: {}", originalFileName, e.message)
                finalizeRecord(record.id, AnalysisStatus.FAILED, null, null, e.message ?: "Unbekannter Fehler")
                return ImageExtractionResponse(
                    recordId = record.id,
                    status = AnalysisStatus.FAILED,
                    extractedText = null,
                    outputFilePath = null,
                    errorMessage = e.message ?: "Unbekannter Fehler",
                    engineUsed = engineLabel,
                )
            }

        val extractedText = aiService.parseExtractionResponse(rawResponse)
        val outputPath = persistExtractedText(originalFileName, extractedText, saveToServerPath)
        val finalRecord = finalizeRecord(record.id, AnalysisStatus.COMPLETED, extractedText, outputPath, null)
        return ImageExtractionResponse(
            recordId = finalRecord?.id ?: record.id,
            status = AnalysisStatus.COMPLETED,
            extractedText = extractedText,
            outputFilePath = outputPath,
            errorMessage = null,
            engineUsed = engineLabel,
        )
    }

    private fun persistExtractedText(
        originalFileName: String,
        extractedText: String,
        saveToServerPath: String?,
    ): String? {
        if (saveToServerPath.isNullOrBlank()) return null
        val targetDir = File(saveToServerPath)
        val baseName = originalFileName.substringBeforeLast('.', originalFileName)
        val targetFile = File(targetDir, "$baseName.txt")
        return try {
            fileSystem.writeFile(targetFile, extractedText)
            targetFile.absolutePath
        } catch (e: Exception) {
            log.warn("Could not write extracted text to {}: {}", targetFile.absolutePath, e.message)
            null
        }
    }

    fun createRunningRecord(
        originalFileName: String,
        sourcePath: String,
        mimeType: String,
        fileSizeBytes: Long,
    ): ImageExtractionRecord =
        repo.saveImageExtraction(
            ImageExtractionRecord(
                originalFileName = originalFileName,
                sourcePath = sourcePath,
                mimeType = mimeType,
                fileSizeBytes = fileSizeBytes,
                status = AnalysisStatus.RUNNING,
            ),
        )

    fun finalizeRecord(
        id: Long,
        status: String,
        extractedText: String?,
        outputFilePath: String?,
        errorMessage: String?,
    ): ImageExtractionRecord? {
        val existing = repo.findImageExtractionById(id) ?: return null
        return repo.saveImageExtraction(
            ImageExtractionRecord(
                id = existing.id,
                originalFileName = existing.originalFileName,
                sourcePath = existing.sourcePath,
                mimeType = existing.mimeType,
                fileSizeBytes = existing.fileSizeBytes,
                extractedText = extractedText ?: existing.extractedText,
                outputFilePath = outputFilePath ?: existing.outputFilePath,
                status = status,
                errorMessage = errorMessage ?: existing.errorMessage,
                createdAt = existing.createdAt,
            ),
        )
    }
}
