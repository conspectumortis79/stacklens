package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.imageextraction.ImageExtractionHistoryUseCase
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionRequest
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionResponse
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionUseCase
import com.stacklens.application.port.inbound.imageextraction.OcrEngine
import com.stacklens.domain.model.ImageExtractionRecord
import com.stacklens.testsupport.FakeI18n
import com.stacklens.testsupport.FakeUpload
import com.stacklens.testsupport.StaleAnalysisStatusFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime

class ImageExtractionApiControllerBranchCoverageTest {
    private val i18n = FakeI18n()

    private val service = RecordingImageExtraction()

    private val history = RecordingHistory()

    private val controller = ImageExtractionApiController(service, history, i18n, StaleAnalysisStatusFixtures.withRealClock())

    private fun upload(
        name: String?,
        mime: String?,
    ): MultipartFile = FakeUpload(fileName = name, mime = mime, content = ByteArray(4))

    private fun serverRequest(
        sourcePath: String = "/srv/doc.png",
        originalFileName: String = "doc.png",
        mimeType: String = "image/png",
    ): ImageExtractionApiController.ExtractFromServerRequest =
        ImageExtractionApiController.ExtractFromServerRequest(
            sourcePath = sourcePath,
            originalFileName = originalFileName,
            mimeType = mimeType,
        )

    @Suppress("UNCHECKED_CAST")
    private fun errorOf(response: Any?): String? = (response as Map<String, Any?>)["error"] as String?

    @Test
    fun `server request fills optional fields with defaults`() {
        val req = serverRequest()
        assertEquals(0L, req.fileSizeBytes)
        assertEquals(null, req.saveToServerPath)
        assertEquals(null, req.ocrEngine)
    }

    @Test
    fun `extract from server forwards engine and save path`() {
        val resp =
            controller.extractFromServer(
                ImageExtractionApiController.ExtractFromServerRequest(
                    sourcePath = "/srv/doc.tiff",
                    originalFileName = "doc.tiff",
                    mimeType = "image/tiff",
                    fileSizeBytes = 12L,
                    saveToServerPath = "/srv/copy.tiff",
                    ocrEngine = "ai",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
        val forwarded = service.serverCalls.single()
        assertEquals("/srv/copy.tiff", forwarded.saveToServerPath)
        assertEquals(OcrEngine.AI_FIRST, forwarded.ocrEngine)
        assertEquals(12L, forwarded.fileSizeBytes)
    }

    @Test
    fun `extract from server rejects blank sources and unsupported types`() {
        assertEquals("i18n:image_extraction.be_source_required", errorOf(controller.extractFromServer(serverRequest(sourcePath = " ")).body))
        assertEquals(
            "i18n:image_extraction.be_unsupported_type",
            errorOf(controller.extractFromServer(serverRequest(originalFileName = "notes.txt", mimeType = "application/pdf")).body),
        )
        assertEquals(HttpStatus.OK, controller.extractFromServer(serverRequest(originalFileName = "notes.txt", mimeType = "IMAGE/PNG")).statusCode)
        assertEquals(
            "image/png",
            service.serverCalls
                .single()
                .mimeType
                .lowercase(),
        )
    }

    @Test
    fun `extract from server answers bad request for illegal arguments with and without message`() {
        service.failure = IllegalArgumentException("nope")
        assertEquals("nope", errorOf(controller.extractFromServer(serverRequest()).body))
        service.failure = IllegalArgumentException()
        assertEquals("i18n:common.be_unknown_error", errorOf(controller.extractFromServer(serverRequest()).body))
    }

    @Test
    fun `extract from server answers internal error for failures with and without message`() {
        service.failure = IllegalStateException("crash")
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.extractFromServer(serverRequest()).statusCode)
        assertEquals("crash", errorOf(controller.extractFromServer(serverRequest()).body))
        service.failure = IllegalStateException()
        assertEquals("i18n:common.be_unknown_error", errorOf(controller.extractFromServer(serverRequest()).body))
    }

    @Test
    fun `extract from upload normalises the optional save path`() {
        assertEquals(HttpStatus.OK, controller.extractFromUpload(upload("doc.png", "image/png"), "   ", "tesseract_only").statusCode)
        assertEquals(null, service.uploadCalls.single().saveToServerPath)
        assertEquals(OcrEngine.TESSERACT_ONLY, service.uploadCalls.single().ocrEngine)

        controller.extractFromUpload(upload("doc.png", "image/png"), "/srv/out.png", null)
        assertEquals("/srv/out.png", service.uploadCalls.last().saveToServerPath)

        controller.extractFromUpload(upload("doc.png", "image/png"), null, "unknown-engine")
        assertEquals(null, service.uploadCalls.last().saveToServerPath)
        assertEquals(OcrEngine.TESSERACT_ONLY, service.uploadCalls.last().ocrEngine)
    }

    @Test
    fun `extract from upload detects the mime type from the file name`() {
        val detected =
            mapOf(
                "scan.jpg" to "image/jpeg",
                "scan.JPEG" to "image/jpeg",
                "scan.jpeg" to "image/jpeg",
                "scan.tif" to "image/tiff",
                "scan.TIFF" to "image/tiff",
                "scan.tiff" to "image/tiff",
                "scan.png" to "image/png",
            )
        detected.forEach { (name, mime) ->
            assertEquals(HttpStatus.OK, controller.extractFromUpload(upload(name, null), null, "ai").statusCode)
            assertEquals(mime, service.uploadCalls.last().mimeType)
        }
        assertEquals(HttpStatus.BAD_REQUEST, controller.extractFromUpload(upload("plainfile", null), null, null).statusCode)
    }

    @Test
    fun `extract from upload rejects empty, unnamed and unsupported uploads`() {
        assertEquals("i18n:image_extraction.be_file_empty", errorOf(controller.extractFromUpload(emptyUpload(), null, null).body))
        assertEquals(HttpStatus.BAD_REQUEST, controller.extractFromUpload(upload(null, null), null, null).statusCode)
        assertEquals("i18n:image_extraction.be_unsupported_type", errorOf(controller.extractFromUpload(upload(null, null), null, null).body))
        assertEquals(HttpStatus.BAD_REQUEST, controller.extractFromUpload(upload("notes.txt", null), null, null).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, controller.extractFromUpload(upload("notes.txt", "application/pdf"), null, null).statusCode)
        assertEquals(HttpStatus.OK, controller.extractFromUpload(upload("notes.txt", "image/jpeg"), null, null).statusCode)
        controller.extractFromUpload(upload(null, "image/png"), null, null)
        assertEquals("uploaded.bin", service.uploadCalls.last().originalFileName)
    }

    @Test
    fun `extract from upload reports failures with and without message`() {
        service.failure = IllegalArgumentException("too big")
        assertEquals("too big", errorOf(controller.extractFromUpload(upload("doc.png", "image/png"), null, null).body))
        service.failure = IllegalArgumentException()
        assertEquals("i18n:common.be_unknown_error", errorOf(controller.extractFromUpload(upload("doc.png", "image/png"), null, null).body))
        service.failure = IllegalStateException("boom")
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.extractFromUpload(upload("doc.png", "image/png"), null, null).statusCode)
        assertEquals("boom", errorOf(controller.extractFromUpload(upload("doc.png", "image/png"), null, null).body))
        service.failure = IllegalStateException()
        assertEquals("i18n:common.be_unknown_error", errorOf(controller.extractFromUpload(upload("doc.png", "image/png"), null, null).body))
    }

