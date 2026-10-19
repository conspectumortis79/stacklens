package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.analysis.AiAnalysisUseCase
import com.stacklens.application.service.I18nService
import com.stacklens.domain.model.AiAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class AiAnalysisControllerTest {
    private val aiService: AiAnalysisUseCase = mock()
    private val i18n: I18nService = mock()
    private val controller = AiAnalysisController(aiService, i18n)

    private fun analysis(id: Long = 1L) =
        AiAnalysis(id = id, sessionId = 1L, status = "COMPLETED").also {
            it.analysisText = "Analysis text"
        }

    @Test
    fun `startAnalysis returns 404 when session does not exist`() {
        whenever(aiService.sessionExists(5L)).thenReturn(false)
        val resp = controller.startAnalysis(5L, null)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `startAnalysis starts analysis and returns id and status`() {
        val a = analysis(id = 10L)
        whenever(aiService.sessionExists(1L)).thenReturn(true)
        whenever(aiService.startAnalysis(any(), any(), any())).thenReturn(a)
        val resp = controller.startAnalysis(1L, null)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(10L, body["analysisId"])
        assertEquals("COMPLETED", body["status"])
    }

    @Test
    fun `startAnalysis passes mermaid and drawio flags from request`() {
        val a = analysis()
        whenever(aiService.sessionExists(1L)).thenReturn(true)
        whenever(aiService.startAnalysis(1L, includeMermaid = true, includeDrawio = true)).thenReturn(a)
        val req = StartAnalysisRequest(includeMermaid = true, includeDrawio = true)
        val resp = controller.startAnalysis(1L, req)
        assertEquals(HttpStatus.OK, resp.statusCode)
    }

    @Test
    fun `getAnalysis returns NONE status when analysis absent`() {
        whenever(aiService.getLatestAnalysis(2L)).thenReturn(null)
        val resp = controller.getAnalysis(2L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals("NONE", body["status"])
    }

    @Test
    fun `getAnalysis returns analysis when present`() {
        val a = analysis(id = 3L)
        whenever(aiService.getLatestAnalysis(3L)).thenReturn(a)
        val resp = controller.getAnalysis(3L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(3L, body["id"])
        assertEquals("COMPLETED", body["status"])
        assertEquals("Analysis text", body["analysisText"])
    }

    @Test
    fun `saveDiagram returns saved path on success`() {
        whenever(aiService.saveDiagram(any(), any(), any())).thenReturn("/saved/path.svg")
        val req = SaveDiagramRequest(path = "/safe/dir", filename = "diagram.svg", content = "<svg/>")
        val resp = controller.saveDiagram(1L, req)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals("/saved/path.svg", body["savedPath"])
    }

    @Test
    fun `saveDiagram returns 400 on IllegalArgumentException`() {
        whenever(aiService.saveDiagram(any(), any(), any())).thenThrow(IllegalArgumentException("invalid dir"))
        val req = SaveDiagramRequest(path = "/bad", filename = "d.svg", content = "")
        val resp = controller.saveDiagram(1L, req)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `saveDiagram returns 500 on generic exception`() {
        whenever(aiService.saveDiagram(any(), any(), any())).thenThrow(RuntimeException("io error"))
        val req = SaveDiagramRequest(path = "/dir", filename = "d.svg", content = "")
        val resp = controller.saveDiagram(1L, req)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
    }

    @Test
    fun `getAnalysis returns completedAt as string when completedAt is set`() {
        val completedAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0)
        val a = analysis(id = 4L).also { it.completedAt = completedAt }
        whenever(aiService.getLatestAnalysis(4L)).thenReturn(a)
        val resp = controller.getAnalysis(4L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        assertEquals(completedAt.toString(), body["completedAt"])
    }

    @Test
    fun `saveDiagram returns 400 with default message when IllegalArgumentException has no message`() {
        whenever(i18n.t("config.be_invalid_dir")).thenReturn("Ungültiges Verzeichnis")
        whenever(aiService.saveDiagram(any(), any(), any())).thenThrow(IllegalArgumentException())
        val req = SaveDiagramRequest(path = "/dir", filename = "d.svg", content = "")
        val resp = controller.saveDiagram(1L, req)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals("Ungültiges Verzeichnis", body["error"])
    }

    @Test
    fun `saveDiagram returns 500 on generic exception with null message`() {
        whenever(i18n.t("config.be_save_error", "")).thenReturn("Fehler beim Speichern")
        whenever(aiService.saveDiagram(any(), any(), any())).thenThrow(RuntimeException())
        val req = SaveDiagramRequest(path = "/dir", filename = "d.svg", content = "")
        val resp = controller.saveDiagram(1L, req)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
    }
}
