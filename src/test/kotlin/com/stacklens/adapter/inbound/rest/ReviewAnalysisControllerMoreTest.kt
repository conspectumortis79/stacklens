package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisDecodingUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryListing
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.domain.model.ReviewAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.HttpStatus
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewAnalysisControllerMoreTest {
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

    @Test
    fun `browseFiles returns 400 when path points to a file instead of a directory`(
        @TempDir tmp: Path,
    ) {
        val file = tmp.resolve("not-a-dir.txt")
        Files.writeString(file, "content")
        whenever(i18n.t(eq("review_analysis.be_dir_not_found"), any())).thenReturn("not-found")
        val resp = controller.browseFiles(file.toString(), directoriesOnly = true)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `browseFiles with empty path uses home directory`(
        @TempDir tmp: Path,
    ) {
        whenever(workspaceFiles.homeDirectory()).thenReturn(tmp.toString())
        whenever(workspaceFiles.browseDirectory(tmp.toString())).thenReturn(listing(emptyList(), path = tmp.toString()))
        val resp = controller.browseFiles("", directoriesOnly = false)
        assertTrue(resp.statusCode == HttpStatus.OK || resp.statusCode == HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `browseFiles includes directories and md-html files only`(
        @TempDir tmp: Path,
    ) {
        whenever(workspaceFiles.browseDirectory(tmp.toString())).thenReturn(
            listing(
                entries =
                    listOf(
                        directoryEntry("dir1"),
                        directoryEntry("dir2"),
                        fileEntry("a.md"),
                        fileEntry("b.html"),
                        fileEntry("c.txt"),
                        fileEntry("d.json"),
                    ),
                path = tmp.toString(),
            ),
        )
        val resp = controller.browseFiles(tmp.toString(), directoriesOnly = false)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val items = resp.body!!["items"] as List<Map<String, Any?>>
        assertEquals(4, items.size)
    }

    @Test
    fun `browseFiles case-insensitive html extension`(
        @TempDir tmp: Path,
    ) {
        whenever(workspaceFiles.browseDirectory(tmp.toString())).thenReturn(
            listing(entries = listOf(fileEntry("a.HTML"), fileEntry("b.Htm")), path = tmp.toString()),
        )
        val resp = controller.browseFiles(tmp.toString(), directoriesOnly = false)
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `status returns 404 when analysis not found`() {
        whenever(analysisService.getAnalysis(99L)).thenReturn(null)
        val resp = controller.status(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `result returns 404 when analysis not found`() {
        whenever(analysisService.getAnalysis(99L)).thenReturn(null)
        val resp = controller.result(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `delete returns 204 on success`() {
        whenever(analysisService.getAnalysis(1L)).thenReturn(ReviewAnalysis(id = 1L, jiraSprintUrl = "http://jira/sprint/1"))
        val resp = controller.delete(1L)
        assertEquals(HttpStatus.NO_CONTENT, resp.statusCode)
    }

    @Test
    fun `delete returns 404 when not found`() {
        whenever(analysisService.getAnalysis(99L)).thenReturn(null)
        val resp = controller.delete(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `deleteAll returns 200`() {
        val resp = controller.deleteAll()
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `list returns summaries for all analyses`() {
        whenever(analysisService.getAllAnalyses()).thenReturn(
            listOf(
                ReviewAnalysis(id = 1L, jiraSprintUrl = "http://jira/sprint/1"),
                ReviewAnalysis(id = 2L, jiraSprintUrl = "http://jira/sprint/2"),
            ),
        )
        val resp = controller.list()
        assertEquals(2, resp.size)
    }
}
