package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.review.ReviewAnalysisDecodingUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.port.inbound.workspace.WorkspaceFileInfo
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.application.service.FakeI18n
import com.stacklens.domain.model.ReviewAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockMultipartFile
import java.io.File
import java.nio.file.Path

class ReviewAnalysisControllerProbeTest {
    private val analysisService: ReviewAnalysisUseCase = mock()

    private val i18n = FakeI18n()

    private val decoding: ReviewAnalysisDecodingUseCase = mock()

    private val workspaceFiles: WorkspaceFileUseCase = mock()

    private val savedRequests = mutableListOf<TextFileSaveRequest>()

    private val controller =
        ReviewAnalysisController(
            analysisService = analysisService,
            i18n = i18n,
            saveHandlers =
                listOf(
                    MarkdownReviewSaveHandler(workspaceFiles, i18n),
                    HtmlReviewSaveHandler(workspaceFiles, i18n),
                ),
            decoding = decoding,
            workspaceFiles = workspaceFiles,
        )

    private fun directoryEntry(name: String) = WorkspaceDirectoryEntry(name = name, path = "$name", isDirectory = true, isFile = false, size = 0L)

    private fun fileEntry(name: String) = WorkspaceDirectoryEntry(name = name, path = name, isDirectory = false, isFile = true, size = 1L)

    private fun directoryInfo(path: String) = WorkspaceFileInfo(name = File(path).name, path = path, size = 0L, isDirectory = true, isFile = false)

    private fun analysis(
        id: Long = 1L,
        markdown: String? = "# Ergebnis",
        htmlPath: String? = null,
    ): ReviewAnalysis =
        ReviewAnalysis(
            id = id,
            jiraSprintUrl = "https://jira.example.com/boards/1",
            resultMarkdown = markdown,
            resultHtmlPath = htmlPath,
            sprintInfoJson = """{"name":"Sprint 12"}""",
        )

    @Test
    fun `browseFileEntries lists directories, markdown, html and pptx files only`() {
        val entries =
            listOf(
                directoryEntry("Unterlagen"),
                fileEntry("bericht.MD"),
                fileEntry("bericht.html"),
                fileEntry("Folien.PPTX"),
                fileEntry("Folie-alt.ppt"),
                fileEntry("notiz.txt"),
            )

        val listed = controller.browseFileEntries(entries, directoriesOnly = false)

        assertEquals(
            listOf(
                "Unterlagen" to "directory",
                "bericht.html" to "file",
                "bericht.MD" to "file",
                "Folie-alt.ppt" to "file",
                "Folien.PPTX" to "file",
            ),
            listed.map { it["name"] to it["type"] },
        )

        val directoriesOnly = controller.browseFileEntries(entries, directoriesOnly = true)

        assertEquals(listOf("Unterlagen"), directoriesOnly.map { it["name"] })
    }

    @Test
    fun `browseFileEntries returns nothing for an empty listing`() {
        assertEquals(emptyList<Map<String, Any?>>(), controller.browseFileEntries(emptyList(), false))
    }

    @Test
    fun `start validates the sprint url the config and the instructions`() {
        assertEquals(
            "review_analysis.be_sprint_url_empty",
            controller.start(mapOf("jiraSprintUrl" to "   ", "jiraConfigId" to "3", "analysisInstructions" to "x")).body?.get("error"),
        )

        whenever(analysisService.firstActiveJiraConfigId()).thenReturn(null)
        assertEquals(
            "review_analysis.be_invalid_jira_config_id",
            controller.start(mapOf("jiraSprintUrl" to "https://jira/sprint", "jiraConfigId" to " keins ", "analysisInstructions" to "x")).body?.get("error"),
        )

        whenever(analysisService.firstActiveJiraConfigId()).thenReturn(9L)
        whenever(analysisService.jiraConfigExists(9L)).thenReturn(false)
        assertEquals(
            "review_analysis.be_jira_config_not_found",
            controller.start(mapOf("jiraSprintUrl" to "https://jira/sprint", "analysisInstructions" to "x")).body?.get("error"),
        )

        whenever(analysisService.jiraConfigExists(9L)).thenReturn(true)
        assertEquals(
            "review_analysis.be_instructions_empty",
            controller.start(mapOf("jiraSprintUrl" to "https://jira/sprint", "analysisInstructions" to "  ")).body?.get("error"),
        )
    }

