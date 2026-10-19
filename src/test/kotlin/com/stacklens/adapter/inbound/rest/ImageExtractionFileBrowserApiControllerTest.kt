package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.workspace.WorkspaceDirectoryEntry
import com.stacklens.application.service.workspace.WorkspaceFileService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.File

class ImageExtractionFileBrowserApiControllerTest {
    private lateinit var tmpDir: File
    private lateinit var i18n: I18nUseCase
    private lateinit var controller: ImageExtractionFileBrowserApiController
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        tmpDir = File.createTempFile("image-x", "")
        tmpDir.delete()
        tmpDir.mkdirs()
        i18n = mock()
        whenever(i18n.t(any(), any())).thenAnswer { inv -> "i18n:${inv.arguments[0]}" }
        whenever(i18n.t(any<String>())).thenAnswer { inv -> "i18n:${inv.arguments[0]}" }
        val fileAdapter = JavaFileSystemAdapter()
        val workspaceFiles = WorkspaceFileService(fileAdapter, fileAdapter, fileAdapter, fileAdapter)
        controller = ImageExtractionFileBrowserApiController(workspaceFiles, i18n)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @AfterEach
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `browseFiles falls back to user home when no path is provided`() {
        mockMvc
            .perform(get("/api/image-extraction/files/browse"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentPath").exists())
    }

    @Test
    fun `browseFiles returns a 400 when the path does not exist`() {
        mockMvc
            .perform(get("/api/image-extraction/files/browse").param("path", "/definitely-not-real-${System.nanoTime()}"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `browseFiles returns a 400 when the path points at a file rather than a directory`() {
        val file = File(tmpDir, "solo.txt")
        file.writeText("hello")
        mockMvc
            .perform(get("/api/image-extraction/files/browse").param("path", file.absolutePath))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `browseFiles returns directories and matching files sorted with dirs first`() {
        File(tmpDir, "sub").mkdir()
        File(tmpDir, "a.png").writeText("a")
        File(tmpDir, "b.txt").writeText("b")
        File(tmpDir, "c.jpg").writeText("c")
        File(tmpDir, "d.gif").writeText("d")

        mockMvc
            .perform(get("/api/image-extraction/files/browse").param("path", tmpDir.absolutePath))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentPath").value(tmpDir.absolutePath))
            .andExpect(jsonPath("$.items[?(@.name == 'sub')].type").value("directory"))
            .andExpect(jsonPath("$.items[?(@.name == 'a.png')].type").value("file"))
            .andExpect(jsonPath("$.items[?(@.name == 'c.jpg')].type").value("file"))
            .andExpect(jsonPath("$.items[?(@.name == 'b.txt')]").doesNotExist())
    }

    @Test
    fun `browseFiles with directoriesOnly=true hides files in the listing`() {
        File(tmpDir, "sub").mkdir()
        File(tmpDir, "a.png").writeText("a")

        mockMvc
            .perform(
                get("/api/image-extraction/files/browse")
                    .param("path", tmpDir.absolutePath)
                    .param("directoriesOnly", "true"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.name == 'a.png')]").doesNotExist())
            .andExpect(jsonPath("$.items[?(@.name == 'sub')]").exists())
    }

    @Test
    fun `browseFiles honours a custom comma-separated fileExtensions filter`() {
        File(tmpDir, "a.tiff").writeText("a")
        File(tmpDir, "b.png").writeText("b")
        File(tmpDir, "c.txt").writeText("c")

        mockMvc
            .perform(
                get("/api/image-extraction/files/browse")
                    .param("path", tmpDir.absolutePath)
                    .param("fileExtensions", "PNG, .TIFF"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.name == 'a.tiff')]").exists())
            .andExpect(jsonPath("$.items[?(@.name == 'b.png')]").exists())
            .andExpect(jsonPath("$.items[?(@.name == 'c.txt')]").doesNotExist())
    }

    @Test
    fun `browseFiles surfaces the parentPath to allow navigation one level up`() {
        val sub = File(tmpDir, "nested").apply { mkdir() }
        File(sub, "a.png").writeText("a")

        mockMvc
            .perform(get("/api/image-extraction/files/browse").param("path", sub.absolutePath))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parentPath").value(tmpDir.absolutePath))
    }

    @Test
    fun `browseFiles returns an empty list and currentPath when the directory has no entries`() {
        mockMvc
            .perform(get("/api/image-extraction/files/browse").param("path", tmpDir.absolutePath))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isArray)
            .andExpect(jsonPath("$.items").isEmpty)
    }

    @Test
    fun `browseFiles with an empty path falls back to user home`() {
        mockMvc
            .perform(
                get("/api/image-extraction/files/browse")
                    .param("path", "   "),
            ).andExpect(status().isOk)
    }

    @Test
    fun `browseFiles filters every entry out when fileExtensions resolves to an empty list`() {
        File(tmpDir, "a.png").writeText("a")
        File(tmpDir, "b.doc").writeText("b")

        mockMvc
            .perform(
                get("/api/image-extraction/files/browse")
                    .param("path", tmpDir.absolutePath)
                    .param("fileExtensions", " , "),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isEmpty)
    }

    @Test
    fun `safeEntry returns null for a file with a mismatching extension`() {
        val entry = WorkspaceDirectoryEntry(name = "b.txt", path = "/tmp/b.txt", isDirectory = false, isFile = true, size = 1L)
        val method =
            ImageExtractionFileBrowserApiController::class.java
                .declaredMethods
                .first { it.name == "safeEntry" }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result =
            method.invoke(controller, entry, false, listOf("png")) as Map<String, Any>?
        assertEquals(null, result)
    }

    @Test
    fun `safeEntry returns a directory entry for sub-directories`() {
        val entry = WorkspaceDirectoryEntry(name = "sub", path = "/tmp/sub", isDirectory = true, isFile = false, size = 0L)
        val method =
            ImageExtractionFileBrowserApiController::class.java
                .declaredMethods
                .first { it.name == "safeEntry" }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result =
            method.invoke(controller, entry, false, listOf("png")) as Map<String, Any>?
        assertNotNull(result)
        assertEquals("directory", result!!["type"])
    }

    @Test
    fun `safeEntry returns a file entry only when the extension matches`() {
        val entry = WorkspaceDirectoryEntry(name = "a.png", path = "/tmp/a.png", isDirectory = false, isFile = true, size = 4L)
        val method =
            ImageExtractionFileBrowserApiController::class.java
                .declaredMethods
                .first { it.name == "safeEntry" }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result =
            method.invoke(controller, entry, false, listOf("png")) as Map<String, Any>?
        assertNotNull(result)
        assertEquals("file", result!!["type"])
        assertEquals(4L, result["size"])
    }

    @Test
    fun `safeEntry hides regular files when directoriesOnly is true`() {
        val entry = WorkspaceDirectoryEntry(name = "a.png", path = "/tmp/a.png", isDirectory = false, isFile = true, size = 4L)
        val method =
            ImageExtractionFileBrowserApiController::class.java
                .declaredMethods
                .first { it.name == "safeEntry" }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result =
            method.invoke(controller, entry, true, listOf("png")) as Map<String, Any>?
        assertEquals(null, result)
    }

    @Test
    fun `safeEntry hides entries that are neither files nor directories`() {
        val entry = WorkspaceDirectoryEntry(name = "ghost.png", path = "/tmp/ghost.png", isDirectory = false, isFile = false, size = 0L)
        val method =
            ImageExtractionFileBrowserApiController::class.java
                .declaredMethods
                .first { it.name == "safeEntry" }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result =
            method.invoke(controller, entry, false, listOf("png")) as Map<String, Any>?
        assertEquals(null, result)
    }

    @Test
    fun `browseFiles lists a nested directory without crashing the response`() {
        val tmpSub = File(tmpDir, "sub").apply { mkdir() }
        mockMvc
            .perform(get("/api/image-extraction/files/browse").param("path", tmpSub.absolutePath))
            .andExpect(status().isOk)
        assertTrue(tmpSub.exists())
    }

    @Test
    fun `readFile returns base64 bytes for a supported image`() {
        val png = File(tmpDir, "a.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        mockMvc
            .perform(get("/api/image-extraction/files/read").param("path", png.absolutePath))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.base64Data").value("AQIDBA=="))
            .andExpect(jsonPath("$.mimeType").value("image/png"))
            .andExpect(jsonPath("$.size").value(4))
    }

    @Test
    fun `readFile returns base64 bytes for an svg image`() {
        val svg = File(tmpDir, "diagram.svg").apply { writeText("<svg viewBox=\"0 0 8 8\"/>") }
        mockMvc
            .perform(get("/api/image-extraction/files/read").param("path", svg.absolutePath))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mimeType").value("image/svg+xml"))
            .andExpect(jsonPath("$.base64Data").exists())
    }

    @Test
    fun `readFile rejects a blank path`() {
        mockMvc
            .perform(get("/api/image-extraction/files/read").param("path", "  "))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `readFile rejects a missing file`() {
        val missing = File(tmpDir, "ghost.png")
        mockMvc
            .perform(get("/api/image-extraction/files/read").param("path", missing.absolutePath))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `readFile rejects a directory`() {
        val dir = File(tmpDir, "sub").apply { mkdir() }
        mockMvc
            .perform(get("/api/image-extraction/files/read").param("path", dir.absolutePath))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `readFile rejects unsupported extensions`() {
        val txt = File(tmpDir, "b.txt").apply { writeText("hi") }
        mockMvc
            .perform(get("/api/image-extraction/files/read").param("path", txt.absolutePath))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `browseFiles lists svg files for the ki chat image filter`() {
        File(tmpDir, "a.svg").writeText("<svg/>")
        File(tmpDir, "b.gif").writeText("nope")
        mockMvc
            .perform(
                get("/api/image-extraction/files/browse")
                    .param("path", tmpDir.absolutePath)
                    .param("fileExtensions", "svg,png,jpg,jpeg,tif,tiff"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.name == 'a.svg')]").exists())
            .andExpect(jsonPath("$.items[?(@.name == 'b.gif')]").doesNotExist())
    }
}
