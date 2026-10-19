package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionRequest
import com.stacklens.application.port.out.ImageExtractionPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.service.imageextraction.ImageExtractionAi
import com.stacklens.domain.model.ImageExtractionRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class ImageExtractionServiceTest {
    private lateinit var aiService: ImageExtractionAi
    private lateinit var aiUsageContext: AiUsageContextPort
    private lateinit var repo: ImageExtractionPort
    private lateinit var startupHealer: StartupHealer
    private lateinit var service: ImageExtractionService
    private lateinit var historyService: ImageExtractionHistoryService

    @BeforeEach
    fun setUp() {
        aiService = mock()
        aiUsageContext = FakeAiUsageContextPort()
        repo = mock()
        startupHealer = mock()
        service = ImageExtractionService(aiService, aiUsageContext, repo, startupHealer, fileSystem = JavaFileSystemAdapter())
        historyService = ImageExtractionHistoryService(repo)
    }

    @Test
    fun `extractFromUpload produces a running then completed record with extracted text`() {
        whenever(repo.saveImageExtraction(any())).thenAnswer { invocation ->
            val arg = invocation.arguments[0] as ImageExtractionRecord
            ImageExtractionRecord(
                id = 42L,
                originalFileName = arg.originalFileName,
                sourcePath = arg.sourcePath,
                mimeType = arg.mimeType,
                fileSizeBytes = arg.fileSizeBytes,
                status = arg.status,
                createdAt = arg.createdAt,
            )
        }
        whenever(repo.findImageExtractionById(42L)).thenReturn(
            ImageExtractionRecord(
                id = 42L,
                originalFileName = "doc.png",
                sourcePath = "(upload) doc.png",
                mimeType = "image/png",
                fileSizeBytes = 1024,
            ),
        )
        whenever(aiService.buildExtractPrompt("doc.png", "image/png")).thenReturn("PROMPT")
        whenever(aiService.extractTextFromImage(any(), any(), any(), any())).thenReturn("Hello World")
        whenever(aiService.parseExtractionResponse("Hello World")).thenReturn("Hello World")

        val response =
            service.extractFromUpload(
                "abc".toByteArray(),
                "doc.png",
                "image/png",
                ocrEngine = com.stacklens.application.port.inbound.imageextraction.OcrEngine.AI_FIRST,
            )

        assertEquals(42L, response.recordId)
        assertEquals("COMPLETED", response.status)
        assertEquals("Hello World", response.extractedText)
        assertEquals("ai", response.engineUsed)
        assertNull(response.errorMessage)
    }

    @Test
    fun `extractFromUpload with Tesseract-only engine uses local OCR and reports tesseract as engineUsed`() {
        whenever(repo.saveImageExtraction(any())).thenAnswer { invocation ->
            val arg = invocation.arguments[0] as ImageExtractionRecord
            ImageExtractionRecord(
                id = 77L,
                originalFileName = arg.originalFileName,
                sourcePath = arg.sourcePath,
                mimeType = arg.mimeType,
                fileSizeBytes = arg.fileSizeBytes,
                status = arg.status,
                createdAt = arg.createdAt,
            )
        }
        whenever(repo.findImageExtractionById(77L)).thenReturn(
            ImageExtractionRecord(
                id = 77L,
                originalFileName = "doc.png",
                sourcePath = "(upload) doc.png",
                mimeType = "image/png",
                fileSizeBytes = 10,
            ),
        )
        whenever(aiService.buildExtractPrompt(any(), any())).thenReturn("PROMPT")
        whenever(aiService.extractTextFromImage(any(), any(), any(), eq(true))).thenReturn("Tesseract result")
        whenever(aiService.parseExtractionResponse("Tesseract result")).thenReturn("Tesseract result")

        val response =
            service.extractFromUpload(
                "abc".toByteArray(),
                "doc.png",
                "image/png",
                ocrEngine = com.stacklens.application.port.inbound.imageextraction.OcrEngine.TESSERACT_ONLY,
            )

        assertEquals(77L, response.recordId)
        assertEquals("COMPLETED", response.status)
        assertEquals("Tesseract result", response.extractedText)
        assertEquals("tesseract", response.engineUsed)
    }

    @Test
    fun `extractFromUpload persists FAILED record when AI throws`() {
        whenever(repo.saveImageExtraction(any())).thenAnswer { invocation ->
            val arg = invocation.arguments[0] as ImageExtractionRecord
            ImageExtractionRecord(
                id = 7L,
                originalFileName = arg.originalFileName,
                sourcePath = arg.sourcePath,
                mimeType = arg.mimeType,
                fileSizeBytes = arg.fileSizeBytes,
                status = arg.status,
                createdAt = arg.createdAt,
            )
        }
        whenever(repo.findImageExtractionById(7L)).thenReturn(
            ImageExtractionRecord(
                id = 7L,
                originalFileName = "doc.png",
                sourcePath = "(upload) doc.png",
                mimeType = "image/png",
                fileSizeBytes = 1,
            ),
        )
        whenever(aiService.buildExtractPrompt(any(), any())).thenReturn("PROMPT")
        whenever(aiService.extractTextFromImage(any(), any(), any(), any())).thenThrow(RuntimeException("AI down"))

        val response =
            service.extractFromUpload(
                "abc".toByteArray(),
                "doc.png",
                "image/png",
                ocrEngine = com.stacklens.application.port.inbound.imageextraction.OcrEngine.AI_FIRST,
            )

        assertEquals("FAILED", response.status)
        assertEquals("AI down", response.errorMessage)
        assertNull(response.extractedText)
    }

    @Test
    fun `extractFromServerFile rejects missing source path`() {
        val req =
            ImageExtractionRequest(
                sourcePath = "/does/not/exist.png",
                originalFileName = "x.png",
                mimeType = "image/png",
                fileSizeBytes = 0,
            )
        assertThrows(IllegalArgumentException::class.java) {
            service.extractFromServerFile(req)
        }
    }

    @Test
    fun `extractFromServerFile writes output txt with matching base name`(
        @TempDir tmp: Path,
    ) {
        val src = tmp.resolve("invoice.png").toFile()
        src.writeText("not really png")
        val outDir = tmp.resolve("out").toFile()
        outDir.mkdirs()
        val req =
            ImageExtractionRequest(
                sourcePath = src.absolutePath,
                originalFileName = "invoice.png",
                mimeType = "image/png",
                fileSizeBytes = src.length(),
                saveToServerPath = outDir.absolutePath,
            )

        whenever(repo.saveImageExtraction(any())).thenAnswer { invocation ->
            val arg = invocation.arguments[0] as ImageExtractionRecord
            ImageExtractionRecord(
                id = 11L,
                originalFileName = arg.originalFileName,
                sourcePath = arg.sourcePath,
                mimeType = arg.mimeType,
                fileSizeBytes = arg.fileSizeBytes,
                status = arg.status,
                createdAt = arg.createdAt,
            )
        }
        whenever(repo.findImageExtractionById(11L)).thenReturn(
            ImageExtractionRecord(
                id = 11L,
                originalFileName = "invoice.png",
                sourcePath = src.absolutePath,
                mimeType = "image/png",
                fileSizeBytes = src.length(),
            ),
        )
        whenever(aiService.buildExtractPrompt(any(), any())).thenReturn("PROMPT")
        whenever(aiService.extractTextFromImage(any(), any(), any(), any())).thenReturn("INVOICE 123")
        whenever(aiService.parseExtractionResponse(any())).thenReturn("INVOICE 123")

        val response = service.extractFromServerFile(req)

        val outFile = File(outDir, "invoice.txt")
        assertTrue(outFile.exists(), "Output file should be created: ${outFile.absolutePath}")
        assertEquals("INVOICE 123", outFile.readText())
        assertEquals("INVOICE 123", response.extractedText)
        assertEquals(outFile.absolutePath, response.outputFilePath)
    }

    @Test
    fun `output filename strips only last extension from compound name`(
        @TempDir tmp: Path,
    ) {
        val src = tmp.resolve("a.b.png").toFile()
        src.writeText("x")
        val outDir = tmp.resolve("o").toFile()
        outDir.mkdirs()
        val req =
            ImageExtractionRequest(
                sourcePath = src.absolutePath,
                originalFileName = "a.b.png",
                mimeType = "image/png",
                fileSizeBytes = 1,
                saveToServerPath = outDir.absolutePath,
            )

        whenever(repo.saveImageExtraction(any())).thenAnswer { invocation ->
            val arg = invocation.arguments[0] as ImageExtractionRecord
            ImageExtractionRecord(
                id = 12L,
                originalFileName = arg.originalFileName,
                sourcePath = arg.sourcePath,
                mimeType = arg.mimeType,
                fileSizeBytes = arg.fileSizeBytes,
                status = arg.status,
                createdAt = arg.createdAt,
            )
        }
        whenever(repo.findImageExtractionById(12L)).thenReturn(
            ImageExtractionRecord(
                id = 12L,
                originalFileName = "a.b.png",
                sourcePath = src.absolutePath,
                mimeType = "image/png",
                fileSizeBytes = 1,
            ),
        )
        whenever(aiService.buildExtractPrompt(any(), any())).thenReturn("P")
        whenever(aiService.extractTextFromImage(any(), any(), any(), any())).thenReturn("text")
        whenever(aiService.parseExtractionResponse(any())).thenReturn("text")

        service.extractFromServerFile(req)
        val expected = File(outDir, "a.b.txt")
        assertTrue(expected.exists())
    }

    @Test
    fun `finalizeRecord merges provided fields onto the existing record`() {
        val existing =
            ImageExtractionRecord(
                id = 5L,
                originalFileName = "doc.png",
                sourcePath = "/x",
                mimeType = "image/png",
                fileSizeBytes = 1,
                status = "RUNNING",
            )
        whenever(repo.findImageExtractionById(5L)).thenReturn(existing)
        whenever(repo.saveImageExtraction(any())).thenAnswer { it.arguments[0] as ImageExtractionRecord }

        val out = service.finalizeRecord(5L, "COMPLETED", "TXT", "/out.txt", null)

        assertNotNull(out)
        val captor = argumentCaptor<ImageExtractionRecord>()
        verify(repo).saveImageExtraction(captor.capture())
        val saved = captor.lastValue
        assertEquals(5L, saved.id)
        assertEquals("COMPLETED", saved.status)
        assertEquals("TXT", saved.extractedText)
        assertEquals("/out.txt", saved.outputFilePath)
    }

    @Test
    fun `finalizeRecord returns null when record is missing`() {
        whenever(repo.findImageExtractionById(99L)).thenReturn(null)
        assertNull(service.finalizeRecord(99L, "COMPLETED", "t", "p", null))
    }

    @Test
    fun `createRunningRecord persists RUNNING status and the provided metadata`() {
        whenever(repo.saveImageExtraction(any())).thenAnswer { invocation ->
            val arg = invocation.arguments[0] as ImageExtractionRecord
            ImageExtractionRecord(
                id = 1L,
                originalFileName = arg.originalFileName,
                sourcePath = arg.sourcePath,
                mimeType = arg.mimeType,
                fileSizeBytes = arg.fileSizeBytes,
                status = arg.status,
                createdAt = arg.createdAt,
            )
        }
        val record = service.createRunningRecord("f.png", "/tmp/f.png", "image/png", 10)
        assertEquals(1L, record.id)
        assertEquals("RUNNING", record.status)
        assertEquals("f.png", record.originalFileName)
        assertEquals("/tmp/f.png", record.sourcePath)
        assertEquals("image/png", record.mimeType)
        assertEquals(10L, record.fileSizeBytes)
    }

    @Test
    fun `deleteAll delegates to the port`() {
        historyService.deleteAllRecords()
        verify(repo).deleteAllImageExtractions()
    }
}
