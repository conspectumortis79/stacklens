package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.imageextraction.ImageExtractionRequest
import com.stacklens.application.port.out.ImageExtractionPort
import com.stacklens.application.service.imageextraction.ImageExtractionAi
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ImageExtractionRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

class ImageExtractionServiceProbeTest {
    private val aiService: ImageExtractionAi = mock()

    private val repo: ImageExtractionPort = mock()

    private val service =
        ImageExtractionService(
            aiService = aiService,
            aiUsageContext = FakeAiUsageContextPort(),
            repo = repo,
            startupHealer = StartupHealer(),
            fileSystem = JavaFileSystemAdapter(),
        )

    private val historyService = ImageExtractionHistoryService(repo)

    private fun record(
        id: Long,
        status: String,
        name: String = "bild.png",
    ): ImageExtractionRecord =
        ImageExtractionRecord(
            id = id,
            originalFileName = name,
            sourcePath = "/srv/$name",
            mimeType = "image/png",
            fileSizeBytes = 10,
            extractedText = "text",
            outputFilePath = "/srv/$name.txt",
            status = status,
            errorMessage = null,
        )

    @Test
    fun `the startup healer cancels running and pending extractions and keeps their data`() {
        val stale = listOf(record(1L, AnalysisStatus.RUNNING), record(2L, AnalysisStatus.PENDING), record(3L, AnalysisStatus.COMPLETED))
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenReturn(stale)
        val saved = argumentCaptor<ImageExtractionRecord>()
        whenever(repo.saveImageExtraction(any())).thenAnswer { it.arguments[0] }

        service.heal()

        verify(repo, org.mockito.kotlin.times(2)).saveImageExtraction(saved.capture())
        assertEquals(listOf(1L, 2L), saved.allValues.map { it.id })
        assertTrue(saved.allValues.all { it.status == AnalysisStatus.CANCELLED }, saved.allValues.toString())
        assertEquals("bild.png", saved.allValues.first().originalFileName)
        assertEquals("/srv/bild.png.txt", saved.allValues.first().outputFilePath)
        assertEquals("text", saved.allValues.first().extractedText)
    }

    @Test
    fun `a failing save during the startup heal does not break the boot`() {
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(record(4L, AnalysisStatus.PENDING)))
        whenever(repo.saveImageExtraction(any())).thenThrow(IllegalStateException("db weg"))

        service.resetStaleExtractions()

