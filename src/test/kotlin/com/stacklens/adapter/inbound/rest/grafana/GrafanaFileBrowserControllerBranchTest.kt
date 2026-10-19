package com.stacklens.adapter.inbound.rest.grafana

import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files

class GrafanaFileBrowserControllerBranchTest {
    private val i18n =
        com.stacklens.application.service
            .FakeI18n()
    private val workspaceFiles = mock<WorkspaceFileUseCase>()

    private fun controller() = GrafanaFileBrowserController(workspaceFiles, i18n = i18n)

    private fun captureRequest(): TextFileSaveRequest {
        val captor = argumentCaptor<TextFileSaveRequest>()
        verify(workspaceFiles).saveText(captor.capture())
        return captor.firstValue
    }

    @Test
    fun `browseFiles returns directories for a valid path`() {
        whenever(workspaceFiles.browseDirectory(any())).thenReturn(
            com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryListing(
                path = "/tmp",
                parentPath = "/",
                entries =
                    listOf(
                        com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry(
                            name = "sub1",
                            path = "/tmp/sub1",
                            isDirectory = true,
                            isFile = false,
                            size = 0L,
                        ),
                    ),
            ),
        )
        val result = controller().browseFiles("/tmp")
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `browseFiles returns 400 when path does not exist`() {
        whenever(workspaceFiles.browseDirectory(any())).thenReturn(null)
        val result = controller().browseFiles("/nonexistent/path/12345")
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `browseFiles returns 200 for null path using user home`() {
        whenever(workspaceFiles.browseDirectory(null)).thenReturn(
            com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryListing(
                path = "/home/user",
                parentPath = null,
                entries = emptyList(),
            ),
        )
        val result = controller().browseFiles(null)
        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `saveDashboardJson returns 200 on success`() {
        val tmp = Files.createTempDirectory("grafana-save").toFile()
        val savedPath = File(tmp, "dashboard.json").absolutePath
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved(savedPath))
        val req =
            GrafanaFileBrowserController.SaveDashboardJsonRequest(
                content = """{"foo":"bar"}""",
                path = tmp.absolutePath,
                filename = "dashboard.json",
            )
        val result = controller().saveDashboardJson(req)
        assertEquals(200, result.statusCode.value())
        assertEquals(mapOf("savedPath" to savedPath), result.body)
        val request = captureRequest()
        assertEquals(tmp.absolutePath, request.directory)
        assertEquals("dashboard.json", request.filename)
        assertEquals("""{"foo":"bar"}""", request.content)
        assertEquals(true, request.canonicalizeDirectory)
        assertEquals("grafana-dashboard.json", request.fallbackFilename)
        assertEquals(true, request.enforceJsonExtension)
    }

    @Test
    fun `saveDashboardJson returns 400 on blank content`() {
        val req = GrafanaFileBrowserController.SaveDashboardJsonRequest(content = "", path = "/tmp")
        val result = controller().saveDashboardJson(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `saveDashboardJson returns 400 on blank path`() {
        val req = GrafanaFileBrowserController.SaveDashboardJsonRequest(content = "x", path = "")
        val result = controller().saveDashboardJson(req)
        assertEquals(400, result.statusCode.value())
    }

    @Test
    fun `saveDashboardJson returns 400 on non-existent directory`() {
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))
        val req =
            GrafanaFileBrowserController.SaveDashboardJsonRequest(
                content = "x",
                path = "/nonexistent/dir/12345",
                filename = "a.json",
            )
        val result = controller().saveDashboardJson(req)
        assertEquals(400, result.statusCode.value())
        assertTrue((result.body as Map<*, *>)["error"].toString().startsWith("grafana_analysis.be_dir_not_found_path"))
    }

    @Test
    fun `saveDashboardJson returns 500 on write failure`() {
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.WRITE_FAILED))
        val req =
            GrafanaFileBrowserController.SaveDashboardJsonRequest(
                content = "x",
                path = "/tmp",
                filename = "a.json",
            )
        val result = controller().saveDashboardJson(req)
        assertEquals(500, result.statusCode.value())
        assertTrue((result.body as Map<*, *>)["error"].toString().startsWith("grafana_analysis.be_save_failed"))
    }

    @Test
    fun `saveDashboardJson adds json extension when missing`() {
        val tmp = Files.createTempDirectory("grafana-save-ext").toFile()
        val savedPath = File(tmp, "noext.json").absolutePath
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved(savedPath))
        val req =
            GrafanaFileBrowserController.SaveDashboardJsonRequest(
                content = "x",
                path = tmp.absolutePath,
                filename = "noext",
            )
        val result = controller().saveDashboardJson(req)
        assertEquals(200, result.statusCode.value())
        assertEquals(savedPath, result.body?.let { (it as Map<*, *>)["savedPath"] })
        assertEquals(true, captureRequest().enforceJsonExtension)
    }

    @Test
    fun `saveDashboardJson uses default filename when blank`() {
        val tmp = Files.createTempDirectory("grafana-save-default").toFile()
        val savedPath = File(tmp, "grafana-dashboard.json").absolutePath
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved(savedPath))
        val req =
            GrafanaFileBrowserController.SaveDashboardJsonRequest(
                content = "x",
                path = tmp.absolutePath,
                filename = "",
            )
        val result = controller().saveDashboardJson(req)
        assertEquals(200, result.statusCode.value())
        assertEquals(savedPath, result.body?.let { (it as Map<*, *>)["savedPath"] })
        val request = captureRequest()
        assertEquals("", request.filename)
        assertEquals("grafana-dashboard.json", request.fallbackFilename)
    }

    @Test
    fun `saveDashboardJson delegates unsafe filename characters to the workspace`() {
        val tmp = Files.createTempDirectory("grafana-save-sanitize").toFile()
        val savedPath = File(tmp, ".._etc_passwd.json").absolutePath
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved(savedPath))
        val req =
            GrafanaFileBrowserController.SaveDashboardJsonRequest(
                content = "x",
                path = tmp.absolutePath,
                filename = "../etc/passwd",
            )
        val result = controller().saveDashboardJson(req)
        assertEquals(200, result.statusCode.value())
        assertEquals(savedPath, result.body?.let { (it as Map<*, *>)["savedPath"] })
        assertEquals("../etc/passwd", captureRequest().filename)
    }
}
