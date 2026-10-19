package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisDecodingUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryListing
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.domain.model.ReviewAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.nio.file.Path

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewAnalysisControllerStartTest {
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

    private fun fileEntry(
        name: String,
        path: String = "/tmp/$name",
    ) = WorkspaceDirectoryEntry(name = name, path = path, isDirectory = false, isFile = true, size = 1L)

    private fun makeAnalysis(id: Long = 1L): ReviewAnalysis {
        val a = ReviewAnalysis(id = id, jiraSprintUrl = "http://jira/sprint/$id")
        a.status = "PENDING"
        return a
    }

    @Test
    fun `start returns 400 when sprint URL is blank`() {
        whenever(i18n.t(eq("review_analysis.be_sprint_url_empty"))).thenReturn("url-empty")
        val resp = controller.start(mapOf("jiraSprintUrl" to ""))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `start returns 400 when no active Jira config`() {
        whenever(analysisService.firstActiveJiraConfigId()).thenReturn(null)
        whenever(i18n.t(eq("review_analysis.be_invalid_jira_config_id"))).thenReturn("no-config")
        val resp = controller.start(mapOf("jiraSprintUrl" to "http://jira/sprint/1"))
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `start returns 400 when config does not exist`() {
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
        whenever(analysisService.startAnalysis(any(), any(), any())).thenReturn(makeAnalysis(id = 42L))
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
    }

    @Test
    fun `browseFiles returns 400 when directory does not exist`() {
        whenever(i18n.t(eq("review_analysis.be_dir_not_found"), any())).thenReturn("not-found")
        val resp = controller.browseFiles("/nonexistent/dir/xyz", directoriesOnly = true)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `browseFiles returns 200 with items when directory exists`(
        @TempDir tmp: Path,
    ) {
        whenever(workspaceFiles.browseDirectory(tmp.toString())).thenReturn(
            WorkspaceDirectoryListing(
                path = tmp.toString(),
                parentPath = null,
                entries =
                    listOf(
                        fileEntry("a.md"),
                        fileEntry("b.html"),
                        fileEntry("c.txt"),
                    ),
            ),
        )
        val resp = controller.browseFiles(tmp.toString(), directoriesOnly = false)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val items = resp.body!!["items"] as List<Map<String, Any?>>
        assertEquals(2, items.size)
    }
}
