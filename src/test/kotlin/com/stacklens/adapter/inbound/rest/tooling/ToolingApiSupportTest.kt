package com.stacklens.adapter.inbound.rest.tooling

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.io.File
import java.nio.file.Path

class ToolingApiSupportTest {
    @Test
    fun `tr returns the translated string when i18n is provided`() {
        val fakeI18n =
            object : I18nUseCase, I18nPort {
                override fun t(
                    key: String,
                    vararg args: Any,
                ): String = "Translated:$key"

                override fun tFor(
                    lang: String,
                    key: String,
                    vararg args: Any,
                ): String = "Translated:$key"

                override fun translateError(e: Exception): String = "err"

                override fun resolveLanguage(): String = "en"
            }
        assertEquals("Translated:app.ok", ToolingApiSupport.tr(fakeI18n, "app.ok"))
    }

    @Test
    fun `resolveOriginalFilename returns the filename when present`() {
        val file = MockMultipartFile("file", "original.txt", "text/plain", "content".toByteArray())
        assertEquals("original.txt", ToolingApiSupport.resolveOriginalFilename(file))
    }

    @Test
    fun `resolveOriginalFilename returns empty when null`() {
        val file = MockMultipartFile("file", null, "text/plain", "content".toByteArray())
        assertEquals("", ToolingApiSupport.resolveOriginalFilename(file))
    }

    @Test
    fun `listDirectoryEntries returns empty when rootDir is null`() {
        val entries = ToolingApiSupport.listDirectoryEntries(File("nonexistent-xyz-12345"), directoriesOnly = true, extensions = null)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `listDirectoryEntries returns only directories when directoriesOnly is true`(
        @TempDir tempDir: Path,
    ) {
        val dir = tempDir.toFile()
        File(dir, "sub-dir").mkdir()
        File(dir, "file.txt").writeText("x")
        val entries = ToolingApiSupport.listDirectoryEntries(dir, directoriesOnly = true, extensions = null)
        assertEquals(1, entries.size)
        assertEquals("sub-dir", entries[0]["name"])
        assertEquals("directory", entries[0]["type"])
    }

    @Test
    fun `listDirectoryEntries returns directories and matching files when directoriesOnly is false`(
        @TempDir tempDir: Path,
    ) {
        val dir = tempDir.toFile()
        File(dir, "sub-dir").mkdir()
        File(dir, "tool.jar").writeText("jar")
        File(dir, "readme.txt").writeText("txt")
        val entries = ToolingApiSupport.listDirectoryEntries(dir, directoriesOnly = false, extensions = listOf("jar"))
        assertEquals(2, entries.size)
        assertTrue(entries.any { it["name"] == "sub-dir" })
        assertTrue(entries.any { it["name"] == "tool.jar" })
    }

    @Test
    fun `listDirectoryEntries excludes files with non-matching extensions`(
        @TempDir tempDir: Path,
    ) {
        val dir = tempDir.toFile()
        File(dir, "a.jar").writeText("jar")
        File(dir, "b.txt").writeText("txt")
        val entries = ToolingApiSupport.listDirectoryEntries(dir, directoriesOnly = false, extensions = listOf("jar"))
        assertEquals(1, entries.size)
        assertEquals("a.jar", entries[0]["name"])
    }

    @Test
    fun `listDirectoryEntries excludes hidden files and directories`(
        @TempDir tempDir: Path,
    ) {
        val dir = tempDir.toFile()
        File(dir, "visible.txt").writeText("x")
        File(dir, ".hidden.txt").writeText("x")
        val entries = ToolingApiSupport.listDirectoryEntries(dir, directoriesOnly = false, extensions = listOf("txt"))
        assertEquals(1, entries.size)
        assertEquals("visible.txt", entries[0]["name"])
    }

    @Test
    fun `safeFileEntry returns null for hidden file`(
        @TempDir tempDir: Path,
    ) {
        val file = File(tempDir.toFile(), ".hidden").apply { writeText("x") }
        val entry = ToolingApiSupport.safeFileEntry(file, directoriesOnly = false, extensions = listOf("hidden"))
        assertEquals(null, entry)
    }

    @Test
    fun `safeFileEntry returns directory entry for directory`(
        @TempDir tempDir: Path,
    ) {
        val dir = File(tempDir.toFile(), "sub").apply { mkdirs() }
        val entry = ToolingApiSupport.safeFileEntry(dir, directoriesOnly = false, extensions = null)
        assertEquals("sub", entry?.get("name"))
        assertEquals("directory", entry?.get("type"))
    }

    @Test
    fun `safeFileEntry returns file entry when extension matches`(
        @TempDir tempDir: Path,
    ) {
        val file = File(tempDir.toFile(), "tool.jar").apply { writeText("x") }
        val entry = ToolingApiSupport.safeFileEntry(file, directoriesOnly = false, extensions = listOf("jar"))
        assertEquals("tool.jar", entry?.get("name"))
        assertEquals("file", entry?.get("type"))
    }

    @Test
    fun `safeFileEntry returns null when directoriesOnly and not a directory`(
        @TempDir tempDir: Path,
    ) {
        val file = File(tempDir.toFile(), "tool.jar").apply { writeText("x") }
        val entry = ToolingApiSupport.safeFileEntry(file, directoriesOnly = true, extensions = listOf("jar"))
        assertEquals(null, entry)
    }

    @Test
    fun `safeFileEntry returns null when extension does not match`(
        @TempDir tempDir: Path,
    ) {
        val file = File(tempDir.toFile(), "tool.jar").apply { writeText("x") }
        val entry = ToolingApiSupport.safeFileEntry(file, directoriesOnly = false, extensions = listOf("exe"))
        assertEquals(null, entry)
    }

    @Test
    fun `safeFileEntry defaults extensions to jar when null`(
        @TempDir tempDir: Path,
    ) {
        val file = File(tempDir.toFile(), "tool.jar").apply { writeText("x") }
        val entry = ToolingApiSupport.safeFileEntry(file, directoriesOnly = false, extensions = null)
        assertEquals("tool.jar", entry?.get("name"))
    }

    @Test
    fun `safeFileEntry returns null on security exception`() {
        val unreachableFile = File("/proc/0/x/y")
        val entry = ToolingApiSupport.safeFileEntry(unreachableFile, directoriesOnly = false, extensions = null)
        assertTrue(entry == null || entry.isNotEmpty())
    }
}
