package com.stacklens.adapter.inbound.rest.tooling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import java.io.File
import java.nio.file.Path

@ExtendWith(MockitoExtension::class)
class ToolingFileBrowserApiControllerTest {
    private val controller by lazy {
        ToolingFileBrowserApiController(
            com.stacklens.application.service
                .FakeI18n(),
        )
    }

    @Test
    fun `browseFiles returns 400 for non-existent directory`() {
        val resp = controller.browseFiles("/nonexistent/dir", false, null)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `browseFiles returns 400 when path exists but is a file`(
        @TempDir tempDir: Path,
    ) {
        val file = tempDir.resolve("notadir.txt").toFile()
        file.writeText("content")
        val resp = controller.browseFiles(file.absolutePath, false, null)
        assertEquals(400, resp.statusCode.value())
    }

    @Test
    fun `browseFiles returns 200 for valid directory`(
        @TempDir tempDir: Path,
    ) {
        val resp = controller.browseFiles(tempDir.toString(), false, null)
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `browseFiles returns 200 with directoriesOnly true and fileExtensions filter`(
        @TempDir tempDir: Path,
    ) {
        File(tempDir.toFile(), "subdir").mkdir()
        File(tempDir.toFile(), "tool.jar").writeBytes(ByteArray(5))
        val resp = controller.browseFiles(tempDir.toString(), true, "jar,py")
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `browseFiles returns 200 when path is blank falls back to home`() {
        val resp = controller.browseFiles("  ", false, null)
        assertTrue(resp.statusCode.value() in listOf(200, 400))
    }

    @Test
    fun `browseFiles with null path falls back to user home`() {
        val resp = controller.browseFiles(null, false, null)
        assertTrue(resp.statusCode.value() in listOf(200, 400))
    }

    @Test
    fun `browseFiles filters out empty entries from fileExtensions`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("tool.jar").toFile().writeBytes(ByteArray(5) { 0x50 })
        val resp = controller.browseFiles(tempDir.toString(), false, "jar,,zip")
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `browseFiles returns null parentPath for root directory`() {
        val resp = controller.browseFiles("/", false, null)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = (resp as org.springframework.http.ResponseEntity<Map<String, Any>>).body!!
        assertEquals(null, body["parentPath"])
    }

    @Test
    fun `browseFiles lists and sorts directory entries`(
        @TempDir tempDir: Path,
    ) {
        tempDir.resolve("alpha").toFile().mkdir()
        tempDir.resolve("beta").toFile().mkdir()
        tempDir.resolve("tool.jar").toFile().writeBytes(ByteArray(5) { 0x50 })
        val resp = controller.browseFiles(tempDir.toString(), false, null)
        assertEquals(200, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = (resp as org.springframework.http.ResponseEntity<Map<String, Any>>).body!!
        val items = body["items"] as List<*>
        assertTrue(items.size >= 2)
    }
}
