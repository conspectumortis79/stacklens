package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.loadtest.LoadTestAnalysisUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.application.service.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LoadTestApiControllerI18nTranslationCoverageTest {
    private val workspaceFiles = mock<WorkspaceFileUseCase>()
    private val controller = LoadTestApiController(mock<LoadTestAnalysisUseCase>(), workspaceFiles, FakeI18n())

    @Test
    fun `a rejected script path is translated through the injected i18n port`() {
        whenever(workspaceFiles.saveText(any()))
            .thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))
        val response =
            controller.saveScript(
                1L,
                LoadTestApiController.SaveScriptRequest(path = "/no/such/directory", filename = "s.js", content = "x"),
            )

        assertEquals("load_test.be_dir_not_found", response.body?.get("error"))
    }
}
