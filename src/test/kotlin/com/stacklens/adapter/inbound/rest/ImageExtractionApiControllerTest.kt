package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.imageextraction.ImageExtractionHistoryUseCase
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionRequest
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionResponse
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.domain.model.ImageExtractionRecord
import com.stacklens.testsupport.StaleAnalysisStatusFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ImageExtractionApiControllerTest {
    @Mock private lateinit var service: ImageExtractionUseCase

    @Mock private lateinit var history: ImageExtractionHistoryUseCase

    private val i18n: I18nUseCase =
        object : I18nUseCase, I18nPort {
            override fun tFor(
                lang: String,
                key: String,
                vararg args: Any,
            ) = key

            override fun t(
                key: String,
                vararg args: Any,
            ) = key

            override fun resolveLanguage() = "en"

            override fun translateError(e: Exception) = e.message ?: ""
        }

    private val controller by lazy { ImageExtractionApiController(service, history, i18n, StaleAnalysisStatusFixtures.withRealClock()) }

    private fun mockRecord(
        id: Long = 1L,
        status: String = "COMPLETED",
        originalFileName: String = "doc.png",
        mimeType: String = "image/png",
    ) = ImageExtractionRecord(
        id = id,
        originalFileName = originalFileName,
        sourcePath = "/x/$originalFileName",
        mimeType = mimeType,
        fileSizeBytes = 1,
        status = status,
        createdAt = LocalDateTime.now(),
    )

    private fun serverRequest(
        sourcePath: String,
        name: String,
        mime: String,
        size: Long = 0,
    ) = ImageExtractionApiController.ExtractFromServerRequest(
        sourcePath = sourcePath,
        originalFileName = name,
        mimeType = mime,
        fileSizeBytes = size,
    )

    @Test
    fun `extractFromServer returns 400 when source path is blank`() {
        val req = serverRequest("", "doc.png", "image/png")
        val result = controller.extractFromServer(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `extractFromServer returns 400 when mime type and extension are unsupported`() {
        val req = serverRequest("/tmp/doc.bmp", "doc.bmp", "image/bmp")
        val result = controller.extractFromServer(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `extractFromServer returns 200 and forwards request when extension is supported`() {
        val req = serverRequest("/tmp/doc.png", "doc.png", "image/png", size = 12)
        whenever(service.extractFromServerFile(any())).thenReturn(
            ImageExtractionResponse(
                recordId = 1L,
                status = "COMPLETED",
                extractedText = "Hello",
                outputFilePath = null,
                errorMessage = null,
                engineUsed = "ai",
            ),
        )

        val result = controller.extractFromServer(req)
        assertEquals(200, result.statusCode.value())
        verify(service).extractFromServerFile(
            ImageExtractionRequest(
                sourcePath = "/tmp/doc.png",
                originalFileName = "doc.png",
                mimeType = "image/png",
                fileSizeBytes = 12,
                saveToServerPath = null,
                ocrEngine = com.stacklens.application.port.inbound.imageextraction.OcrEngine.TESSERACT_ONLY,
            ),
        )
        val body = result.body as Map<*, *>
        assertEquals("ai", body["engineUsed"])
    }

    @Test
    fun `extractFromServer parses Tesseract engine choice and forwards it to the service`() {
        val req =
            serverRequest("/tmp/doc.png", "doc.png", "image/png").copy(ocrEngine = "tesseract")
        whenever(service.extractFromServerFile(any())).thenReturn(
            ImageExtractionResponse(
                recordId = 2L,
                status = "COMPLETED",
                extractedText = "Tesseract text",
                outputFilePath = null,
                errorMessage = null,
                engineUsed = "tesseract",
            ),
        )

        val result = controller.extractFromServer(req)
        assertEquals(200, result.statusCode.value())
        verify(service).extractFromServerFile(
            ImageExtractionRequest(
                sourcePath = "/tmp/doc.png",
                originalFileName = "doc.png",
                mimeType = "image/png",
                fileSizeBytes = 0,
                saveToServerPath = null,
                ocrEngine = com.stacklens.application.port.inbound.imageextraction.OcrEngine.TESSERACT_ONLY,
            ),
        )
    }

    @Test
    fun `extractFromServer returns 500 with the error message when the service throws`() {
        val req = serverRequest("/tmp/doc.png", "doc.png", "image/png")
        whenever(service.extractFromServerFile(any())).thenThrow(RuntimeException("boom"))

        val result = controller.extractFromServer(req)
        assertEquals(500, result.statusCode.value())
        val body = result.body as Map<*, *>
        assertEquals("boom", body["error"])
    }

    @Test
    fun `extractFromServer accepts tif and tiff extensions even with empty mime`() {
        val req = serverRequest("/tmp/x.tif", "x.tif", "")
        whenever(service.extractFromServerFile(any())).thenReturn(
            ImageExtractionResponse(recordId = 1L, status = "COMPLETED", extractedText = "t", outputFilePath = null, errorMessage = null, engineUsed = "ai"),
        )
        val result = controller.extractFromServer(req)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `extractFromUpload returns 400 for empty file`() {
        val empty = MockMultipartFile("file", "doc.png", "image/png", ByteArray(0))
        val result = controller.extractFromUpload(empty, null, null)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `extractFromUpload returns 400 for unsupported file extension`() {
        val file = MockMultipartFile("file", "doc.bmp", "image/bmp", byteArrayOf(1, 2, 3))
        val result = controller.extractFromUpload(file, null, null)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `extractFromUpload returns 200 and forwards bytes when file is valid`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val file = MockMultipartFile("file", "doc.png", "image/png", bytes)
        whenever(
            service.extractFromUpload(
                eq(bytes),
                eq("doc.png"),
                eq("image/png"),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(
            ImageExtractionResponse(
                recordId = 2L,
                status = "COMPLETED",
                extractedText = "TXT",
                outputFilePath = "/out/doc.txt",
                errorMessage = null,
                engineUsed = "ai",
            ),
        )

        val result = controller.extractFromUpload(file, "/tmp", null)
        assertEquals(200, result.statusCode.value())
        val body = result.body as Map<*, *>
        assertEquals(2L, body["recordId"])
        assertEquals("TXT", body["extractedText"])
        assertEquals("/out/doc.txt", body["outputFilePath"])
        assertEquals("ai", body["engineUsed"])
    }

    @Test
    fun `extractFromUpload passes Tesseract engine choice from query param to the service`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val file = MockMultipartFile("file", "doc.png", "image/png", bytes)
        whenever(
            service.extractFromUpload(
                eq(bytes),
                eq("doc.png"),
                eq("image/png"),
                anyOrNull(),
                eq(com.stacklens.application.port.inbound.imageextraction.OcrEngine.TESSERACT_ONLY),
            ),
        ).thenReturn(
            ImageExtractionResponse(
                recordId = 3L,
                status = "COMPLETED",
                extractedText = "OCR",
                outputFilePath = null,
                errorMessage = null,
                engineUsed = "tesseract",
            ),
        )

        val result = controller.extractFromUpload(file, null, "tesseract")
        assertEquals(200, result.statusCode.value())
        val body = result.body as Map<*, *>
        assertEquals("tesseract", body["engineUsed"])
    }

    @Test
    fun `listHistory renders each record as a flat map with stale-running normalised to FAILED`() {
        val oldRunning =
            ImageExtractionRecord(
                id = 1L,
                originalFileName = "old.png",
                sourcePath = "/x/old.png",
                mimeType = "image/png",
                fileSizeBytes = 1,
                status = "RUNNING",
                createdAt = LocalDateTime.now().minusHours(1),
            )
        val completed = mockRecord(id = 2L, status = "COMPLETED", originalFileName = "fresh.png")
        whenever(history.getAllRecords()).thenReturn(listOf(oldRunning, completed))

        val list = controller.listHistory()
        assertEquals(2, list.size)
        val first = list[0]
        assertEquals("FAILED", first["status"], "Stale RUNNING should be normalised to FAILED")
        assertEquals("old.png", first["originalFileName"])
        val second = list[1]
        assertEquals("COMPLETED", second["status"])
    }

    @Test
    fun `getHistory returns 404 when record is missing`() {
        whenever(history.getRecord(123L)).thenReturn(null)
        val result = controller.getHistory(123L)
        assertEquals(404, result.statusCode.value())
    }

    @Test
    fun `getHistory returns full record when present`() {
        whenever(history.getRecord(7L)).thenReturn(
            mockRecord(id = 7L, status = "COMPLETED", originalFileName = "doc.png"),
        )
        val result = controller.getHistory(7L)
        assertEquals(200, result.statusCode.value())
        val body = result.body as Map<*, *>
        assertEquals(7L, body["id"])
        assertEquals("doc.png", body["originalFileName"])
        assertTrue(body.containsKey("extractedText"))
    }

    @Test
    fun `deleteHistory returns 404 when record is missing`() {
        whenever(history.getRecord(404L)).thenReturn(null)
        val result = controller.deleteHistory(404L)
        assertEquals(404, result.statusCode.value())
        verify(history, never()).deleteRecord(any())
    }

    @Test
    fun `deleteHistory returns 204 when record is deleted`() {
        whenever(history.getRecord(1L)).thenReturn(mockRecord(id = 1L))
        val result = controller.deleteHistory(1L)
        assertEquals(204, result.statusCode.value())
        verify(history).deleteRecord(1L)
    }

    @Test
    fun `deleteAllHistory returns success map`() {
        val result = controller.deleteAllHistory()
        assertEquals(200, result.statusCode.value())
        assertEquals(true, (result.body as Map<*, *>)["deleted"])
        verify(history).deleteAllRecords()
    }

    @Test
    fun `jpeg extension is allowed via extension even when mime is missing`() {
        val req = serverRequest("/tmp/p.jpg", "p.jpg", "")
        whenever(service.extractFromServerFile(any())).thenReturn(
            ImageExtractionResponse(recordId = 1L, status = "COMPLETED", extractedText = "t", outputFilePath = null, errorMessage = null, engineUsed = "ai"),
        )
        val result = controller.extractFromServer(req)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `tiff mime type is allowed even when extension is empty`() {
        val req = serverRequest("/tmp/scan", "scan", "image/tiff")
        whenever(service.extractFromServerFile(any())).thenReturn(
            ImageExtractionResponse(recordId = 1L, status = "COMPLETED", extractedText = "t", outputFilePath = null, errorMessage = null, engineUsed = "ai"),
        )
        val result = controller.extractFromServer(req)
        assertEquals(200, result.statusCode.value())
    }
}
