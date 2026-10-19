package com.stacklens.adapter.out.textfilter

import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import java.io.File
import java.nio.file.Files

class TomlFilterAdapterDirectoryCoverageTest {
    private val resourceLoader = mock<ResourceLoader>()
    private val dir: File = Files.createTempDirectory("stacklens-toml-filter").toFile()

    private fun betaSpec(name: String) = "[filters.$name]\ndescription = \"beta filter\"\nmatch_command = \"^beta run.*\"\nfilter_stderr = true\n"

    @AfterEach
    fun clearFilterDirectoryProperty() {
        dir.setReadable(true)
        System.clearProperty("stacklens.ai.filter.directory")
    }

    private fun writeFilter(
        name: String,
        body: String,
    ) {
        File(dir, "$name.toml").writeText(body)
    }

    private fun adapter(config: TextFilterConfigPort? = null) = TomlFilterAdapter(resourceLoader, config)

    @Test
    fun `a filter directory without toml files falls back to the bundled classpath`() {
        System.setProperty("stacklens.ai.filter.directory", dir.path)
        val missing = missingResource()
        whenever(resourceLoader.getResource(any<String>())).thenReturn(missing)

        val adapter = adapter()

        assertEquals(emptyList<String>(), adapter.knownFilters())
        assertTrue(adapter.enabled)
    }

    @Test
    fun `an unreadable filter directory falls back to the bundled classpath`() {
        writeFilter("readable", "[filters.readable]\nmatch_command = \"^readable\"\n")
        dir.setReadable(false)
        System.setProperty("stacklens.ai.filter.directory", dir.path)

        val adapter = adapter()

        dir.setReadable(true)
        assertEquals(emptyList<String>(), adapter.knownFilters())
    }

    @Test
    fun `specs from the filter directory are loaded instead of the bundle`() {
        writeFilter("beta-one", betaSpec("beta-one"))
        writeFilter("beta-two", betaSpec("beta-two"))
        writeFilter("gamma", "[filters.gamma]\ndescription = \"gamma filter\"\nmatch_command = \"^gamma\"\n")
        writeFilter("alpha", "[filters.alpha]\ndescription = \"no command matcher\"\n")
        File(dir, "notes.txt").writeText("ignored")
        System.setProperty("stacklens.ai.filter.directory", dir.path)

        val adapter = adapter()

        assertEquals(listOf("alpha", "beta-one", "beta-two", "gamma"), adapter.knownFilters())
        assertEquals("^beta run.*", adapter.findBestSpec("beta run --all")?.matchCommand?.pattern)
        assertNull(adapter.findBestSpec("alpha run"))
    }

    @Test
    fun `a spec that filters stderr prefers the error output and falls back to stdout`() {
        writeFilter("beta-one", betaSpec("beta-one"))
        writeFilter("beta-two", betaSpec("beta-two"))
        writeFilter("gamma", "[filters.gamma]\ndescription = \"gamma filter\"\nmatch_command = \"^gamma\"\n")
        System.setProperty("stacklens.ai.filter.directory", dir.path)
        val adapter = adapter()

        val withStderr = adapter.filter(listOf("beta", "run", "--all"), "out text", "err text")
        val withoutStderr = adapter.filter(listOf("beta", "run", "--all"), "out text", "")
        val stdoutOnly = adapter.filter(listOf("gamma", "run"), "out text", "err text")
        val unmatched = adapter.filter(listOf("nomatch"), "plain")

        assertEquals("err text", withStderr.text)
        assertEquals("out text", withoutStderr.text)
        assertEquals("out text", stdoutOnly.text)
        assertEquals("<none>", unmatched.specName)
    }

    @Test
    fun `the port configuration wins over the injected property`() {
        val config = mock<TextFilterConfigPort>()
        whenever(config.enabled).thenReturn(false)

        assertFalse(adapter(config).enabled)
    }

    @Test
    fun `a disabled adapter echoes the output unchanged`() {
        writeFilter("beta", betaSpec("beta"))
        System.setProperty("stacklens.ai.filter.directory", dir.path)
        val config = mock<TextFilterConfigPort>()
        whenever(config.enabled).thenReturn(false)

        val result = adapter(config).filter(listOf("beta", "run", "--all"), "kept")

        assertEquals("kept", result.text)
        assertEquals("<disabled>", result.specName)
    }

    private fun missingResource(): Resource =
        mock<Resource>().apply {
            whenever(exists()).thenReturn(false)
        }
}