        verify(repo).saveImageExtraction(any())
    }

    @Test
    fun `an unreadable history does not break the startup heal`() {
        whenever(repo.findAllByOrderByCreatedAtDesc()).thenThrow(IllegalStateException("tabelle fehlt"))

        service.resetStaleExtractions()

        verify(repo, org.mockito.kotlin.never()).saveImageExtraction(any())
    }

    @Test
    fun `the history use case delegates to the repository`() {
        val recent = listOf(record(5L, AnalysisStatus.COMPLETED))
        whenever(repo.findRecentImageExtractions(any())).thenReturn(recent)
        whenever(repo.findImageExtractionById(5L)).thenReturn(recent.first())

        assertEquals(recent, historyService.getAllRecords())
        assertEquals(recent.first(), historyService.getRecord(5L))
        assertNull(historyService.getRecord(404L))

        historyService.deleteRecord(5L)

        verify(repo).deleteImageExtractionById(5L)
    }

    @Test
    fun `a server file extraction without a target path keeps the output empty`(
        @TempDir tmp: Path,
    ) {
        val source = File(tmp.toFile(), "bild.png").apply { writeBytes(byteArrayOf(1, 2)) }
        stubAi()

        val response =
            service.extractFromServerFile(
                ImageExtractionRequest(
                    originalFileName = "bild.png",
                    sourcePath = source.absolutePath,
                    mimeType = "image/png",
                    fileSizeBytes = 2,
                    saveToServerPath = "   ",
                ),
            )

        assertEquals(AnalysisStatus.COMPLETED, response.status)
        assertNull(response.outputFilePath)
    }

    @Test
    fun `a target path that is a file cannot receive the text`(
        @TempDir tmp: Path,
    ) {
        val source = File(tmp.toFile(), "bild.png").apply { writeBytes(byteArrayOf(1, 2)) }
        val blocked = File(tmp.toFile(), "ziel").apply { writeText("ich bin eine datei") }
        stubAi()

        val response =
            service.extractFromServerFile(
                ImageExtractionRequest(
                    originalFileName = "bild.png",
                    sourcePath = source.absolutePath,
                    mimeType = "image/png",
                    fileSizeBytes = 2,
                    saveToServerPath = blocked.absolutePath,
                ),
            )

        assertNull(response.outputFilePath)
        assertEquals(AnalysisStatus.COMPLETED, response.status)
    }

    @Test
    fun `a target directory that cannot be created is reported without an output path`(
        @TempDir tmp: Path,
    ) {
        val source = File(tmp.toFile(), "bild.png").apply { writeBytes(byteArrayOf(1, 2)) }
        val parentFile = File(tmp.toFile(), "nur-eine-datei").apply { writeText("x") }
        stubAi()

        val response =
            service.extractFromServerFile(
                ImageExtractionRequest(
                    originalFileName = "bild.png",
                    sourcePath = source.absolutePath,
                    mimeType = "image/png",
                    fileSizeBytes = 2,
                    saveToServerPath = File(parentFile, "kind").absolutePath,
                ),
            )

        assertNull(response.outputFilePath)
    }

    @Test
    fun `a failing text write is swallowed and reported as no output`(
        @TempDir tmp: Path,
    ) {
        val source = File(tmp.toFile(), "bild.png").apply { writeBytes(byteArrayOf(1, 2)) }
        val targetDir = File(tmp.toFile(), "ziel").apply { mkdirs() }
        File(targetDir, "bild.txt").mkdirs()
        stubAi()

        val response =
            service.extractFromServerFile(
                ImageExtractionRequest(
                    originalFileName = "bild.png",
                    sourcePath = source.absolutePath,
                    mimeType = "image/png",
                    fileSizeBytes = 2,
                    saveToServerPath = targetDir.absolutePath,
                ),
            )

        assertNull(response.outputFilePath)
    }

    @Test
    fun `an ai failure without a message uses the fallback text`(
        @TempDir tmp: Path,
    ) {
        val source = File(tmp.toFile(), "bild.png").apply { writeBytes(byteArrayOf(1, 2)) }
        whenever(repo.saveImageExtraction(any())).thenAnswer { it.arguments[0] }
        whenever(repo.findImageExtractionById(any())).thenReturn(record(9L, AnalysisStatus.RUNNING))
        whenever(aiService.buildExtractPrompt(eq("bild.png"), any())).thenReturn("prompt")
        whenever(aiService.extractTextFromImage(any(), any(), any(), any())).thenThrow(IllegalStateException())

        val response =
            service.extractFromServerFile(
                ImageExtractionRequest(
                    originalFileName = "bild.png",
                    sourcePath = source.absolutePath,
                    mimeType = "image/png",
                    fileSizeBytes = 2,
                ),
            )

        assertEquals(AnalysisStatus.FAILED, response.status)
        assertEquals("Unbekannter Fehler", response.errorMessage)
    }

    @Test
    fun `a missing record after the run keeps the original record id`(
        @TempDir tmp: Path,
    ) {
        val source = File(tmp.toFile(), "bild.png").apply { writeBytes(byteArrayOf(1, 2)) }
        whenever(repo.saveImageExtraction(any())).thenAnswer { it.arguments[0] }
        whenever(aiService.buildExtractPrompt(any(), any())).thenReturn("prompt")
        whenever(aiService.extractTextFromImage(any(), any(), any(), any())).thenReturn("roh")
        whenever(aiService.parseExtractionResponse("roh")).thenReturn("extrahierter text")
        whenever(repo.findImageExtractionById(any())).thenReturn(null)

        val response =
            service.extractFromServerFile(
                ImageExtractionRequest(
                    originalFileName = "bild.png",
                    sourcePath = source.absolutePath,
                    mimeType = "image/png",
                    fileSizeBytes = 2,
                ),
            )

        assertEquals(0L, response.recordId)
        assertEquals(AnalysisStatus.COMPLETED, response.status)
    }

    private fun stubAi() {
        whenever(repo.saveImageExtraction(any())).thenAnswer { it.arguments[0] }
        whenever(repo.findImageExtractionById(any())).thenReturn(record(7L, AnalysisStatus.RUNNING))
        whenever(aiService.buildExtractPrompt(any(), any())).thenReturn("prompt")
        whenever(aiService.extractTextFromImage(any(), any(), any(), any())).thenReturn("roh")
        whenever(aiService.parseExtractionResponse("roh")).thenReturn("extrahierter text")
    }
}
