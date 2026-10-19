package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisDecodingUseCase
import com.stacklens.application.port.inbound.review.ReviewAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.domain.model.ReviewAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewAnalysisControllerStartMultipartTest {
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

    @BeforeEach
    fun mockI18n() {
        whenever(i18n.t(any<String>())).thenReturn("err")
        whenever(i18n.t(any<String>(), any())).thenReturn("err")
        whenever(i18n.t(any<String>(), any(), any())).thenReturn("err")
    }

    @Test
    fun `startMultipart returns bad request when sprint url is blank`() {
        val resp = controller.startMultipart("   ", "1", "instructions", null, null)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `startMultipart returns bad request when jiraConfigId is not numeric and no active config`() {
        whenever(analysisService.firstActiveJiraConfigId()).thenReturn(null)
        val resp = controller.startMultipart("http://jira/sprint/1", "not-a-number", "instructions", null, null)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `startMultipart uses firstActiveJiraConfigId when jiraConfigId is not numeric`() {
        whenever(analysisService.firstActiveJiraConfigId()).thenReturn(7L)
        whenever(analysisService.jiraConfigExists(7L)).thenReturn(true)
        whenever(analysisService.startAnalysis(any(), eq(7L), any())).thenReturn(ReviewAnalysis(id = 99L, jiraSprintUrl = "u"))

        val resp = controller.startMultipart("http://jira/sprint/1", "not-a-number", "instructions", null, null)
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `startMultipart returns bad request when jira config does not exist`() {
        whenever(analysisService.jiraConfigExists(99L)).thenReturn(false)
        val resp = controller.startMultipart("http://jira/sprint/1", "99", "instructions", null, null)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `startMultipart returns bad request when instructions are blank`() {
        whenever(analysisService.jiraConfigExists(1L)).thenReturn(true)
        val resp = controller.startMultipart("http://jira/sprint/1", "1", "   ", null, null)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `startMultipart returns bad request when both pptxFile and pptxServerPath are provided`() {
        whenever(analysisService.jiraConfigExists(1L)).thenReturn(true)
        val pptx = MockMultipartFile("pptxFile", "a.pptx", "application/vnd.ms-powerpoint", ByteArray(10))
        val resp =
            controller.startMultipart(
                "http://jira/sprint/1",
                "1",
                "instructions",
                pptx,
                "/path/to.pptx",
            )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `startMultipart starts analysis with pptxFile when provided`() {
        whenever(analysisService.jiraConfigExists(1L)).thenReturn(true)
        val pptx = MockMultipartFile("pptxFile", "deck.pptx", "application/vnd.ms-powerpoint", ByteArray(100))
        whenever(analysisService.startAnalysisWithPptx(any(), eq(1L), any(), any(), any()))
            .thenReturn(ReviewAnalysis(id = 42L, jiraSprintUrl = "u"))

        val resp = controller.startMultipart("http://jira/sprint/1", "1", "instructions", pptx, null)
        assertEquals(HttpStatus.OK, resp.statusCode)
        val body = resp.body
        assertNotNull(body)
        assertEquals(42L, body!!["id"])
    }

    @Test
    fun `startMultipart starts analysis with pptxServerPath when provided`() {
        whenever(analysisService.jiraConfigExists(1L)).thenReturn(true)
        whenever(analysisService.startAnalysisWithPptxPath(any(), eq(1L), any(), any()))
            .thenReturn(ReviewAnalysis(id = 77L, jiraSprintUrl = "u"))

        val resp =
            controller.startMultipart(
                "http://jira/sprint/1",
                "1",
                "instructions",
                null,
                "/path/to.pptx",
            )
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(77L, resp.body!!["id"])
    }

    @Test
    fun `startMultipart uses filename upload-pptx when originalFilename is null`() {
        whenever(analysisService.jiraConfigExists(1L)).thenReturn(true)
        val pptx = MockMultipartFile("pptxFile", null, "application/vnd.ms-powerpoint", ByteArray(50))
        whenever(analysisService.startAnalysisWithPptx(any(), eq(1L), any(), any(), any()))
            .thenReturn(ReviewAnalysis(id = 1L, jiraSprintUrl = "u"))

        val resp = controller.startMultipart("http://jira/sprint/1", "1", "instructions", pptx, null)
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `startMultipart falls back to startAnalysis when no pptx provided`() {
        whenever(analysisService.jiraConfigExists(1L)).thenReturn(true)
        whenever(analysisService.startAnalysis(any(), eq(1L), any()))
            .thenReturn(ReviewAnalysis(id = 5L, jiraSprintUrl = "u"))

        val resp = controller.startMultipart("http://jira/sprint/1", "1", "instructions", null, null)
        assertEquals(HttpStatus.OK, resp.statusCode)
        assertEquals(5L, resp.body!!["id"])
    }
}
