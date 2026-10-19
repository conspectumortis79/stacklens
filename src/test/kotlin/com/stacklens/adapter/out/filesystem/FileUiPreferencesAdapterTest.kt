package com.stacklens.adapter.out.filesystem

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class FileUiPreferencesAdapterTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var mapper: ObjectMapper
    private lateinit var adapter: FileUiPreferencesAdapter

    @BeforeEach
    fun setUp() {
        mapper = ObjectMapper()
        adapter = FileUiPreferencesAdapter(mapper, tempDir.toString())
    }

    @AfterEach
    fun cleanRelativePreferenceFile() {
        File("ui-prefs-file-adapter-test.json").delete()
    }

    @Test
    fun `getAll returns empty map for missing or non-file preferences`() {
        assertEquals(emptyMap<String, String>(), adapter.getAll())

        File(tempDir.toFile(), FileUiPreferencesAdapter.FILE_NAME).mkdirs()
        assertEquals(emptyMap<String, String>(), adapter.getAll())
    }

    @Test
    fun `getAll normalizes string and boolean values`() {
        val file = File(tempDir.toFile(), FileUiPreferencesAdapter.FILE_NAME)
        file.writeText("{\"theme\":\"dark\",\"text_filter_enabled\":true,\"count\":7}")

        assertEquals(
            mapOf("theme" to "dark", "text_filter_enabled" to "true", "count" to "7"),
            adapter.getAll(),
        )
    }

    @Test
    fun `getAll returns empty map for malformed or non-object JSON`() {
        val file = File(tempDir.toFile(), FileUiPreferencesAdapter.FILE_NAME)
        file.writeText("not-json")
        assertEquals(emptyMap<String, String>(), adapter.getAll())

        file.writeText("[1, 2]")
        assertEquals(emptyMap<String, String>(), adapter.getAll())
    }

    @Test
    fun `replaceAll creates the directory and round-trips preferences`() {
        val nestedDir = File(tempDir.toFile(), "nested")
        val nestedAdapter = FileUiPreferencesAdapter(mapper, nestedDir.absolutePath)

        nestedAdapter.replaceAll(mapOf("theme" to "popos", "dev_show_token_usage" to "false"))

        assertTrue(File(nestedDir, FileUiPreferencesAdapter.FILE_NAME).isFile)
        assertEquals(
            mapOf("theme" to "popos", "dev_show_token_usage" to "false"),
            nestedAdapter.getAll(),
        )
    }

    @Test
    fun `replaceAll swallows file errors`() {
        val blocker = File(tempDir.toFile(), "blocker")
        blocker.writeText("not a directory")
        val failing = FileUiPreferencesAdapter(mapper, File(blocker, "prefs").absolutePath)

        failing.replaceAll(mapOf("theme" to "dark"))
        assertEquals(emptyMap<String, String>(), failing.getAll())
    }

    @Test
    fun `replaceAll supports a preference file without a parent`() {
        val relative =
            object : FileUiPreferencesAdapter(mapper, "unused") {
                override fun uiPrefsFile(): File = File("ui-prefs-file-adapter-test.json")
            }

        relative.replaceAll(mapOf("theme" to "legacy"))

        assertEquals(mapOf("theme" to "legacy"), relative.getAll())
    }
}
