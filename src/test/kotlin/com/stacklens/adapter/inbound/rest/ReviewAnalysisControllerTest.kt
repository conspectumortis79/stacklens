package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisDecodingUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryListing
import com.stacklens.application.port.inbound.workspace.WorkspaceFileInfo
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.domain.model.ReviewAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.HttpStatus
import java.io.File
import java.nio.file.Path

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewAnalysisControllerTest {
    @Mock
    lateinit var analysisService: ReviewAnalysisUseCase

    @Mock
    lateinit var i18n: I18nUseCase

    @Mock
    lateinit var decoding: ReviewAnalysisDecodingUseCase

    @Mock
    lateinit var workspaceFiles: WorkspaceFileUseCase

    private val saveHandlers: List<ReviewSaveHandler> by lazy {
        listOf(
            MarkdownReviewSaveHandler(workspaceFiles, i18n),
            HtmlReviewSaveHandler(workspaceFiles, i18n),
        )
    }

    private val controller by lazy {
        ReviewAnalysisController(
            analysisService = analysisService,
            i18n = i18n,
            saveHandlers = saveHandlers,
            decoding = decoding,
            workspaceFiles = workspaceFiles,
        )
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

    private fun makeAnalysis(
        id: Long = 1L,
        status: String = "COMPLETED",
        markdown: String? = null,
        sprintInfoJson: String? = null,
    ): ReviewAnalysis {
        val a = ReviewAnalysis(id = id, jiraSprintUrl = "http://jira/sprint/$id")
        a.status = status
        a.resultMarkdown = markdown
        a.sprintInfoJson = sprintInfoJson
        return a
    }

    private fun capturedSaveRequest(): TextFileSaveRequest {
        val captor = argumentCaptor<TextFileSaveRequest>()
        verify(workspaceFiles).saveText(captor.capture())
        return captor.firstValue
    }

    @Test
    fun `start returns 400 when sprint URL is blank`() {
        whenever(i18n.t(eq("review_analysis.be_sprint_url_empty"))).thenReturn("url-empty")
        val resp = controller.start(mapOf("jiraSprintUrl" to ""))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        assertEquals("url-empty", resp.body!!["error"])
    }

    @Test
    fun `start returns 400 when jiraConfigId is missing and no active config`() {
        whenever(analysisService.firstActiveJiraConfigId()).thenReturn(null)
        whenever(i18n.t(eq("review_analysis.be_invalid_jira_config_id"))).thenReturn("no-config")
        val resp = controller.start(mapOf("jiraSprintUrl" to "http://jira/sprint/1"))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        assertEquals("no-config", resp.body!!["error"])
    }

    @Test
    fun `start returns 400 when jiraConfigId does not exist`() {
        whenever(analysisService.firstActiveJiraConfigId()).thenReturn(99L)
        whenever(analysisService.jiraConfigExists(99L)).thenReturn(false)
        whenever(i18n.t(eq("review_analysis.be_jira_config_not_found"))).thenReturn("not-found")
        val resp = controller.start(mapOf("jiraSprintUrl" to "http://jira/sprint/1", "jiraConfigId" to "99"))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `start returns 400 when instructions are blank`() {
        whenever(analysisService.firstActiveJiraConfigId()).thenReturn(1L)
        whenever(analysisService.jiraConfigExists(1L)).thenReturn(true)
        whenever(i18n.t(eq("review_analysis.be_instructions_empty"))).thenReturn("instr-empty")
        val resp =
            controller.start(
                mapOf(
                    "jiraSprintUrl" to "http://jira/sprint/1",
                    "jiraConfigId" to "1",
                    "analysisInstructions" to "",
                ),
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `start returns OK with id and status on success`() {
        whenever(analysisService.firstActiveJiraConfigId()).thenReturn(1L)
        whenever(analysisService.jiraConfigExists(1L)).thenReturn(true)
        whenever(analysisService.startAnalysis(any(), any(), any())).thenReturn(
            makeAnalysis(id = 42L, status = "PENDING"),
        )
        val resp =
            controller.start(
                mapOf(
                    "jiraSprintUrl" to "http://jira/sprint/1",
                    "jiraConfigId" to "1",
                    "analysisInstructions" to "find issues",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(42L, resp.body!!["id"])
        assertEquals("PENDING", resp.body!!["status"])
    }

    @Test
    fun `start uses provided jiraConfigId when valid`() {
        whenever(analysisService.jiraConfigExists(7L)).thenReturn(true)
        whenever(analysisService.startAnalysis(any(), any(), any())).thenReturn(
            makeAnalysis(id = 42L),
        )
        val resp =
            controller.start(
                mapOf(
                    "jiraSprintUrl" to "http://jira/sprint/1",
                    "jiraConfigId" to "7",
                    "analysisInstructions" to "x",
                ),
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `list returns summaries`() {
        whenever(analysisService.getAllAnalyses()).thenReturn(listOf(makeAnalysis()))
        val resp = controller.list()
        assertEquals(1, resp.size)
    }

    @Test
    fun `list returns empty when no analyses exist`() {
        whenever(analysisService.getAllAnalyses()).thenReturn(emptyList())
        val resp = controller.list()
        assertEquals(0, resp.size)
    }

    @Test
    fun `status returns 404 when analysis not found`() {
        whenever(analysisService.getAnalysis(99L)).thenReturn(null)
        val resp = controller.status(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `status returns 200 with status info when found`() {
        val a = makeAnalysis(id = 5L, status = "RUNNING")
        a.currentStep = "Phase 1"
        a.errorMessage = null
        whenever(analysisService.getAnalysis(5L)).thenReturn(a)
        val resp = controller.status(5L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(5L, resp.body!!["id"])
        assertEquals("RUNNING", resp.body!!["status"])
        assertEquals("Phase 1", resp.body!!["currentStep"])
    }

    @Test
    fun `result returns 404 when analysis not found`() {
        whenever(analysisService.getAnalysis(99L)).thenReturn(null)
        val resp = controller.result(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `result returns 200 with detail map when found`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(makeAnalysis())
        val resp = controller.result(1L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertNotNull(resp.body)
    }

    @Test
    fun `delete returns 404 when analysis not found`() {
        whenever(analysisService.getAnalysis(99L)).thenReturn(null)
        val resp = controller.delete(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `delete returns 204 on success`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(makeAnalysis())
        val resp = controller.delete(1L)
        assertEquals(HttpStatus.NO_CONTENT, resp.statusCode)
    }

    @Test
    fun `deleteAll returns 200 with deleted=true`() {
        val resp = controller.deleteAll()
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(true, resp.body!!["deleted"])
    }

    @Test
    fun `browseFiles returns 400 when directory does not exist`() {
        whenever(i18n.t(eq("review_analysis.be_dir_not_found"), any())).thenReturn("not-found")
        val resp = controller.browseFiles("/nonexistent/dir/please", directoriesOnly = true)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `browseFiles returns 200 with items when directory exists`(
        @TempDir tmp: Path,
    ) {
        whenever(workspaceFiles.browseDirectory(tmp.toString())).thenReturn(
            listing(
                entries =
                    listOf(
                        directoryEntry("subdir"),
                        fileEntry("a.md"),
                        fileEntry("b.html"),
                        fileEntry("c.txt"),
                    ),
                path = tmp.toString(),
            ),
        )
        val resp = controller.browseFiles(tmp.toString(), directoriesOnly = false)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val items = resp.body!!["items"] as List<Map<String, Any?>>
        assertEquals(3, items.size)
        val names = items.map { it["name"] }.toSet()
        assertTrue("subdir" in names)
        assertTrue("a.md" in names)
        assertTrue("b.html" in names)
        assertFalse("c.txt" in names)
    }

    @Test
    fun `browseFiles with directoriesOnly=true excludes files`(
        @TempDir tmp: Path,
    ) {
        whenever(workspaceFiles.browseDirectory(tmp.toString())).thenReturn(
            listing(
                entries = listOf(directoryEntry("dir1"), fileEntry("a.md")),
                path = tmp.toString(),
            ),
        )
        val resp = controller.browseFiles(tmp.toString(), directoriesOnly = true)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val items = resp.body!!["items"] as List<Map<String, Any?>>
        assertEquals(1, items.size)
        assertEquals("dir1", items[0]["name"])
    }

    @Test
    fun `browseFiles with null path uses user home`() {
        whenever(workspaceFiles.homeDirectory()).thenReturn("/home/user")
        whenever(workspaceFiles.browseDirectory("/home/user")).thenReturn(
            listing(entries = emptyList(), path = "/home/user"),
        )
        val resp = controller.browseFiles(null, directoriesOnly = true)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals("/home/user", resp.body!!["currentPath"])
    }

    @Test
    fun `browseFileEntries handles an empty listing`() {
        val entries = controller.browseFileEntries(emptyList(), directoriesOnly = false)
        assertEquals(emptyList<Map<String, Any?>>(), entries)
    }

    @Test
    fun `saveToPath returns 400 when format is missing`() {
        whenever(i18n.t(eq("review_analysis.be_invalid_format"))).thenReturn("bad-format")
        val resp = controller.saveToPath(1L, mapOf("path" to "/tmp"))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `saveToPath returns 400 when no handler matches`() {
        whenever(i18n.t(eq("review_analysis.be_invalid_format"))).thenReturn("bad-format")
        val resp = controller.saveToPath(1L, mapOf("format" to "pdf", "path" to "/tmp"))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `saveToPath returns 400 when path is missing`() {
        whenever(i18n.t(eq("review_analysis.be_no_dir_path"))).thenReturn("no-path")
        val resp = controller.saveToPath(1L, mapOf("format" to "md"))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `saveToPath returns 404 when analysis not found`() {
        whenever(analysisService.getAnalysis(99L)).thenReturn(null)
        val resp = controller.saveToPath(99L, mapOf("format" to "md", "path" to "/tmp"))
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `saveToPath returns 400 when directory does not exist`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(makeAnalysis())
        whenever(i18n.t(eq("review_analysis.be_dir_not_found"), any())).thenReturn("not-found")
        val resp = controller.saveToPath(1L, mapOf("format" to "md", "path" to "/nonexistent/dir"))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `saveToPath uses default handler name when sprint name is null`(
        @TempDir tmp: Path,
    ) {
        val a = makeAnalysis(markdown = "# Analysis")
        whenever(analysisService.getAnalysis(1L)).thenReturn(a)
        whenever(decoding.sprintName(any())).thenReturn(null)
        whenever(workspaceFiles.fileInfo(tmp.toString())).thenReturn(
            WorkspaceFileInfo(name = tmp.toFile().name, path = tmp.toString(), size = 0L, isDirectory = true, isFile = false),
        )
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved(File(tmp.toFile(), "Sprint-Review-Analyse.md").absolutePath))
        val resp = controller.saveToPath(1L, mapOf("format" to "md", "path" to tmp.toString()))
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertNotNull(resp.body)
        assertEquals("Sprint-Review-Analyse.md", capturedSaveRequest().filename)
    }

    @Test
    fun `saveToPath uses sprint name in handler ctx when available`(
        @TempDir tmp: Path,
    ) {
        val a = makeAnalysis(markdown = "# Analysis")
        a.sprintInfoJson = """{"name":"Sprint 42"}"""
        whenever(analysisService.getAnalysis(1L)).thenReturn(a)
        whenever(decoding.sprintName(any())).thenReturn("Sprint 42")
        whenever(workspaceFiles.fileInfo(tmp.toString())).thenReturn(
            WorkspaceFileInfo(name = tmp.toFile().name, path = tmp.toString(), size = 0L, isDirectory = true, isFile = false),
        )
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved(File(tmp.toFile(), "Sprint-Review-Sprint_42.md").absolutePath))
        val resp = controller.saveToPath(1L, mapOf("format" to "md", "path" to tmp.toString()))
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals("Sprint-Review-Sprint_42.md", capturedSaveRequest().filename)
    }

    @Test
    fun `saveToPath sanitizes sprint name for safe filename`(
        @TempDir tmp: Path,
    ) {
        val a = makeAnalysis(markdown = "# Analysis")
        a.sprintInfoJson = """{"name":"Sprint/with:bad*chars"}"""
        whenever(analysisService.getAnalysis(1L)).thenReturn(a)
        whenever(decoding.sprintName(any())).thenReturn("Sprint/with:bad*chars")
        whenever(workspaceFiles.fileInfo(tmp.toString())).thenReturn(
            WorkspaceFileInfo(name = tmp.toFile().name, path = tmp.toString(), size = 0L, isDirectory = true, isFile = false),
        )
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved(File(tmp.toFile(), "Sprint-Review-Sprint_with_bad_chars.md").absolutePath))
        val resp = controller.saveToPath(1L, mapOf("format" to "md", "path" to tmp.toString()))
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals("Sprint-Review-Sprint_with_bad_chars.md", capturedSaveRequest().filename)
    }

    @Test
    fun `downloadMarkdown returns 404 when analysis not found`() {
        whenever(analysisService.getAnalysis(99L)).thenReturn(null)
        val response = mockResponse()
        controller.downloadMarkdown(99L, response)
        assertEquals(404, response.status)
    }

    @Test
    fun `downloadMarkdown returns 404 when markdown is null`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(makeAnalysis(markdown = null))
        whenever(i18n.t(any())).thenReturn("no-result")
        val response = mockResponse()
        controller.downloadMarkdown(1L, response)
        assertEquals(404, response.status)
    }

    @Test
    fun `downloadMarkdown returns 404 when markdown is blank`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(makeAnalysis(markdown = "   "))
        whenever(i18n.t(any())).thenReturn("no-result")
        val response = mockResponse()
        controller.downloadMarkdown(1L, response)
        assertEquals(404, response.status)
    }

    @Test
    fun `downloadMarkdown writes file with default name`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(makeAnalysis(markdown = "# Hello"))
        whenever(decoding.sprintName(any())).thenReturn(null)
        val response = mockResponse()
        controller.downloadMarkdown(1L, response)
        assertEquals(200, response.status)
        assertTrue(response.written.contains("# Hello"))
        assertTrue(response.contentDisposition!!.contains("Sprint-Analyse.md"))
    }

    @Test
    fun `downloadMarkdown writes file with sprint-named filename`() {
        val a = makeAnalysis(markdown = "# Hello")
        a.sprintInfoJson = """{"name":"MySprint"}"""
        whenever(analysisService.getAnalysis(1L)).thenReturn(a)
        whenever(decoding.sprintName(any())).thenReturn("MySprint")
        val response = mockResponse()
        controller.downloadMarkdown(1L, response)
        assertTrue(response.contentDisposition!!.contains("Sprint-Analyse-MySprint.md"))
    }

    @Test
    fun `downloadHtml returns 404 when analysis not found`() {
        whenever(analysisService.getAnalysis(99L)).thenReturn(null)
        val response = mockResponse()
        controller.downloadHtml(99L, response)
        assertEquals(404, response.status)
    }

    @Test
    fun `downloadHtml returns 404 when markdown is null`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(makeAnalysis(markdown = null))
        whenever(i18n.t(any())).thenReturn("no-result")
        val response = mockResponse()
        controller.downloadHtml(1L, response)
        assertEquals(404, response.status)
    }

    @Test
    fun `downloadHtml returns 404 when markdown is blank`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(makeAnalysis(markdown = "  "))
        whenever(i18n.t(any())).thenReturn("no-result")
        val response = mockResponse()
        controller.downloadHtml(1L, response)
        assertEquals(404, response.status)
    }

    @Test
    fun `downloadHtml writes html page when content present`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(makeAnalysis(markdown = "# Hello"))
        whenever(decoding.sprintName(any())).thenReturn(null)
        val response = mockResponse()
        controller.downloadHtml(1L, response)
        assertEquals(200, response.status)
        assertTrue(response.written.contains("Hello"))
    }

    @Test
    fun `downloadHtml writes html with default filename`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(makeAnalysis(markdown = "# Hello"))
        whenever(decoding.sprintName(any())).thenReturn(null)
        val response = mockResponse()
        controller.downloadHtml(1L, response)
        assertTrue(response.contentDisposition!!.contains("Sprint-Analyse.html"))
    }

    @Test
    fun `downloadHtml writes html with sprint-named filename`() {
        val a = makeAnalysis(markdown = "# Hello")
        a.sprintInfoJson = """{"name":"MySprint"}"""
        whenever(analysisService.getAnalysis(1L)).thenReturn(a)
        whenever(decoding.sprintName(any())).thenReturn("MySprint")
        val response = mockResponse()
        controller.downloadHtml(1L, response)
        assertTrue(response.contentDisposition!!.contains("Sprint-Analyse-MySprint.html"))
    }

    @Test
    fun `browseFileEntries returns empty for no entries`() {
        val entries = controller.browseFileEntries(emptyList(), directoriesOnly = false)
        assertEquals(emptyList<Map<String, Any?>>(), entries)
    }

    @Test
    fun `browseFileEntries sorts directories first`() {
        val entries =
            controller.browseFileEntries(
                listOf(fileEntry("aaa.md"), directoryEntry("zzz-dir")),
                directoriesOnly = false,
            )
        assertEquals(2, entries.size)
        assertEquals("directory", entries[0]["type"])
        assertEquals("file", entries[1]["type"])
    }

    @Test
    fun `browseFileEntries excludes non-md-html files`() {
        val entries =
            controller.browseFileEntries(
                listOf(fileEntry("a.md"), fileEntry("a.html"), fileEntry("a.txt"), fileEntry("a.json")),
                directoriesOnly = false,
            )
        assertEquals(2, entries.size)
    }

    @Test
    fun `browseFileEntries handles case-insensitive md and html extensions`() {
        val entries =
            controller.browseFileEntries(
                listOf(fileEntry("a.MD"), fileEntry("b.HTML")),
                directoriesOnly = false,
            )
        assertEquals(2, entries.size)
    }

    @Test
    fun `browseFileEntries skips entries that are neither files nor directories`() {
        val ghost = WorkspaceDirectoryEntry(name = "ghost", path = "/tmp/ghost", isDirectory = false, isFile = false, size = 0L)
        val entries =
            controller.browseFileEntries(
                listOf(directoryEntry("visible"), ghost, fileEntry("a.md")),
                directoriesOnly = true,
            )
        assertEquals(1, entries.size)
        assertEquals("visible", entries[0]["name"])
    }

    private fun mockResponse(): MockHttpServletResponse = MockHttpServletResponse()

    private class MockHttpServletResponse : jakarta.servlet.http.HttpServletResponse {
        @get:JvmName("respStatus")
        @set:JvmName("setRespStatus")
        var status: Int = 200

        @get:JvmName("respContentType")
        @set:JvmName("setRespContentType")
        var contentType: String? = null

        @get:JvmName("respContentDisposition")
        @set:JvmName("setRespContentDisposition")
        var contentDisposition: String? = null
        val written = StringBuilder()

        @get:JvmName("respWriter")
        val writer: java.io.PrintWriter =
            object : java.io.PrintWriter(java.io.StringWriter()) {
                override fun write(s: String?) {
                    written.append(s)
                }
            }

        override fun setStatus(sc: Int) {
            status = sc
        }

        override fun sendError(
            sc: Int,
            msg: String?,
        ) {
            status = sc
        }

        override fun sendError(sc: Int) {
            status = sc
        }

        override fun setHeader(
            name: String,
            value: String?,
        ) {
            if (name == "Content-Disposition") contentDisposition = value
        }

        override fun setContentType(type: String?) {
            contentType = type
        }

        override fun getWriter(): java.io.PrintWriter = writer

        override fun addCookie(cookie: jakarta.servlet.http.Cookie?) {}

        override fun containsHeader(name: String?): Boolean = false

        override fun encodeURL(url: String?): String? = url

        override fun encodeRedirectURL(url: String?): String? = url

        override fun sendRedirect(location: String?) {}

        override fun setDateHeader(
            name: String?,
            date: Long,
        ) {}

        override fun addDateHeader(
            name: String?,
            date: Long,
        ) {}

        override fun setIntHeader(
            name: String?,
            value: Int,
        ) {}

        override fun addIntHeader(
            name: String?,
            value: Int,
        ) {}

        override fun addHeader(
            name: String?,
            value: String?,
        ) {}

        override fun getHeader(name: String?): String? = if (name == "Content-Disposition") contentDisposition else null

        override fun getHeaderNames(): MutableList<String> = mutableListOf()

        override fun getHeaders(name: String?): MutableList<String> = mutableListOf()

        override fun getStatus(): Int = status

        override fun getCharacterEncoding(): String? = "UTF-8"

        override fun setCharacterEncoding(charset: String?) {}

        override fun getContentType(): String? = contentType

        override fun getOutputStream(): jakarta.servlet.ServletOutputStream? = null

        override fun setContentLengthLong(len: Long) {}

        override fun setContentLength(len: Int) {}

        override fun setBufferSize(size: Int) {}

        override fun getBufferSize(): Int = 0

        override fun flushBuffer() {}

        override fun resetBuffer() {}

        override fun isCommitted(): Boolean = false

        override fun reset() {}

        override fun setLocale(loc: java.util.Locale?) {}

        override fun getLocale(): java.util.Locale? = null
    }
}
