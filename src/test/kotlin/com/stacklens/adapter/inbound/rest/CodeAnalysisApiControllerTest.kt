package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.analysis.CodeAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.domain.model.CodeAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CodeAnalysisApiControllerTest {
    private val useCase = mock<CodeAnalysisUseCase>()
    private val workspaceFiles = mock<WorkspaceFileUseCase>()
    private val i18n =
        com.stacklens.application.service
            .FakeI18n()
    private val controller = CodeAnalysisApiController(useCase, workspaceFiles, i18n)

    @Test
    fun `all returns mapped analyses`() {
        val a = CodeAnalysis(mrUrl = "x")
        whenever(useCase.getAllAnalyses()).thenReturn(listOf(a))
        val r = controller.all()
        assertEquals(1, r.size)
    }

    @Test
    fun `status returns mapped DTO when found`() {
        val a = CodeAnalysis(mrUrl = "x")
        whenever(useCase.getAnalysis(1L)).thenReturn(a)
        val r = controller.status(1L)
        assertEquals(200, r.statusCode.value())
    }

    @Test
    fun `status returns 404 when not found`() {
        whenever(useCase.getAnalysis(99L)).thenReturn(null)
        assertEquals(404, controller.status(99L).statusCode.value())
    }

    @Test
    fun `delete returns 404 when not found`() {
        whenever(useCase.getAnalysis(99L)).thenReturn(null)
        val r = controller.delete(99L)
        assertEquals(404, r.statusCode.value())
    }

    @Test
    fun `cancel returns 200 when not found`() {
        whenever(useCase.getAnalysis(99L)).thenReturn(null)
        val r = controller.cancel(99L)
        assertEquals(200, r.statusCode.value())
    }

    @Test
    fun `saveDiagram returns the saved path`() {
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved("/tmp/diagram.puml"))

        val r =
            controller.saveDiagram(
                1L,
                CodeAnalysisApiController.SaveDiagramRequest(path = "/tmp", filename = "diagram.puml", content = "x"),
            )

        assertEquals(200, r.statusCode.value())
        assertEquals("/tmp/diagram.puml", r.body!!["savedPath"])
    }

    @Test
    fun `saveDiagram returns 400 when the directory is missing`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))

        val r =
            controller.saveDiagram(
                1L,
                CodeAnalysisApiController.SaveDiagramRequest(path = "/missing", filename = "diagram.puml", content = "x"),
            )

        assertEquals(400, r.statusCode.value())
        assertEquals("code_analysis.be_dir_not_found_save", r.body!!["error"])
    }

    @Test
    fun `saveDiagram returns 500 when the write fails`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))

        val r =
            controller.saveDiagram(
                1L,
                CodeAnalysisApiController.SaveDiagramRequest(path = "/tmp", filename = "diagram.puml", content = "x"),
            )

        assertEquals(500, r.statusCode.value())
        assertTrue(r.body!!["error"].toString().startsWith("code_analysis.be_save_failed"))
    }
}
