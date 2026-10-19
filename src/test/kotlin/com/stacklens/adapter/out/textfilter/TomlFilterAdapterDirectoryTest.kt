package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader
import java.io.File
import java.nio.file.Path

class TomlFilterAdapterDirectoryTest {
    private val property = "stacklens.ai.filter.directory"

    @AfterEach
    fun clearProperty() {
        System.clearProperty(property)
    }

    private fun adapter() = TomlFilterAdapter(DefaultResourceLoader(), config = null)

    @Test
    fun `a directory with toml files is used instead of the bundled ones`(
        @TempDir dir: Path,
    ) {
        dir
            .resolve("custom.toml")
            .toFile()
            .writeText(
                """
                [filters.custom-filter]
                description = "Compact custom output"
                match_command = "^custom\\b"
                strip_ansi = true
                max_lines = 10
                """.trimIndent(),
            )
        dir.resolve("ignored.txt").toFile().writeText("nothing")
        System.setProperty(property, dir.toString())

        val filters = adapter().knownFilters()

        assertTrue(filters.contains("custom-filter"))
        assertTrue(!filters.contains("ansible-playbook"))
    }

    @Test
    fun `a directory without toml files falls back to the bundled resources`(
        @TempDir dir: Path,
    ) {
        dir.resolve("notes.md").toFile().writeText("no filter here")
        System.setProperty(property, dir.toString())

        assertTrue(adapter().knownFilters().isNotEmpty())
    }

    @Test
    fun `a plain file as filter directory falls back to the bundled resources`() {
        val file = File.createTempFile("stacklens-not-a-dir", ".toml")
        System.setProperty(property, file.absolutePath)

        assertTrue(adapter().knownFilters().isNotEmpty())
        file.delete()
    }

    @Test
    fun `no directory property reads the bundled resources`() {
        assertTrue(adapter().knownFilters().contains("ansible-playbook"))
    }
}
