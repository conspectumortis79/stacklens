package com.stacklens.adapter.inbound.rest.grafana

import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class GrafanaFileBrowserControllerSaveFailureCoverageTest {
    private val workspaceWithoutI18n: WorkspaceFileUseCase = mock()
    private val workspaceWithI18n: WorkspaceFileUseCase = mock()

    private val controllerWithoutI18n =
        GrafanaFileBrowserController(
            workspaceWithoutI18n,
            com.stacklens.application.service
                .FakeI18n(),
        )
    private val controllerWithI18n = GrafanaFileBrowserController(workspaceWithI18n, FakeI18n())

    @Test
    fun `a workspace write rejection makes the save fail with the translated reason`() {
        whenever(workspaceWithoutI18n.saveText(any())).thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))

        val response =
            controllerWithoutI18n.saveDashboardJson(
                GrafanaFileBrowserController.SaveDashboardJsonRequest(
                    content = "{\"panels\":[]}",
                    path = "/tmp",
                    filename = "grafana-dashboard",
                ),
            )

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        val error = (response.body as Map<*, *>)["error"] as String
        assertTrue(error.startsWith("grafana_analysis.be_save_failed"), error)
    }

    @Test
    fun `the rejection reason is rendered for the client`() {
        whenever(workspaceWithI18n.saveText(any())).thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))

        val response =
            controllerWithI18n.saveDashboardJson(
                GrafanaFileBrowserController.SaveDashboardJsonRequest(
                    content = "{}",
                    path = "/tmp",
                    filename = "broken.json",
                ),
            )

        val error = (response.body as Map<*, *>)["error"] as String
        assertTrue(error.contains("i18n:grafana_analysis.be_save_failed|WRITE_FAILED"), error)
    }

    @Test
    fun `non-directory rejections become the save failure response`() {
        val reasons =
            listOf(
                WorkspaceWriteRejection.PATH_OUTSIDE_ALLOWED_ROOT,
                WorkspaceWriteRejection.TARGET_NOT_A_FILE,
                WorkspaceWriteRejection.EXTENSION_NOT_EDITABLE,
            )
        for (reason in reasons) {
            val workspaceFiles: WorkspaceFileUseCase = mock()
            whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Rejected(reason))
            val controller =
                GrafanaFileBrowserController(
                    workspaceFiles,
                    com.stacklens.application.service
                        .FakeI18n(),
                )

            val response =
                controller.saveDashboardJson(
                    GrafanaFileBrowserController.SaveDashboardJsonRequest(
                        content = "{}",
                        path = "/tmp",
                        filename = "silent.json",
                    ),
                )

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode, reason.name)
            val error = (response.body as Map<*, *>)["error"] as String
            assertTrue(error.startsWith("grafana_analysis.be_save_failed"), error)
        }
    }

    @Test
    fun `browsing the filesystem root has no parent path`() {
        whenever(workspaceWithI18n.browseDirectory("/")).thenReturn(
            com.stacklens.application.port.inbound.workspace
                .WorkspaceDirectoryListing(path = "/", parentPath = null, entries = emptyList()),
        )
        val body = controllerWithI18n.browseFiles(path = "/").body as Map<*, *>

        assertNull(body["parentPath"])
        assertEquals("/", body["currentPath"])
    }
}
