package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files

class ToolingApiSupportCoverageTest {
    @Test
    fun `translation delegates to the i18n port when present`() {
        val i18n = mock<I18nUseCase>()
        whenever(i18n.t(org.mockito.kotlin.eq("tooling.run"), any())).thenReturn("Werkzeug ausführen")

        assertEquals("Werkzeug ausführen", ToolingApiSupport.tr(i18n, "tooling.run", "maven"))
    }

    @Test
    fun `a multipart upload without a file name resolves to an empty name`() {
        val file = mock<MultipartFile>()
        whenever(file.originalFilename).thenReturn(null)

        assertEquals("", ToolingApiSupport.resolveOriginalFilename(file))
    }

    @Test
    fun `an upload with a file name is reported verbatim`() {
        val file = mock<MultipartFile>()
        whenever(file.originalFilename).thenReturn("cli.jar")

        assertEquals("cli.jar", ToolingApiSupport.resolveOriginalFilename(file))
    }

    @Test
    fun `a file whose metadata cannot be read is skipped`() {
        val hostile =
            object : File("/tmp/does-not-matter") {
                override fun isHidden(): Boolean = throw IllegalStateException("stat failed")
            }

        assertNull(ToolingApiSupport.safeFileEntry(hostile, directoriesOnly = false, extensions = null))
    }

    @Test
    fun `directories and allowed extensions are reported while hidden entries vanish`() {
        val root = Files.createTempDirectory("tooling-support").toFile()
        try {
            val dir = File(root, "sub").apply { mkdir() }
            val jar = File(root, "lib.jar").apply { writeText("x") }
            val other = File(root, "notes.txt").apply { writeText("x") }

            val directoryEntry = ToolingApiSupport.safeFileEntry(dir, directoriesOnly = true, extensions = null)
            assertEquals("directory", directoryEntry?.getValue("type"))
            assertEquals(dir.absolutePath, directoryEntry?.getValue("path"))
            assertEquals("file", ToolingApiSupport.safeFileEntry(jar, directoriesOnly = false, extensions = null)?.getValue("type"))
            assertNull(ToolingApiSupport.safeFileEntry(jar, directoriesOnly = true, extensions = null))
            assertNull(ToolingApiSupport.safeFileEntry(other, directoriesOnly = false, extensions = listOf("jar")))
            assertEquals(3, ToolingApiSupport.listDirectoryEntries(root, directoriesOnly = false, extensions = listOf("jar", "txt")).size)
            assertEquals(1, ToolingApiSupport.listDirectoryEntries(root, directoriesOnly = true, extensions = null).size)
            assertEquals(emptyList<Map<String, Any>>(), ToolingApiSupport.listDirectoryEntries(File(root, "missing"), directoriesOnly = false, extensions = null))
        } finally {
            root.deleteRecursively()
        }
    }
}