    @Test
    fun `history marks stale running records and reports output files`() {
        val now = LocalDateTime.now()
        history.records =
            listOf(
                record(status = "RUNNING", createdAt = now.minusMinutes(10), outputFilePath = null),
                record(status = "RUNNING", createdAt = now.plusMinutes(1), outputFilePath = "/srv/out.png"),
                record(status = "COMPLETED", createdAt = now.minusDays(1), outputFilePath = null),
            )
        val rows = controller.listHistory()
        assertEquals("FAILED", rows[0]["status"])
        assertEquals(false, rows[0]["hasOutputFile"])
        assertEquals("RUNNING", rows[1]["status"])
        assertEquals(true, rows[1]["hasOutputFile"])
        assertEquals("COMPLETED", rows[2]["status"])
        assertEquals("doc.png", rows[0]["originalFileName"])
    }

    @Test
    fun `history lookups and deletions report missing records`() {
        history.missing = true
        assertEquals(HttpStatus.NOT_FOUND, controller.getHistory(1L).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, controller.deleteHistory(1L).statusCode)

        history.missing = false
        assertEquals(HttpStatus.OK, controller.getHistory(1L).statusCode)
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteHistory(1L).statusCode)
        assertEquals(1L, history.deleted.single())
        assertEquals(HttpStatus.OK, controller.deleteAllHistory().statusCode)
        assertEquals(true, (controller.deleteAllHistory().body as Map<*, *>)["deleted"])
    }

    private fun emptyUpload(): MultipartFile = FakeUpload(fileName = "doc.png", mime = "image/png")

    private fun record(
        status: String,
        createdAt: LocalDateTime,
        outputFilePath: String?,
    ): ImageExtractionRecord =
        ImageExtractionRecord(
            id = 3L,
            originalFileName = "doc.png",
            sourcePath = "/srv/doc.png",
            mimeType = "image/png",
            outputFilePath = outputFilePath,
            status = status,
            createdAt = createdAt,
        )

    private class RecordingImageExtraction : ImageExtractionUseCase {
        var failure: RuntimeException? = null

        val serverCalls = mutableListOf<ImageExtractionRequest>()

        val uploadCalls = mutableListOf<UploadCall>()

        data class UploadCall(
            val originalFileName: String,
            val mimeType: String,
            val saveToServerPath: String?,
            val ocrEngine: OcrEngine,
        )

        override fun extractFromServerFile(req: ImageExtractionRequest): ImageExtractionResponse {
            serverCalls += req
            return answered()
        }

        override fun extractFromUpload(
            bytes: ByteArray,
            originalFileName: String,
            mimeType: String,
            saveToServerPath: String?,
            ocrEngine: OcrEngine,
        ): ImageExtractionResponse {
            uploadCalls += UploadCall(originalFileName, mimeType, saveToServerPath, ocrEngine)
            return answered()
        }

        private fun answered(): ImageExtractionResponse {
            val pending = failure
            if (pending != null) {
                throw pending
            }
            return ImageExtractionResponse(7L, "COMPLETED", "extracted", null, null, "tesseract")
        }
    }

    private class RecordingHistory : ImageExtractionHistoryUseCase {
        var records: List<ImageExtractionRecord> = emptyList()

        var missing: Boolean = false

        val deleted: MutableList<Long> = mutableListOf()

        override fun getAllRecords(): List<ImageExtractionRecord> = records

        override fun getRecord(id: Long): ImageExtractionRecord? = if (missing) null else stored()

        override fun deleteRecord(id: Long) {
            deleted += id
        }

        override fun deleteAllRecords() {
            records = emptyList()
        }

        private fun stored(): ImageExtractionRecord =
            ImageExtractionRecord(
                id = 1L,
                originalFileName = "doc.png",
                sourcePath = "/srv/doc.png",
                mimeType = "image/png",
            )
    }
}
