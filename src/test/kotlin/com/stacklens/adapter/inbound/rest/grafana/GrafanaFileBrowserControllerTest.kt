package com.stacklens.adapter.inbound.rest.grafana

import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryListing
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GrafanaFileBrowserControllerTest {
    private val workspaceFiles: WorkspaceFileUseCase = mock()

    private val controller =
        GrafanaFileBrowserController(
            workspaceFiles,
            com.stacklens.application.service
                .FakeI18n(),
        )

    private fun directory(
        name: String,
        path: String = "/tmp/$name",
    ) = WorkspaceDirectoryEntry(name = name, path = path, isDirectory = true, isFile = false, size = 0L)

    private fun file(
        name: String,
        path: String = "/tmp/$name",
    ) = WorkspaceDirectoryEntry(name = name, path = path, isDirectory = false, isFile = true, size = 1L)

    private fun listing(
        entries: List<WorkspaceDirectoryEntry>,
        path: String = "/tmp",
        parentPath: String? = "/",
    ) = WorkspaceDirectoryListing(path = path, parentPath = parentPath, entries = entries)

    @Suppress("UNCHECKED_CAST")
    private fun itemsOf(body: Any?): List<Map<String, Any?>> = ((body as Map<String, Any?>)["items"] as List<*>).map { it as Map<String, Any?> }

    @Test
    fun `browseFiles returns 400 when the directory cannot be listed`() {
        whenever(workspaceFiles.browseDirectory("/does-not-exist")).thenReturn(null)
        val response = controller.browseFiles("/does-not-exist")
        assertEquals(400, response.statusCode.value())
    }

    @Test
    fun `browseFiles returns 200 with empty items for an empty directory`() {
        whenever(workspaceFiles.browseDirectory(any())).thenReturn(listing(emptyList()))
        val response = controller.browseFiles("/tmp")
        assertEquals(200, response.statusCode.value())
        assertNotNull((response.body as Map<*, *>)["items"])
        assertTrue(itemsOf(response.body).isEmpty())
    }

    @Test
    fun `browseFiles lists directories only and skips files`() {
        whenever(workspaceFiles.browseDirectory(any())).thenReturn(
            listing(listOf(directory("visible-dir"), file("visible-file.txt"), directory(".hidden-dir"))),
        )
        val response = controller.browseFiles("/tmp")
        assertEquals(200, response.statusCode.value())
        val items = itemsOf(response.body)
        assertEquals(listOf(".hidden-dir", "visible-dir"), items.map { it["name"] })
        assertEquals("directory", items[1]["type"])
        assertEquals("/tmp/visible-dir", items[1]["path"])
    }

    @Test
    fun `browseFiles falls back to user home when path is null`() {
        whenever(workspaceFiles.browseDirectory(null)).thenReturn(listing(emptyList(), path = "/home/user"))
        val response = controller.browseFiles(null)
        assertEquals(200, response.statusCode.value())
        assertEquals("/home/user", (response.body as Map<*, *>)["currentPath"])
    }

    @Test
    fun `browseFiles falls back to user home when path is blank`() {
        whenever(workspaceFiles.browseDirectory("")).thenReturn(listing(emptyList(), path = "/home/user"))
        val response = controller.browseFiles("")
        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `browseFiles sorts items alphabetically case-insensitive`() {
        whenever(workspaceFiles.browseDirectory(any())).thenReturn(
            listing(listOf(directory("Zebra"), directory("alpha"), directory("Beta"))),
        )
        val response = controller.browseFiles("/tmp")
        assertEquals(listOf("alpha", "Beta", "Zebra"), itemsOf(response.body).map { it["name"] })
    }

    @Test
    fun `browseFiles includes parentPath when directory has a parent`() {
        whenever(workspaceFiles.browseDirectory(any())).thenReturn(listing(emptyList(), parentPath = "/parent"))
        val response = controller.browseFiles("/tmp/child")
        assertEquals("/parent", (response.body as Map<*, *>)["parentPath"])
    }

    @Test
    fun `browseFiles exposes a null parent path for the filesystem root`() {
        whenever(workspaceFiles.browseDirectory(any())).thenReturn(listing(emptyList(), path = "/", parentPath = null))
        val response = controller.browseFiles("/")
        assertNull((response.body as Map<*, *>)["parentPath"])
    }
}
