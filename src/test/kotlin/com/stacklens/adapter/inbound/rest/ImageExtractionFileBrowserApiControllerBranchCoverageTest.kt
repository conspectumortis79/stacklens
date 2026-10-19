package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryListing
import com.stacklens.application.port.inbound.workspace.WorkspaceFileInfo
import com.stacklens.application.port.inbound.workspace.WorkspaceFileUseCase
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class ImageExtractionFileBrowserApiControllerBranchCoverageTest {
    private val workspaceFiles: WorkspaceFileUseCase = mock()

    private val controller = ImageExtractionFileBrowserApiController(workspaceFiles, FakeI18n())

    private fun bodyOf(response: Any?): Map<*, *> = response as Map<*, *>

    private fun itemsOf(response: Any?): List<Map<*, *>> = (bodyOf(response)["items"] as List<*>).map { it as Map<*, *> }

    private fun errorOf(response: Any?): Any? = bodyOf(response)["error"]

    private fun entry(
        name: String,
        directory: Boolean = false,
        file: Boolean = true,
        size: Long = 0L,
    ) = WorkspaceDirectoryEntry(name = name, path = "/tmp/$name", isDirectory = directory, isFile = file, size = size)

    private fun listing(entries: List<WorkspaceDirectoryEntry>) = WorkspaceDirectoryListing(path = "/tmp/root", parentPath = "/tmp", entries = entries)

    private fun fileInfo(
        name: String,
        size: Long = 4L,
        directory: Boolean = false,
        file: Boolean = true,
    ) = WorkspaceFileInfo(name = name, path = "/$name", size = size, isDirectory = directory, isFile = file)

    @Test
    fun `browse reports missing or non directory paths with translations`() {
        whenever(workspaceFiles.browseDirectory(anyOrNull())).thenReturn(null)
        assertEquals("i18n:tooling.be_dir_not_found", errorOf(controller.browseFiles("/missing", false, null).body))
    }

    @Test
    fun `browse exposes the parent path and filters entries`() {
        whenever(workspaceFiles.browseDirectory("/tmp/root")).thenReturn(
            listing(
                listOf(
                    entry("sub", directory = true, file = false),
                    entry("a.png", size = 4L),
                    entry("b.SVG", size = 5L),
                    entry("skip.txt"),
                    entry("ghost.png", file = false),
                ),
            ),
        )

        val body = controller.browseFiles("/tmp/root", false, " .PNG , ,svg,").body

        assertEquals(listOf("sub", "a.png", "b.SVG"), itemsOf(body).map { it["name"] })
        assertEquals("/tmp/root", bodyOf(body)["currentPath"])
        assertEquals("/tmp", bodyOf(body)["parentPath"])
    }

    @Test
    fun `browse keeps only directories when files are filtered out`() {
        whenever(workspaceFiles.browseDirectory(anyOrNull())).thenReturn(
            listing(listOf(entry("nested", directory = true, file = false), entry("a.png"))),
        )
        assertEquals(listOf("nested"), itemsOf(controller.browseFiles("/tmp/root", true, null).body).map { it["name"] })
    }

    @Test
    fun `browse ignores entries whose extension is not requested`() {
        whenever(workspaceFiles.browseDirectory(anyOrNull())).thenReturn(listing(listOf(entry("a.png"))))
        assertTrue(itemsOf(controller.browseFiles("/tmp/root", false, "pdf").body).isEmpty())
    }

    @Test
    fun `browse yields an empty listing when the directory is empty`() {
        whenever(workspaceFiles.browseDirectory(anyOrNull())).thenReturn(listing(emptyList()))
        assertTrue(itemsOf(controller.browseFiles("/tmp/root", false, null).body).isEmpty())
    }

    @Test
    fun `browse supports a home fallback with a root parent`() {
        whenever(workspaceFiles.browseDirectory(null)).thenReturn(
            WorkspaceDirectoryListing(path = "/", parentPath = null, entries = listOf(entry("tmp", directory = true, file = false))),
        )
        val body = controller.browseFiles(null, false, null).body
        assertEquals("/", bodyOf(body)["currentPath"])
        assertNull(bodyOf(body)["parentPath"])
    }

    @Test
    fun `read reports blank paths and unreadable paths with translations`() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.readFile("  ").statusCode)
        whenever(workspaceFiles.fileInfo("/missing.png")).thenReturn(null)
        assertEquals(HttpStatus.BAD_REQUEST, controller.readFile("/missing.png").statusCode)
        whenever(workspaceFiles.fileInfo("/dir.png")).thenReturn(fileInfo("dir.png", directory = true, file = false))
        assertEquals(HttpStatus.BAD_REQUEST, controller.readFile("/dir.png").statusCode)
        whenever(workspaceFiles.fileInfo("/notes.txt")).thenReturn(fileInfo("notes.txt"))
        assertEquals(HttpStatus.BAD_REQUEST, controller.readFile("/notes.txt").statusCode)
    }

    @Test
    fun `read rejects files above the size limit`() {
        whenever(workspaceFiles.fileInfo("/big.png")).thenReturn(fileInfo("big.png", size = 16L * 1024L * 1024L + 1L))
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, controller.readFile("/big.png").statusCode)
    }

    @Test
    fun `read encodes every supported extension as base64`() {
        val expected =
            mapOf(
                "a.png" to "image/png",
                "b.jpg" to "image/jpeg",
                "c.jpeg" to "image/jpeg",
                "d.tif" to "image/tiff",
                "e.tiff" to "image/tiff",
                "f.svg" to "image/svg+xml",
            )
        expected.forEach { (name, mime) ->
            whenever(workspaceFiles.fileInfo("/$name")).thenReturn(fileInfo(name))
            whenever(workspaceFiles.readBytes("/$name")).thenReturn("data".toByteArray())
            val body = controller.readFile("/$name").body
            assertEquals(mime, bodyOf(body)["mimeType"])
            assertEquals(name, bodyOf(body)["name"])
            assertEquals(4L, bodyOf(body)["size"])
        }
    }

    @Test
    fun `read reports a vanished file as a read failure`() {
        whenever(workspaceFiles.fileInfo("/gone.png")).thenReturn(fileInfo("gone.png"))
        whenever(workspaceFiles.readBytes("/gone.png")).thenReturn(null)
        assertEquals("read_failed", errorOf(controller.readFile("/gone.png").body))
    }

    @Test
    fun `read reports io failures with and without a message`() {
        whenever(workspaceFiles.fileInfo("/io.png")).thenReturn(fileInfo("io.png"))
        org.mockito.Mockito
            .doThrow(IllegalStateException("disk gone"))
            .`when`(workspaceFiles)
            .readBytes("/io.png")
        assertEquals("disk gone", errorOf(controller.readFile("/io.png").body))
        org.mockito.Mockito
            .doThrow(IllegalStateException())
            .`when`(workspaceFiles)
            .readBytes("/io.png")
        assertEquals("read_failed", errorOf(controller.readFile("/io.png").body))
    }

    @Test
    fun `read passes the info path through to the response`() {
        whenever(workspaceFiles.fileInfo("/tmp/a.png")).thenReturn(fileInfo("a.png"))
        whenever(workspaceFiles.readBytes("/tmp/a.png")).thenReturn(byteArrayOf(1))
        val body = controller.readFile("/tmp/a.png").body
        assertEquals("/a.png", bodyOf(body)["path"])
    }
}
