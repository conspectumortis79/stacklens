package com.stacklens.application.port.inbound.imageextraction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OcrEngineTest {
    @Test
    fun `fromString returns TESSERACT_ONLY for null input`() {
        assertEquals(OcrEngine.TESSERACT_ONLY, OcrEngine.fromString(null))
    }

    @Test
    fun `fromString returns TESSERACT_ONLY for blank input`() {
        assertEquals(OcrEngine.TESSERACT_ONLY, OcrEngine.fromString(""))
        assertEquals(OcrEngine.TESSERACT_ONLY, OcrEngine.fromString("   "))
    }

    @Test
    fun `fromString returns TESSERACT_ONLY for unknown values`() {
        assertEquals(OcrEngine.TESSERACT_ONLY, OcrEngine.fromString("unknown"))
        assertEquals(OcrEngine.TESSERACT_ONLY, OcrEngine.fromString("gpt-vision"))
    }

    @Test
    fun `fromString maps tesseract aliases to TESSERACT_ONLY`() {
        assertEquals(OcrEngine.TESSERACT_ONLY, OcrEngine.fromString("tesseract"))
        assertEquals(OcrEngine.TESSERACT_ONLY, OcrEngine.fromString("TESSERACT"))
        assertEquals(OcrEngine.TESSERACT_ONLY, OcrEngine.fromString("Tesseract_Only"))
    }

    @Test
    fun `fromString maps ai aliases to AI_FIRST`() {
        assertEquals(OcrEngine.AI_FIRST, OcrEngine.fromString("ai"))
        assertEquals(OcrEngine.AI_FIRST, OcrEngine.fromString("AI_FIRST"))
        assertEquals(OcrEngine.AI_FIRST, OcrEngine.fromString("ai_first"))
    }

    @Test
    fun `ImageExtractionRequest defaults ocrEngine to TESSERACT_ONLY`() {
        val req =
            ImageExtractionRequest(
                sourcePath = "/tmp/doc.png",
                originalFileName = "doc.png",
                mimeType = "image/png",
                fileSizeBytes = 1,
            )
        assertEquals(OcrEngine.TESSERACT_ONLY, req.ocrEngine)
    }

    @Test
    fun `extractFromUpload parameter defaults ocrEngine to TESSERACT_ONLY`() {
        val useCase =
            object : ImageExtractionUseCase {
                override fun extractFromServerFile(req: ImageExtractionRequest): ImageExtractionResponse {
                    error("not used in this test")
                }

                override fun extractFromUpload(
                    bytes: ByteArray,
                    originalFileName: String,
                    mimeType: String,
                    saveToServerPath: String?,
                    ocrEngine: OcrEngine,
                ): ImageExtractionResponse {
                    assertEquals(OcrEngine.TESSERACT_ONLY, ocrEngine)
                    return ImageExtractionResponse(
                        recordId = 0L,
                        status = "COMPLETED",
                        extractedText = null,
                        outputFilePath = null,
                        errorMessage = null,
                        engineUsed = "tesseract",
                    )
                }
            }
        useCase.extractFromUpload(
            bytes = ByteArray(0),
            originalFileName = "doc.png",
            mimeType = "image/png",
        )
    }
}
