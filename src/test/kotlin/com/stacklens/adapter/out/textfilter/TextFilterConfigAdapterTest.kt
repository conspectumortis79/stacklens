package com.stacklens.adapter.out.textfilter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class TextFilterConfigAdapterTest {
    private lateinit var mapper: ObjectMapper
    private lateinit var dataDir: File

    @BeforeEach
    fun setup(
        @TempDir tmp: Path,
    ) {
        mapper = ObjectMapper()
        dataDir = tmp.toFile()
    }

    @AfterEach
    fun cleanup() {
    }

    private fun newAdapter(
        defaultEnabled: Boolean = true,
        existing: Map<String, Any?>? = null,
    ): TextFilterConfigAdapter {
        existing?.let {
            val f = File(dataDir, TextFilterConfigAdapter.FILE_NAME)
            f.parentFile?.mkdirs()
            mapper.writeValue(f, it)
        }
        return TextFilterConfigAdapter(
            objectMapper = mapper,
            dataDir = dataDir.absolutePath,
            defaultEnabled = defaultEnabled,
        )
    }

    @Test fun `defaults to true when no ui-prefs file exists`() {
        val adapter = newAdapter(defaultEnabled = true)
        assertTrue(adapter.enabled)
    }

    @Test fun `defaults to false when defaultEnabled is false and no ui-prefs file exists`() {
        val adapter = newAdapter(defaultEnabled = false)
        assertFalse(adapter.enabled)
    }

    @Test fun `loads boolean true from ui-prefs at construction`() {
        val adapter =
            newAdapter(
                defaultEnabled = false,
                existing = mapOf("text_filter_enabled" to true),
            )
        assertTrue(adapter.enabled)
    }

    @Test fun `loads boolean false from ui-prefs at construction`() {
        val adapter =
            newAdapter(
                defaultEnabled = true,
                existing = mapOf("text_filter_enabled" to false),
            )
        assertFalse(adapter.enabled)
    }

    @Test fun `loads string true from ui-prefs at construction`() {
        val adapter =
            newAdapter(
                defaultEnabled = false,
                existing = mapOf("text_filter_enabled" to "true"),
            )
        assertTrue(adapter.enabled)
    }

    @Test fun `loads case-insensitive TRUE string from ui-prefs at construction`() {
        val adapter =
            newAdapter(
                defaultEnabled = false,
                existing = mapOf("text_filter_enabled" to "TRUE"),
            )
        assertTrue(adapter.enabled)
    }

    @Test fun `ignores non-boolean non-true-string value in ui-prefs (falls back to default)`() {
        val adapter =
            newAdapter(
                defaultEnabled = true,
                existing = mapOf("text_filter_enabled" to 42),
            )
        assertTrue(adapter.enabled, "non-boolean 42 should fall back to defaultEnabled=true")
    }

    @Test fun `falls back to default when key is absent but file exists`() {
        val adapter =
            newAdapter(
                defaultEnabled = true,
                existing = mapOf("other_key" to "value"),
            )
        assertTrue(adapter.enabled)
    }

    @Test fun `setEnabled updates in-memory state immediately`() {
        val adapter = newAdapter(defaultEnabled = false)
        assertFalse(adapter.enabled)
        adapter.setEnabled(true)
        assertTrue(adapter.enabled)
        adapter.setEnabled(false)
        assertFalse(adapter.enabled)
    }

    @Test fun `setEnabled persists boolean to ui prefs file`() {
        val adapter = newAdapter(defaultEnabled = false)
        adapter.setEnabled(true)
        val node: JsonNode = mapper.readTree(File(dataDir, TextFilterConfigAdapter.FILE_NAME))
        assertTrue(node.get(TextFilterConfigAdapter.KEY).booleanValue())
    }

    @Test fun `setEnabled preserves other keys in ui prefs file`() {
        val adapter =
            newAdapter(
                defaultEnabled = false,
                existing = mapOf("dev_show_prompt_buttons" to "true", "other" to 7),
            )
        adapter.setEnabled(true)
        val node: JsonNode = mapper.readTree(File(dataDir, TextFilterConfigAdapter.FILE_NAME))
        assertTrue(node.get(TextFilterConfigAdapter.KEY).booleanValue())
        assertEquals("true", node.get("dev_show_prompt_buttons").asText())
        assertEquals(7, node.get("other").intValue())
    }

    @Test fun `setEnabled with same value is a no-op and does not rewrite the file`() {
        val adapter = newAdapter(defaultEnabled = true)
        val file = File(dataDir, TextFilterConfigAdapter.FILE_NAME)
        val sentinel = mapOf("sentinel" to "keep", TextFilterConfigAdapter.KEY to true)
        mapper.writeValue(file, sentinel)
        adapter.setEnabled(true)
        val node = mapper.readTree(file)
        assertEquals("keep", node.get("sentinel").asText())
        assertTrue(node.get(TextFilterConfigAdapter.KEY).booleanValue())
    }

    @Test fun `setEnabled from true to false updates both memory and disk`() {
        val adapter = newAdapter(defaultEnabled = true)
        adapter.setEnabled(false)
        assertFalse(adapter.enabled)
        val node = mapper.readTree(File(dataDir, TextFilterConfigAdapter.FILE_NAME))
        assertFalse(node.get(TextFilterConfigAdapter.KEY).booleanValue())
    }

    @Test fun `state survives simulated restart via fresh adapter on same dataDir`() {
        val first = newAdapter(defaultEnabled = true)
        first.setEnabled(false)
        assertFalse(first.enabled)

        val second = newAdapter(defaultEnabled = true)
        assertFalse(second.enabled, "second adapter should pick up the persisted false value")
    }

    @Test fun `corrupt ui-prefs file is ignored and defaults are used`() {
        File(dataDir, TextFilterConfigAdapter.FILE_NAME).writeText("{ not valid json")
        val adapter = newAdapter(defaultEnabled = true)
        assertTrue(adapter.enabled)
    }

    @Test fun `setEnabled succeeds even when parent dir cannot be created (logged, not thrown)`() {
        val blocker = File(dataDir, "blocker")
        blocker.writeText("x")
        val adapter =
            TextFilterConfigAdapter(
                objectMapper = mapper,
                dataDir = File(blocker, TextFilterConfigAdapter.FILE_NAME).absolutePath,
                defaultEnabled = false,
            )
        assertFalse(adapter.enabled)
        adapter.setEnabled(true)
        assertTrue(adapter.enabled, "in-memory state must update even if disk write fails")
    }

    @Test fun `non-existent ui-prefs file with defaultEnabled false works`() {
        val adapter = newAdapter(defaultEnabled = false)
        assertFalse(adapter.enabled)
    }

    @Test fun `setEnabled works when ui-prefs file has no parent directory`() {
        val adapter =
            object : TextFilterConfigAdapter(mapper, dataDir.absolutePath, false) {
                override fun uiPrefsFile(): java.io.File = java.io.File("ui-prefs-no-parent.json")
            }
        adapter.setEnabled(true)
        assertTrue(adapter.enabled)
        java.io.File("ui-prefs-no-parent.json").delete()
    }
}
