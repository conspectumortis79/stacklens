package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.service.workspace.WorkspaceFileService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.nio.file.Path

class ImageExtractionFileBrowserApiControllerBranchTest {
    private val fileAdapter = JavaFileSystemAdapter()

    private val controller =
        ImageExtractionFileBrowserApiController(
            WorkspaceFileService(fileAdapter, fileAdapter, fileAdapter, fileAdapter),
            com.stacklens.application.service
                .FakeI18n(),
        )

    private fun bodyOf(resp: ResponseEntity<Any>): Map<*, *> = requireNotNull(resp.body) as Map<*, *>

    private fun itemsOf(resp: ResponseEntity<Any>): List<Map<*, *>> = (requireNotNull(bodyOf(resp)["items"]) as List<*>).map { requireNotNull(it) as Map<*, *> }

    @Test
    fun `browse returns bad request for missing directory`() {
        val resp = controller.browseFiles("/does/not/exist", false, null)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `browse lists entries sorted directories first`(
        @TempDir tmp: Path,
    ) {
        java.io.File(tmp.toFile(), "b.png").writeText("x")
        java.io.File(tmp.toFile(), "a.txt").writeText("x")
        java.io.File(tmp.toFile(), "sub").mkdirs()
        val resp = controller.browseFiles(tmp.toString(), false, null)
        assertEquals(HttpStatus.OK, resp.statusCode)
        val items = itemsOf(resp)
        assertEquals("sub", items[0]["name"])
        assertTrue(items.any { it["name"] == "b.png" })
        assertTrue(items.none { it["name"] == "a.txt" })
        assertEquals(tmp.toString(), bodyOf(resp)["currentPath"])
    }

    @Test
    fun `browse with directoriesOnly and custom extensions`(
        @TempDir tmp: Path,
    ) {
        java.io.File(tmp.toFile(), "x.txt").writeText("x")
        java.io.File(tmp.toFile(), "sub").mkdirs()
        val resp = controller.browseFiles(tmp.toString(), true, "txt")

        val items = itemsOf(resp)
        assertEquals(1, items.size)
        assertEquals("sub", items.single()["name"])
        assertEquals("directory", items.single()["type"])
    }

    @Test
    fun `browse ignores hidden files and falls back to home`(
        @TempDir tmp: Path,
    ) {
        val hidden = java.io.File(tmp.toFile(), ".hidden.png")
        hidden.writeText("x")
        val resp = controller.browseFiles(tmp.toString(), false, null)

        val items = itemsOf(resp)
        assertTrue(items.none { it["name"] == ".hidden.png" })
        val home = controller.browseFiles(null, false, null)
        assertEquals(HttpStatus.OK, home.statusCode)
    }

    @Test
    fun `readFile returns bad request for missing or unsupported file`(
        @TempDir tmp: Path,
    ) {
        assertEquals(HttpStatus.BAD_REQUEST, controller.readFile("  ").statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, controller.readFile(tmp.resolve("missing.png").toString()).statusCode)
        val txt = java.io.File(tmp.toFile(), "a.txt")
        txt.writeText("x")
        assertEquals(HttpStatus.BAD_REQUEST, controller.readFile(txt.absolutePath).statusCode)
    }

    @Test
    fun `readFile rejects oversized files`(
        @TempDir tmp: Path,
    ) {
        val big = java.io.File(tmp.toFile(), "big.png")
        big.writeBytes(ByteArray(17 * 1024 * 1024))
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, controller.readFile(big.absolutePath).statusCode)
    }

    @Test
    fun `readFile returns base64 content`(
        @TempDir tmp: Path,
    ) {
        val png = java.io.File(tmp.toFile(), "img.png")
        png.writeBytes(byteArrayOf(1, 2, 3))
        val resp = controller.readFile(png.absolutePath)
        assertEquals(HttpStatus.OK, resp.statusCode)
        val body = bodyOf(resp)
        assertEquals("image/png", body["mimeType"])
        assertTrue((body["base64Data"] as String).isNotBlank())
        val jpg = java.io.File(tmp.toFile(), "img.jpg")
        jpg.writeBytes(byteArrayOf(1))
        assertEquals("image/jpeg", bodyOf(controller.readFile(jpg.absolutePath))["mimeType"])
        val tif = java.io.File(tmp.toFile(), "img.tif")
        tif.writeBytes(byteArrayOf(1))
        assertEquals("image/tiff", bodyOf(controller.readFile(tif.absolutePath))["mimeType"])
    }
}
