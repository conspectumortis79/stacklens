package com.stacklens.adapter.inbound.rest.grafana

import com.stacklens.application.port.inbound.workspace.TextFileSaveRequest
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryListing
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteRejection
import com.stacklens.application.port.inbound.workspace.WorkspaceWriteResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files

class GrafanaFileBrowserControllerAdditionalTest {
    private lateinit var tmpDir: File
    private lateinit var controller: GrafanaFileBrowserController

    private val workspaceFiles: WorkspaceFileUseCase = mock()

    @BeforeEach
    fun setUp() {
        tmpDir = Files.createTempDirectory("grafana-browser").toFile()
        controller =
            GrafanaFileBrowserController(
                workspaceFiles,
                com.stacklens.application.service
                    .FakeI18n(),
            )
    }

    @AfterEach
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `browseFiles returns bad request when path doesn't exist`() {
        val r = controller.browseFiles("/nonexistent/path/abc123")
        assertEquals(400, r.statusCode.value())
    }

    @Test
    fun `browseFiles with null path defaults to user home`() {
        whenever(workspaceFiles.browseDirectory(null)).thenReturn(
            WorkspaceDirectoryListing(path = "/home/user", parentPath = null, entries = emptyList()),
        )
        val r = controller.browseFiles(null)
        assertEquals(200, r.statusCode.value())
        assertTrue(r.body.toString().contains("currentPath"))
    }

    @Test
    fun `browseFiles with blank path defaults to user home`() {
        whenever(workspaceFiles.browseDirectory("   ")).thenReturn(
            WorkspaceDirectoryListing(path = "/home/user", parentPath = null, entries = emptyList()),
        )
        val r = controller.browseFiles("   ")
        assertEquals(200, r.statusCode.value())
    }

    @Test
    fun `browseFiles with valid path returns directories only`() {
        whenever(workspaceFiles.browseDirectory(tmpDir.absolutePath)).thenReturn(
            WorkspaceDirectoryListing(
                path = tmpDir.absolutePath,
                parentPath = tmpDir.parent,
                entries =
                    listOf(
                        directoryEntry("sub1"),
                        directoryEntry("sub2"),
                        WorkspaceDirectoryEntry(name = "file.txt", path = "/tmp/file.txt", isDirectory = false, isFile = true, size = 1L),
                        directoryEntry(".hidden"),
                    ),
            ),
        )
        val r = controller.browseFiles(tmpDir.absolutePath)
        assertEquals(200, r.statusCode.value())
        val body = r.body as Map<*, *>
        val items = body["items"] as List<*>
        assertTrue(items.any { (it as Map<*, *>)["name"] == "sub1" })
        assertTrue(items.any { (it as Map<*, *>)["name"] == "sub2" })
        assertTrue(items.any { (it as Map<*, *>)["name"] == ".hidden" })
        assertTrue(items.none { (it as Map<*, *>)["name"] == "file.txt" })
    }

    @Test
    fun `browseFiles returns sorted items`() {
        whenever(workspaceFiles.browseDirectory(tmpDir.absolutePath)).thenReturn(
            WorkspaceDirectoryListing(
                path = tmpDir.absolutePath,
                parentPath = tmpDir.parent,
                entries = listOf(directoryEntry("zebra"), directoryEntry("apple")),
            ),
        )
        val r = controller.browseFiles(tmpDir.absolutePath)
        val body = r.body as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val items = body["items"] as List<Map<*, *>>
        assertEquals("apple", items[0]["name"])
        assertEquals("zebra", items[1]["name"])
    }

    private fun directoryEntry(name: String) = WorkspaceDirectoryEntry(name = name, path = "$tmpDir/$name", isDirectory = true, isFile = false, size = 0L)

    @Test
    fun `saveDashboardJson with blank content returns 400`() {
        val r =
            controller.saveDashboardJson(
                GrafanaFileBrowserController.SaveDashboardJsonRequest(
                    content = "",
                    path = tmpDir.absolutePath,
                    filename = "test.json",
                ),
            )
        assertEquals(400, r.statusCode.value())
    }

    @Test
    fun `saveDashboardJson with blank path returns 400`() {
        val r =
            controller.saveDashboardJson(
                GrafanaFileBrowserController.SaveDashboardJsonRequest(
                    content = "{}",
                    path = "",
                    filename = "test.json",
                ),
            )
        assertEquals(400, r.statusCode.value())
    }

    @Test
    fun `saveDashboardJson with nonexistent path returns 400`() {
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.DIRECTORY_NOT_FOUND))
        val r =
            controller.saveDashboardJson(
                GrafanaFileBrowserController.SaveDashboardJsonRequest(
                    content = "{}",
                    path = "/nonexistent/path/abc",
                    filename = "test.json",
                ),
            )
        assertEquals(400, r.statusCode.value())
    }

    @Test
    fun `saveDashboardJson with valid inputs saves through the workspace`() {
        val savedPath = File(tmpDir, "test-dashboard.json").absolutePath
        whenever(workspaceFiles.saveText(any())).thenReturn(WorkspaceWriteResult.Saved(savedPath))
        val r =
            controller.saveDashboardJson(
                GrafanaFileBrowserController.SaveDashboardJsonRequest(
                    content = """{"dashboard":{}}""",
                    path = tmpDir.absolutePath,
                    filename = "test-dashboard.json",
                ),
            )
        assertEquals(200, r.statusCode.value())
        assertEquals(mapOf("savedPath" to savedPath), r.body)
        val captor = argumentCaptor<TextFileSaveRequest>()
        verify(workspaceFiles).saveText(captor.capture())
        assertEquals("test-dashboard.json", captor.firstValue.filename)
        assertEquals("""{"dashboard":{}}""", captor.firstValue.content)
    }
}
