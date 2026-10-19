package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.analysis.CodeAnalysisUseCase
import com.stacklens.application.port.inbound.loadtest.LoadTestAnalysisUseCase
import com.stacklens.application.port.inbound.pipeline.PipelineAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.application.port.out.platform.HostBrowserPort
import com.stacklens.application.service.ExternalBrowserService
import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.PipelineAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DesktopIntegrationControllerTranslationCoverageTest {
    private val controller =
        DesktopIntegrationController(
            ExternalBrowserService(unusableHostBrowser()),
            i18n =
                com.stacklens.application.service
                    .FakeI18n(),
        )

    private companion object {
        private fun unusableHostBrowser(): HostBrowserPort =
            object : HostBrowserPort {
                override fun browse(url: String) = Unit
            }
    }

    @Test
    fun `a missing url reports the translated key`() {
        val response = controller.openUrl(mapOf("something" to "else"))

        assertEquals("app.be_missing_url", response.body?.get("error"))
    }

    @Test
    fun `an unsupported url scheme reports the bare translation key`() {
        val response = controller.openUrl(mapOf("url" to "ftp://example.com"))

        assertEquals("app.be_invalid_url_scheme", response.body?.get("error"))
    }
}

class CodeAnalysisApiControllerTranslationCoverageTest {
    private val service = mock<CodeAnalysisUseCase>()
    private val workspaceFiles = mock<WorkspaceFileUseCase>()
    private val controller =
        CodeAnalysisApiController(
            service,
            workspaceFiles,
            i18n =
                com.stacklens.application.service
                    .FakeI18n(),
        )

    @Test
    fun `creating an mr without a gitlab target asks the user to select one`() {
        whenever(service.getAnalysis(1L)).thenReturn(CodeAnalysis(id = 1L))

        val response = controller.createMr(1L, CodeAnalysisApiController.CreateMrDto())

        assertEquals("code_analysis.mr_select_hint", response.body?.get("error"))
    }

    @Test
    fun `a failure without a message falls back to the bare translation key`() {
        whenever(service.getAnalysis(1L)).thenReturn(
            CodeAnalysis(id = 1L, gitlabConfigId = 2L, gitlabProjectId = 3L),
        )
        doThrow(
            object : RuntimeException() {
                override val message: String? = null
            },
        ).whenever(service)
            .createMr(1L, 2L, 3L)

        val response =
            controller.createMr(
                1L,
                CodeAnalysisApiController.CreateMrDto(gitlabConfigId = 2L, projectId = 3L),
            )

        assertEquals("common.be_unknown_error", response.body?.get("error"))
    }

    @Test
    fun `a save into a missing directory is rejected with the bare translation key`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))
        val response =
            controller.saveDiagram(
                1L,
                CodeAnalysisApiController.SaveDiagramRequest(path = "/no/such/directory", filename = "d.mmd", content = "x"),
            )

        assertEquals("code_analysis.be_dir_not_found_save", response.body?.get("error"))
    }

    @Test
    fun `a failed write is reported with the bare translation key`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))

        val response =
            controller.saveDiagram(
                1L,
                CodeAnalysisApiController.SaveDiagramRequest(path = "/tmp", filename = "d.mmd", content = "x"),
            )

        assertTrue(
            response.body
                ?.get("error")
                .toString()
                .startsWith("code_analysis.be_save_failed"),
        )
    }
}

class LoadTestApiControllerTranslationCoverageTest {
    private val service = mock<LoadTestAnalysisUseCase>()
    private val workspaceFiles = mock<WorkspaceFileUseCase>()
    private val controller =
        LoadTestApiController(
            service,
            workspaceFiles,
            i18n =
                com.stacklens.application.service
                    .FakeI18n(),
        )

    @Test
    fun `a save into a missing directory is rejected with the bare translation key`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))
        val response =
            controller.saveScript(
                1L,
                LoadTestApiController.SaveScriptRequest(path = "/no/such/directory", filename = "s.js", content = "x"),
            )

        assertEquals("load_test.be_dir_not_found", response.body?.get("error"))
    }

    @Test
    fun `a failed write is reported with the bare translation key`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))

        val response =
            controller.saveScript(
                1L,
                LoadTestApiController.SaveScriptRequest(path = "/tmp", filename = "s.js", content = "x"),
            )

        assertTrue(
            response.body
                ?.get("error")
                .toString()
                .startsWith("load_test.be_save_failed"),
        )
    }
}

class PipelineAnalysisApiControllerTranslationCoverageTest {
    private val service = mock<PipelineAnalysisUseCase>()
    private val controller =
        PipelineAnalysisApiController(
            service,
            i18n =
                com.stacklens.application.service
                    .FakeI18n(),
        )

    @Test
    fun `a cancelled analysis without a status is reported as failed`() {
        whenever(service.getAnalysis(1L)).thenReturn(null)

        val response = controller.cancel(1L)

        assertEquals("FAILED", response.body?.get("status"))
    }

    @Test
    fun `a provider error without a message falls back to the bare translation key`() {
        whenever(service.getAnalysis(1L)).thenReturn(PipelineAnalysis(id = 1L))
        doThrow(
            object : RuntimeException() {
                override val message: String? = null
            },
        ).whenever(service)
            .createMrOrPr(any(), anyOrNull(), anyOrNull())

        val response = controller.createMrOrPr(1L, null)

        assertEquals("common.be_unknown_error", response.body?.get("error"))
    }
}