    @Test
    fun `startWithPptx rejects an upload together with a server path and prefers the upload otherwise`() {
        whenever(analysisService.jiraConfigExists(3L)).thenReturn(true)
        whenever(analysisService.startAnalysisWithPptx(any(), any(), any(), any(), any()))
            .thenReturn(analysis(id = 21L))

        val ambiguous =
            controller.startMultipart(
                jiraSprintUrl = "https://jira/sprint",
                jiraConfigId = "3",
                analysisInstructions = "mehr Tests",
                pptxFile = MockMultipartFile("pptxFile", "deck.pptx", null, byteArrayOf(1)),
                pptxServerPath = "/srv/deck.pptx",
            )
        assertEquals("review_analysis.be_pptx_ambiguous", ambiguous.body?.get("error"))

        val uploaded =
            controller.startMultipart(
                jiraSprintUrl = "https://jira/sprint",
                jiraConfigId = "3",
                analysisInstructions = "mehr Tests",
                pptxFile = MockMultipartFile("pptxFile", null, null, byteArrayOf(1)),
                pptxServerPath = null,
            )
        assertEquals(21L, uploaded.body?.get("id"))

        whenever(analysisService.startAnalysisWithPptxPath(any(), any(), any(), any())).thenReturn(analysis(id = 22L))
        val fromServer =
            controller.startMultipart(
                jiraSprintUrl = "https://jira/sprint",
                jiraConfigId = "3",
                analysisInstructions = "mehr Tests",
                pptxFile = null,
                pptxServerPath = "/srv/deck.pptx",
            )
        assertEquals(22L, fromServer.body?.get("id"))

        whenever(analysisService.startAnalysis(any(), any(), any())).thenReturn(analysis(id = 23L))
        val emptyUpload =
            controller.startMultipart(
                jiraSprintUrl = "https://jira/sprint",
                jiraConfigId = "3",
                analysisInstructions = "mehr Tests",
                pptxFile = MockMultipartFile("pptxFile", "leer.pptx", null, ByteArray(0)),
                pptxServerPath = "   ",
            )
        assertEquals(23L, emptyUpload.body?.get("id"))
    }

    @Test
    fun `downloadHtml serves the rendered pptx page when it still exists`(
        @TempDir tmp: Path,
    ) {
        val html = File(tmp.toFile(), "filled.html").apply { writeText("<html>Folien</html>") }
        whenever(analysisService.getAnalysis(1L)).thenReturn(analysis(htmlPath = html.absolutePath))
        whenever(decoding.sprintName(any())).thenReturn("Sprint 12")
        whenever(workspaceFiles.readText(html.absolutePath)).thenReturn("<html>Folien</html>")
        val response = MockHttpServletResponse()

        controller.downloadHtml(1L, response)

        assertEquals("<html>Folien</html>", response.contentAsString)
        assertEquals("attachment; filename=\"PPTX-Analyse-Sprint_12.html\"", response.getHeader("Content-Disposition"))
    }

    @Test
    fun `downloadHtml falls back to the markdown when the pptx page is gone`(
        @TempDir tmp: Path,
    ) {
        val gone = File(tmp.toFile(), "missing.html").absolutePath
        whenever(analysisService.getAnalysis(1L)).thenReturn(analysis(htmlPath = gone, markdown = "## Bericht <html>x</html>"))
        whenever(decoding.sprintName(any())).thenReturn(null)
        val response = MockHttpServletResponse()

        controller.downloadHtml(1L, response)

        assertTrue(response.contentAsString.contains("Bericht"), response.contentAsString)
        assertEquals("attachment; filename=\"Sprint-Analyse.html\"", response.getHeader("Content-Disposition"))
    }

