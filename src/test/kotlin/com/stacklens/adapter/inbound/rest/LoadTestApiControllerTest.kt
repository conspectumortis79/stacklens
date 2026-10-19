package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.loadtest.LoadTestAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.domain.model.LoadTestAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LoadTestApiControllerTest {
    private val service = mock<LoadTestAnalysisUseCase>()
    private val workspaceFiles = mock<WorkspaceFileUseCase>()
    private val i18n =
        com.stacklens.application.service
            .FakeI18n()
    private val controller = LoadTestApiController(service, workspaceFiles, i18n)

    @Test
    fun `all returns mapped analyses`() {
        val a = LoadTestAnalysis(repoConfigId = 1L)
        whenever(service.getAllAnalyses()).thenReturn(listOf(a))
        val r = controller.all()
        assertEquals(1, r.size)
    }

    @Test
    fun `status returns mapped DTO when found`() {
        val a = LoadTestAnalysis(repoConfigId = 1L)
        whenever(service.getAnalysis(1L)).thenReturn(a)
        val r = controller.status(1L)
        assertEquals(200, r.statusCode.value())
    }

    @Test
    fun `status returns 404 when not found`() {
        whenever(service.getAnalysis(99L)).thenReturn(null)
        assertEquals(404, controller.status(99L).statusCode.value())
    }

    @Test
    fun `delete returns 404 when not found`() {
        whenever(service.getAnalysis(1L)).thenReturn(null)
        val r = controller.delete(1L)
        assertEquals(404, r.statusCode.value())
    }

    @Test
    fun `cancel returns 200`() {
        whenever(service.getAnalysis(1L)).thenReturn(null)
        val r = controller.cancel(1L)
        assertEquals(200, r.statusCode.value())
    }

    @Test
    fun `saveScript returns the saved path`() {
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved("/tmp/script.js"))

        val r =
            controller.saveScript(
                1L,
                LoadTestApiController.SaveScriptRequest(path = "/tmp", filename = "script.js", content = "x"),
            )

        assertEquals(200, r.statusCode.value())
        assertEquals("/tmp/script.js", r.body!!["savedPath"])
    }

    @Test
    fun `saveScript returns 400 when the directory is missing`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))

        val r =
            controller.saveScript(
                1L,
                LoadTestApiController.SaveScriptRequest(path = "/missing", filename = "script.js", content = "x"),
            )

        assertEquals(400, r.statusCode.value())
        assertEquals("load_test.be_dir_not_found", r.body!!["error"])
    }

    @Test
    fun `saveScript returns 500 when the write fails`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))

        val r =
            controller.saveScript(
                1L,
                LoadTestApiController.SaveScriptRequest(path = "/tmp", filename = "script.js", content = "x"),
            )

        assertEquals(500, r.statusCode.value())
        assertTrue(r.body!!["error"].toString().startsWith("load_test.be_save_failed"))
    }
}
