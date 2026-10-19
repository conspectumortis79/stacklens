package com.stacklens.adapter.out.textfilter

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class TextFilterConfigAdapterCoverageTest {
    private val mapper = ObjectMapper()

    private fun adapterIn(dir: Path): TextFilterConfigAdapter = TextFilterConfigAdapter(mapper, dir.toString())

    private fun prefsFile(dir: Path): File = File(dir.toFile(), "ui-prefs.json")

    @Test
    fun `the configured default applies while no state file exists`(
        @TempDir dir: Path,
    ) {
        val adapter = adapterIn(dir)

        assertTrue(adapter.enabled)
        assertFalse(prefsFile(dir).exists())
    }

    @Test
    fun `a stored boolean flag wins over the configured default`(
        @TempDir dir: Path,
    ) {
        prefsFile(dir).writeText("""{"text_filter_enabled":false}""")

        assertFalse(adapterIn(dir).enabled)
    }

    @Test
    fun `switching the flag persists an object node and reloads on the next start`(
        @TempDir dir: Path,
    ) {
        adapterIn(dir).setEnabled(false)

        assertEquals(false, mapper.readTree(prefsFile(dir)).get("text_filter_enabled").booleanValue())
        assertFalse(adapterIn(dir).enabled)
    }

    @Test
    fun `an unparseable state file is rewritten instead of failing`(
        @TempDir dir: Path,
    ) {
        prefsFile(dir).writeText("{ not json")
        val adapter = TextFilterConfigAdapter(mapper, dir.toString(), defaultEnabled = false)

        assertFalse(adapter.enabled)

        adapter.setEnabled(true)

        assertTrue(adapter.enabled)
        assertTrue(mapper.readTree(prefsFile(dir)).get("text_filter_enabled").booleanValue())
    }

    @Test
    fun `a state file holding an array is replaced by a fresh object node`(
        @TempDir dir: Path,
    ) {
        prefsFile(dir).writeText("""["unexpected"]""")
        val adapter = adapterIn(dir)

        adapter.setEnabled(false)

        assertTrue(prefsFile(dir).readText().startsWith("{"), prefsFile(dir).readText())
        assertEquals(false, mapper.readTree(prefsFile(dir)).get("text_filter_enabled").booleanValue())
    }

    @Test
    fun `a textual flag is parsed case insensitively`(
        @TempDir dir: Path,
    ) {
        prefsFile(dir).writeText("""{"text_filter_enabled":"TRUE"}""")

        assertTrue(adapterIn(dir).enabled)
    }

    @Test
    fun `an unrelated node type leaves the configured default untouched`(
        @TempDir dir: Path,
    ) {
        prefsFile(dir).writeText("""{"text_filter_enabled":7}""")

        assertTrue(adapterIn(dir).enabled)
    }

    @Test
    fun `a missing flag falls back to the configured default`(
        @TempDir dir: Path,
    ) {
        prefsFile(dir).writeText("""{"other":"value"}""")

        assertTrue(adapterIn(dir).enabled)
    }
}