    @Test
    fun `downloadHtml serves a bare html block without surrounding text`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(analysis(markdown = "```html\n<table><tr><td>a</td></tr></table>\n```"))
        whenever(decoding.sprintName(any())).thenReturn(null)
        val response = MockHttpServletResponse()

        controller.downloadHtml(1L, response)

        assertTrue(response.contentAsString.startsWith("<table>"), response.contentAsString)
    }

    @Test
    fun `downloadHtml reports a missing result as not found`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(analysis(markdown = "   "))
        val response = MockHttpServletResponse()

        controller.downloadHtml(1L, response)

        assertEquals(404, response.status)
    }

    @Test
    fun `downloadMarkdown reports a missing result as not found`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(null)
        val response = MockHttpServletResponse()

        controller.downloadMarkdown(1L, response)

        assertEquals(404, response.status)
    }

    @Test
    fun `saveToPath needs a known format an existing analysis and a real directory`(
        @TempDir tmp: Path,
    ) {
        assertEquals(
            "review_analysis.be_invalid_format",
            controller.saveToPath(1L, mapOf("path" to tmp.toString())).body?.get("error"),
        )
        assertEquals(
            "review_analysis.be_invalid_format",
            controller.saveToPath(1L, mapOf("format" to "rtf", "path" to tmp.toString())).body?.get("error"),
        )
        assertEquals(
            "review_analysis.be_no_dir_path",
            controller.saveToPath(1L, mapOf("format" to "md")).body?.get("error"),
        )

        whenever(analysisService.getAnalysis(1L)).thenReturn(null)
        assertNull(controller.saveToPath(1L, mapOf("format" to "md", "path" to tmp.toString())).body)

        val missing = File(tmp.toFile(), "nope").absolutePath
        whenever(analysisService.getAnalysis(1L)).thenReturn(analysis())
        whenever(decoding.sprintName(any())).thenReturn("Sprint 12")
        whenever(workspaceFiles.fileInfo(tmp.toString())).thenReturn(directoryInfo(tmp.toString()))
        whenever(workspaceFiles.saveText(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<TextFileSaveRequest>(0)
            savedRequests += request
            WorkspaceWriteResult.Saved(File(request.directory, request.filename).absolutePath)
        }
        val rejected = controller.saveToPath(1L, mapOf("format" to "md", "path" to missing))
        assertTrue(
            rejected.body
                ?.get("error")
                ?.toString()
                ?.startsWith("review_analysis.be_dir_not_found") == true,
            "unexpected: ${rejected.body}",
        )

        val saved = controller.saveToPath(1L, mapOf("format" to "md", "path" to tmp.toString()))
        assertTrue(
            saved.body
                ?.get("savedPath")
                ?.toString()
                ?.endsWith("Sprint-Review-Sprint_12.md") == true,
            "unexpected: ${saved.body}",
        )
        assertEquals("# Ergebnis", savedRequests[0].content)

        val asHtml = controller.saveToPath(1L, mapOf("format" to "html", "path" to tmp.toString()))
        assertTrue(
            asHtml.body
                ?.get("savedPath")
                ?.toString()
                ?.endsWith("Sprint-Review-Sprint_12.html") == true,
            "unexpected: ${asHtml.body}",
        )
        assertTrue(savedRequests[1].content.contains("Ergebnis"))

        whenever(analysisService.getAnalysis(1L)).thenReturn(analysis(markdown = "   "))
        assertEquals(
            "review_analysis.be_no_result",
            controller.saveToPath(1L, mapOf("format" to "md", "path" to tmp.toString())).body?.get("error"),
        )
    }
}
