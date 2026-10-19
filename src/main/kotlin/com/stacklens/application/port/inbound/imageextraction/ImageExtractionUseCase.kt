package com.stacklens.application.port.inbound.imageextraction

import com.stacklens.domain.model.ImageExtractionRecord

enum class OcrEngine {
    AI_FIRST,
    TESSERACT_ONLY,
    ;

    companion object {
        fun fromString(value: String?): OcrEngine =
            when (value?.lowercase()) {
                "ai", "ai_first" -> AI_FIRST
                "tesseract", "tesseract_only" -> TESSERACT_ONLY
                else -> TESSERACT_ONLY
            }
    }
}

data class ImageExtractionRequest(
    val sourcePath: String,
    val originalFileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val saveToServerPath: String? = null,
    val ocrEngine: OcrEngine = OcrEngine.TESSERACT_ONLY,
)

data class ImageExtractionResponse(
    val recordId: Long,
    val status: String,
    val extractedText: String?,
    val outputFilePath: String?,
    val errorMessage: String?,
    val engineUsed: String,
)

interface ImageExtractionUseCase {
    fun extractFromServerFile(req: ImageExtractionRequest): ImageExtractionResponse

    fun extractFromUpload(
        bytes: ByteArray,
        originalFileName: String,
        mimeType: String,
        saveToServerPath: String? = null,
        ocrEngine: OcrEngine = OcrEngine.TESSERACT_ONLY,
    ): ImageExtractionResponse
}

interface ImageExtractionHistoryUseCase {
    fun getAllRecords(): List<ImageExtractionRecord>

    fun getRecord(id: Long): ImageExtractionRecord?

    fun deleteRecord(id: Long)

    fun deleteAllRecords()
}
