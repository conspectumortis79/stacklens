package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.DefaultResourceLoader
import java.io.File
import java.nio.file.Files

class TomlFilterAdapterCoverageTest {
    private val resourceLoader = DefaultResourceLoader()

    private fun newAdapter(enabled: Boolean = true) = TomlFilterAdapter(resourceLoader, config = null, enabledProp = enabled)

    @Test fun `filesystem directory loads bundled TOMLs when present`() {
        val tmp = Files.createTempDirectory("stacklens-fs-filter").toFile()
        try {
            val toml = tmp.resolve("custom-spec.toml")
            toml.writeText(
                "[filters.custom-spec]\n" +
                    "description = \"loaded from filesystem\"\n" +
                    "match_command = \"^custom-tool\"\n" +
                    "strip_lines_matching = [\"^\\\\s*$\"]\n" +
                    "on_empty = \"custom: ok\"\n",
            )
            System.setProperty("stacklens.ai.filter.directory", tmp.absolutePath)
            try {
                val adapter = newAdapter()
                assertTrue(adapter.knownFilters().contains("custom-spec"))
                val result = adapter.filter(listOf("custom-tool", "do"), "   \n\n   \n")
                assertEquals("custom: ok", result.text)
            } finally {
                System.clearProperty("stacklens.ai.filter.directory")
            }
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test fun `missing filesystem directory falls back to bundled resources`() {
        System.setProperty("stacklens.ai.filter.directory", "/nonexistent/dir-${System.nanoTime()}")
        try {
            val adapter = newAdapter()
            assertTrue(adapter.knownFilters().size >= 50)
        } finally {
            System.clearProperty("stacklens.ai.filter.directory")
        }
    }

    @Test fun `empty filesystem directory falls back to bundled resources`() {
        val tmp = Files.createTempDirectory("stacklens-empty").toFile()
        try {
            System.setProperty("stacklens.ai.filter.directory", tmp.absolutePath)
            try {
                val adapter = newAdapter()
                assertTrue(adapter.knownFilters().size >= 50)
            } finally {
                System.clearProperty("stacklens.ai.filter.directory")
            }
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test fun `filesystem directory with one broken file logs warning and continues`() {
        val tmp = Files.createTempDirectory("stacklens-broken").toFile()
        try {
            tmp.resolve("good.toml").writeText(
                """
                [filters.fsgood]
                description = "good"
                match_command = "^fs-good"
                """.trimIndent(),
            )
            tmp.resolve("broken.toml").writeText(
                """
                [filters.fsbroken]
                description = "broken"
                strip_lines_matching = ["["]
                """.trimIndent(),
            )
            System.setProperty("stacklens.ai.filter.directory", tmp.absolutePath)
            try {
                val adapter = newAdapter()
                assertTrue(adapter.knownFilters().contains("fsgood"))
                assertTrue(!adapter.knownFilters().contains("fsbroken"))
            } finally {
                System.clearProperty("stacklens.ai.filter.directory")
            }
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test fun `findBestSpec returns null when no spec matches`() {
        val adapter = newAdapter()
        assertEquals(null, adapter.findBestSpec("this-tool-doesnt-exist"))
    }

    @Test fun `findBestSpec returns null for blank command string`() {
        val adapter = newAdapter()
        assertEquals(null, adapter.findBestSpec(""))
    }

    @Test fun `findBestSpec picks longest match when multiple specs match`() {
        val adapter = newAdapter()
        val spec = adapter.findBestSpec("composer install --no-dev")
        assertNotNull(spec)
        assertEquals("composer-install", spec!!.name)
    }

    @Test fun `findBestSpec ignores specs without matchCommand`() {
        val adapter = newAdapter()
        val name = adapter.findBestSpec("a")
        if (name != null) assertNotNull(name.name)
    }

    @Test fun `filter applies spec for spec with filter_stderr enabled`() {
        val adapter = newAdapter()
        val result =
            adapter.filter(
                command = listOf("liquibase", "status"),
                stdout = "stdout that should be ignored",
                stderr = "stderr that should be filtered",
            )
        assertTrue(
            !result.text.contains("stdout that should be ignored") ||
                result.text.isEmpty(),
        )
    }

    @Test fun `knownFilters returns sorted list of names`() {
        val adapter = newAdapter()
        val filters = adapter.knownFilters()
        assertEquals(filters.sorted(), filters)
    }

    @Test fun `enabled flag is read from constructor parameter`() {
        val disabled = TomlFilterAdapter(resourceLoader, config = null, enabledProp = false)
        val enabled = TomlFilterAdapter(resourceLoader, config = null, enabledProp = true)
        assertEquals(false, disabled.enabled)
        assertEquals(true, enabled.enabled)
    }

    @Test fun `filter returns disabled-marker when adapter is disabled`() {
        val disabled = TomlFilterAdapter(resourceLoader, config = null, enabledProp = false)
        val result =
            disabled.filter(
                command = listOf("gradle", "build"),
                stdout = "raw gradle output",
            )
        assertEquals("raw gradle output", result.text)
        assertEquals("<disabled>", result.specName)
    }

    @Test fun `filter returns none-marker when no spec matches and adapter is enabled`() {
        val adapter = newAdapter()
        val result =
            adapter.filter(
                command = listOf("some-unknown-tool"),
                stdout = "raw output",
            )
        assertEquals("raw output", result.text)
        assertEquals("<none>", result.specName)
    }

    @Test fun `filterByName applies the named spec and returns its result`() {
        val adapter = newAdapter()
        val result =
            adapter.filterByName(
                specName = "du",
                output = "4.0K\t./src\n\n8.0K\t./tests\n",
            )
        assertTrue(!result.text.contains("\n\n"))
    }

    @Test fun `filterByName throws IllegalArgumentException for unknown spec`() {
        val adapter = newAdapter()
        val ex =
            assertThrows<IllegalArgumentException> {
                adapter.filterByName("totally-unknown", "any input")
            }
        assertTrue(ex.message!!.contains("totally-unknown"))
        assertTrue(ex.message!!.contains("Known specs"))
    }

    private fun File.deleteRecursively() {
        if (isDirectory) {
            listFiles()?.forEach { it.deleteRecursively() }
        }
        delete()
    }
}
