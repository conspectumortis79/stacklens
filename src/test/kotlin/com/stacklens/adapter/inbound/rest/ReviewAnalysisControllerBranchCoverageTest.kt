package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.review.ReviewAnalysisDecodingUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryListing
import com.stacklens.application.port.inbound.workspace.WorkspaceFileInfo
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.testsupport.FakeI18n
import com.stacklens.testsupport.FakeUpload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewAnalysisControllerBranchCoverageTest {
    @Mock private lateinit var decoding: ReviewAnalysisDecodingUseCase

    private val i18n = FakeI18n()

    private val analysisService = RecordingReviewAnalysis()

    private val savedDirectories: MutableList<String> = mutableListOf()

    private val workspaceFiles: WorkspaceFileUseCase = mock()

    private val saveHandler =
        object : ReviewSaveHandler {
            override val format: String = "md"

            override fun save(ctx: ReviewSaveContext): ResponseEntity<Map<String, Any?>> {
                savedDirectories += ctx.dirPath
                return ResponseEntity.ok(mapOf("saved" to ctx.analysis.id, "suffix" to ctx.safeSuffix))
            }
        }

    @TempDir
    lateinit var tempDir: Path

    private val controller: ReviewAnalysisController by lazy {
        ReviewAnalysisController(analysisService, i18n, listOf(saveHandler), decoding, workspaceFiles)
    }

    private fun directoryEntry(
        name: String,
        path: String = "/tmp/$name",
    ) = WorkspaceDirectoryEntry(name = name, path = path, isDirectory = true, isFile = false, size = 0L)

    private fun fileEntry(
        name: String,
        path: String = "/tmp/$name",
    ) = WorkspaceDirectoryEntry(name = name, path = path, isDirectory = false, isFile = true, size = 1L)

    private fun listing(
        entries: List<WorkspaceDirectoryEntry>,
        path: String = "/tmp",
        parentPath: String? = null,
    ) = WorkspaceDirectoryListing(path = path, parentPath = parentPath, entries = entries)

    private fun directoryInfo(path: String) = WorkspaceFileInfo(name = File(path).name, path = path, size = 0L, isDirectory = true, isFile = false)

    private fun stored(
        id: Long = 5L,
        markdown: String? = null,
        htmlPath: String? = null,
        completedAt: LocalDateTime? = null,
    ): ReviewAnalysis =
        ReviewAnalysis(id = id, jiraSprintUrl = "https://jira/browse/BOARD?sprint=$id")
            .apply {
                status = "COMPLETED"
                currentStep = "done"
                resultMarkdown = markdown
                this.resultHtmlPath = htmlPath
                this.completedAt = completedAt
            }.also { analysisService.analyses[it.id] = it }

    private fun file(
        name: String,
        content: String = "content",
    ): File = File(tempDir.toFile(), name).apply { writeText(content) }

    private fun upload(name: String?): MultipartFile = FakeUpload(fieldName = "pptxFile", fileName = name, content = ByteArray(3))

    @Suppress("UNCHECKED_CAST")
    private fun errorOf(body: Any?): String? = (body as Map<String, Any?>)["error"] as String?

    @Test
    fun `browse entries yield nothing for an empty listing`() {
        assertTrue(controller.browseFileEntries(emptyList(), false).isEmpty())
    }

    @Test
    fun `browse entries keep directories and recognised documents`() {
        val entries =
            listOf(
                directoryEntry("folder"),
                fileEntry("notes.md"),
                fileEntry("REPORT.HTML"),
                fileEntry("deck.pptx"),
                fileEntry("legacy.PPT"),
                fileEntry("image.png"),
            )
        val mapped = controller.browseFileEntries(entries, false)
        assertEquals(listOf("folder", "deck.pptx", "legacy.PPT", "notes.md", "REPORT.HTML"), mapped.map { it["name"] })
        assertEquals(listOf("directory", "file", "file", "file", "file"), mapped.map { it["type"] })
    }

    @Test
    fun `browse entries skip files when only directories are requested`() {
        val entries = listOf(directoryEntry("folder"), fileEntry("notes.md"), WorkspaceDirectoryEntry("ghost", "ghost", false, false, 0L))
        val mapped = controller.browseFileEntries(entries, true)
        assertEquals(listOf("folder"), mapped.map { it["name"] })
    }

    @Test
    fun `browse files defaults to the home directory when no path is given`() {
        whenever(workspaceFiles.homeDirectory()).thenReturn("/home/tester")
        whenever(workspaceFiles.browseDirectory("/home/tester")).thenReturn(listing(emptyList(), path = "/home/tester"))
        val body = controller.browseFiles(path = null, directoriesOnly = true).body!!
        assertEquals("/home/tester", body["currentPath"])
    }

    @Test
    fun `browse files reports the file system root without a parent`() {
        whenever(workspaceFiles.browseDirectory("/")).thenReturn(
            listing(entries = listOf(directoryEntry("etc", "/etc")), path = "/", parentPath = null),
        )
        val body = controller.browseFiles(path = "/", directoriesOnly = true).body!!
        assertNull(body["parentPath"])
        assertTrue((body["items"] as List<*>).isNotEmpty())
    }

    @Test
    fun `browse files rejects paths that are not directories`() {
        whenever(workspaceFiles.browseDirectory(anyOrNull())).thenReturn(null)
        assertEquals(HttpStatus.BAD_REQUEST, controller.browseFiles(path = "/definitely/missing", directoriesOnly = true).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, controller.browseFiles(path = file("plain.txt").absolutePath, directoriesOnly = true).statusCode)
        whenever(workspaceFiles.browseDirectory(tempDir.toString())).thenReturn(listing(emptyList(), path = tempDir.toString()))
        assertEquals(HttpStatus.OK, controller.browseFiles(path = tempDir.toString(), directoriesOnly = false).statusCode)
    }

    @Test
    fun `start falls back to blank values for absent body entries`() {
        assertEquals("i18n:review_analysis.be_sprint_url_empty", errorOf(controller.start(emptyMap()).body))

        analysisService.firstActiveConfigId = null
        assertEquals("i18n:review_analysis.be_invalid_jira_config_id", errorOf(controller.start(mapOf("jiraSprintUrl" to "https://jira/sprint")).body))

        analysisService.firstActiveConfigId = 11L
        assertEquals("i18n:review_analysis.be_jira_config_not_found", errorOf(controller.start(mapOf("jiraSprintUrl" to "https://jira/sprint")).body))

        analysisService.existingConfigs += 11L
        assertEquals("i18n:review_analysis.be_instructions_empty", errorOf(controller.start(mapOf("jiraSprintUrl" to "  https://jira/sprint  ")).body))
    }

    @Test
    fun `start forwards trimmed payload values`() {
        analysisService.firstActiveConfigId = 11L
        analysisService.existingConfigs += 11L
        val body =
            controller
                .start(
                    mapOf(
                        "jiraSprintUrl" to " https://jira/sprint ",
                        "jiraConfigId" to " not-a-number ",
                        "analysisInstructions" to "  focus on risks ",
                    ),
                ).body!!
        assertEquals(5L, body["id"])
        assertEquals("PENDING", body["status"])
        assertEquals(Triple("https://jira/sprint", 11L, "focus on risks"), analysisService.plainCalls.single())
    }

    @Test
    fun `start multipart uses the placeholder name for uploads without a file name`() {
        analysisService.existingConfigs += 4L
        val response = controller.startMultipart(" https://jira/sprint ", " 4 ", " instructions ", upload(null), "  ")
        assertEquals(5L, response.body!!["id"])
        assertEquals(listOf("upload.pptx"), analysisService.uploadNames)
    }

    @Test
    fun `start multipart selects the pptx source of the analysis`() {
        analysisService.existingConfigs += 4L
        assertEquals("i18n:review_analysis.be_sprint_url_empty", errorOf(controller.startMultipart("  ", "4", "go", null, null).body))

        val ambiguous = controller.startMultipart("https://jira/sprint", "4", "go", upload("deck.pptx"), "/srv/deck.pptx").body!!
        assertEquals("i18n:review_analysis.be_pptx_ambiguous", ambiguous["error"])

        controller.startMultipart("https://jira/sprint", "4", "go", upload("deck.pptx"), null)
        assertEquals(listOf("deck.pptx"), analysisService.uploadNames)

        controller.startMultipart("https://jira/sprint", "4", "go", null, "/srv/deck.pptx")
        assertEquals(listOf("/srv/deck.pptx"), analysisService.serverPaths)

        analysisService.firstActiveConfigId = 9L
        analysisService.existingConfigs += 9L
        controller.startMultipart("https://jira/sprint", "", "go", null, null)
        assertEquals(1, analysisService.plainCalls.size)

        controller.startMultipart("https://jira/sprint", "4", "go", FakeUpload(fieldName = "pptxFile", fileName = "empty.pptx"), "   ")
        assertEquals(2, analysisService.plainCalls.size)
        assertEquals(listOf("/srv/deck.pptx"), analysisService.serverPaths)

        analysisService.firstActiveConfigId = null
        assertEquals("i18n:review_analysis.be_invalid_jira_config_id", errorOf(controller.startMultipart("https://jira/sprint", "%%", "go", null, null).body))
        assertEquals("i18n:review_analysis.be_jira_config_not_found", errorOf(controller.startMultipart("https://jira/sprint", "777", "go", null, null).body))
        assertEquals("i18n:review_analysis.be_instructions_empty", errorOf(controller.startMultipart("https://jira/sprint", "4", "   ", null, null).body))
    }

    @Test
    fun `save to path validates format and directory`() {
        stored()
        assertEquals("i18n:review_analysis.be_invalid_format", errorOf(controller.saveToPath(5L, emptyMap()).body))
        assertEquals("i18n:review_analysis.be_invalid_format", errorOf(controller.saveToPath(5L, mapOf("format" to "pdf", "path" to tempDir.toString())).body))
        assertEquals("i18n:review_analysis.be_no_dir_path", errorOf(controller.saveToPath(5L, mapOf("format" to "md")).body))
        assertEquals(HttpStatus.NOT_FOUND, controller.saveToPath(404L, mapOf("format" to "md", "path" to tempDir.toString())).statusCode)
        assertTrue(errorOf(controller.saveToPath(5L, mapOf("format" to "md", "path" to "/definitely/missing")).body)!!.startsWith("i18n:review_analysis.be_dir_not_found"))
        val plainFile = file("plain.txt")
        whenever(workspaceFiles.fileInfo(plainFile.absolutePath)).thenReturn(
            WorkspaceFileInfo(name = "plain.txt", path = plainFile.absolutePath, size = plainFile.length(), isDirectory = false, isFile = true),
        )
        assertTrue(errorOf(controller.saveToPath(5L, mapOf("format" to "md", "path" to plainFile.absolutePath)).body)!!.startsWith("i18n:review_analysis.be_dir_not_found"))

        whenever(workspaceFiles.fileInfo(tempDir.toString())).thenReturn(directoryInfo(tempDir.toString()))
        val unnamed = controller.saveToPath(5L, mapOf("format" to "MD", "path" to tempDir.toString())).body!!
        assertEquals(5L, unnamed["saved"])
        assertEquals(null, unnamed["suffix"])
        assertEquals(listOf(tempDir.toFile().canonicalPath), savedDirectories.map { File(it).canonicalPath })

        whenever(decoding.sprintName(anyOrNull())).thenReturn("Sprint 42!")
        val named = controller.saveToPath(5L, mapOf("format" to "md", "path" to tempDir.toString())).body!!
        assertEquals("Sprint_42_", named["suffix"])
    }

    @Test
    fun `download html prefers an existing pptx export without a sprint name`() {
        val export = file("pptx.html", "<html><body>pptx</body></html>")
        stored(markdown = "# ignored", htmlPath = export.absolutePath)
        whenever(workspaceFiles.readText(export.absolutePath)).thenReturn("<html><body>pptx</body></html>")
        val response = MockHttpServletResponse()
        controller.downloadHtml(5L, response)
        assertTrue(response.getContentAsString().contains("pptx"))
        assertEquals("attachment; filename=\"PPTX-Analyse.html\"", response.getHeader("Content-Disposition"))

        whenever(decoding.sprintName(anyOrNull())).thenReturn("Sprint 42!")
        val named = MockHttpServletResponse()
        controller.downloadHtml(5L, named)
        assertEquals("attachment; filename=\"PPTX-Analyse-Sprint_42_.html\"", named.getHeader("Content-Disposition"))
    }

    @Test
    fun `download html falls back to markdown when the export is not a readable file`() {
        stored(markdown = "report body", htmlPath = tempDir.toString())
        val directoryExport = MockHttpServletResponse()
        controller.downloadHtml(5L, directoryExport)
        assertTrue(directoryExport.getContentAsString().contains("report body"))
        assertEquals("attachment; filename=\"Sprint-Analyse.html\"", directoryExport.getHeader("Content-Disposition"))

        stored(id = 6L, markdown = "plain text only", htmlPath = "   ")
        val blankExport = MockHttpServletResponse()
        controller.downloadHtml(6L, blankExport)
        assertTrue(blankExport.getContentAsString().contains("plain text only"))

        stored(id = 7L, markdown = "missing export body", htmlPath = File(tempDir.toFile(), "gone.html").absolutePath)
        val absentExport = MockHttpServletResponse()
        controller.downloadHtml(7L, absentExport)
        assertTrue(absentExport.getContentAsString().contains("missing export body"))

        val missing = MockHttpServletResponse()
        controller.downloadHtml(404L, missing)
        assertEquals(404, missing.status)
    }

    @Test
    fun `download html keeps embedded html only blocks verbatim`() {
        stored(markdown = "```html\n<b>styled</b>\n```\n")
        val response = MockHttpServletResponse()
        controller.downloadHtml(5L, response)
        assertEquals("<b>styled</b>", response.getContentAsString().trim())
    }

    @Test
    fun `download html wraps fenced blocks and remaining text together`() {
        stored(markdown = "```html\n<b>styled</b>\n```\n\nextra notes")
        val response = MockHttpServletResponse()
        controller.downloadHtml(5L, response)
        assertTrue(response.getContentAsString().contains("extra notes"))
        assertEquals("attachment; filename=\"Sprint-Analyse.html\"", response.getHeader("Content-Disposition"))
    }

    @Test
    fun `download markdown and status expose optional values`() {
        stored(id = 8L, markdown = "  ", completedAt = LocalDateTime.now())
        val blank = MockHttpServletResponse()
        controller.downloadMarkdown(8L, blank)
        assertEquals(404, blank.status)

        stored(markdown = "kept text", completedAt = LocalDateTime.now())
        val written = MockHttpServletResponse()
        controller.downloadMarkdown(5L, written)
        assertTrue(written.getContentAsString().contains("kept text"))
        assertEquals("attachment; filename=\"Sprint-Analyse.md\"", written.getHeader("Content-Disposition"))

        val withTimestamp = controller.status(5L).body!!
        assertTrue(withTimestamp["completedAt"].toString().isNotBlank())

        stored(id = 9L, completedAt = null)
        assertNull(controller.status(9L).body!!["completedAt"])

        assertEquals(HttpStatus.NOT_FOUND, controller.status(404L).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, controller.result(404L).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, controller.delete(404L).statusCode)
        assertEquals(HttpStatus.NO_CONTENT, controller.delete(5L).statusCode)
        assertEquals(HttpStatus.OK, controller.deleteAll().statusCode)
        assertTrue(controller.list().isEmpty())
    }

    private class RecordingReviewAnalysis : ReviewAnalysisUseCase {
        val analyses: MutableMap<Long, ReviewAnalysis> = mutableMapOf()

        val existingConfigs: MutableSet<Long> = mutableSetOf()

        val uploadNames: MutableList<String> = mutableListOf()

        val serverPaths: MutableList<String> = mutableListOf()

        val plainCalls: MutableList<Triple<String, Long, String>> = mutableListOf()

        var firstActiveConfigId: Long? = null

        override fun getAnalysis(id: Long): ReviewAnalysis? = analyses[id]

        override fun getAllAnalyses(): List<ReviewAnalysis> = analyses.values.toList()

        override fun jiraConfigExists(id: Long): Boolean = existingConfigs.contains(id)

        override fun firstActiveJiraConfigId(): Long? = firstActiveConfigId

        override fun resolveLanguage(): String = "en"

        override fun startAnalysis(
            jiraSprintUrl: String,
            jiraConfigId: Long,
            analysisInstructions: String,
        ): ReviewAnalysis {
            plainCalls += Triple(jiraSprintUrl, jiraConfigId, analysisInstructions)
            return ReviewAnalysis(id = 5L, jiraSprintUrl = jiraSprintUrl, jiraConfigId = jiraConfigId, analysisInstructions = analysisInstructions)
        }

        override fun startAnalysisWithPptx(
            jiraSprintUrl: String,
            jiraConfigId: Long,
            analysisInstructions: String,
            pptxFileName: String,
            pptxBytes: ByteArray,
        ): ReviewAnalysis {
            uploadNames += pptxFileName
            return ReviewAnalysis(id = 5L, jiraSprintUrl = jiraSprintUrl, jiraConfigId = jiraConfigId, analysisInstructions = analysisInstructions)
        }

        override fun startAnalysisWithPptxPath(
            jiraSprintUrl: String,
            jiraConfigId: Long,
            analysisInstructions: String,
            pptxServerPath: String,
        ): ReviewAnalysis {
            serverPaths += pptxServerPath
            return ReviewAnalysis(id = 5L, jiraSprintUrl = jiraSprintUrl, jiraConfigId = jiraConfigId, analysisInstructions = analysisInstructions)
        }

        override fun deleteAnalysis(id: Long) {
            analyses.remove(id)
        }

        override fun deleteAllAnalyses() {
            analyses.clear()
        }
    }
}
