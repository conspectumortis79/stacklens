package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import java.io.File
import java.nio.file.Files

class TomlFilterAdapterDirectoryResidualCoverageTest {
    private val resourceLoader = mock<ResourceLoader>()
    private val dir: File = Files.createTempDirectory("stacklens-toml-residual").toFile()

    @AfterEach
    fun cleanup() {
        System.clearProperty("stacklens.ai.filter.directory")
        dir.deleteRecursively()
    }

    private fun missingResource(): Resource {
        val resource = mock<Resource>()
        whenever(resource.exists()).thenReturn(false)
        return resource
    }

    @Test
    fun `non toml files in the filter directory are ignored`() {
        System.setProperty("stacklens.ai.filter.directory", dir.path)
        val missing = missingResource()
        whenever(resourceLoader.getResource(any<String>())).thenReturn(missing)
        File(dir, "alpha.toml").writeText(
            "[filters.alpha]\ndescription = \"alpha filter\"\nmatch_command = \"^alpha.*\"\n",
        )
        File(dir, "notes.txt").writeText("not a filter")
        File(dir, "beta.toml.bak").writeText("backup")

        val adapter = TomlFilterAdapter(resourceLoader, null)

        assertEquals(listOf("alpha"), adapter.knownFilters())
        assertTrue(File(dir, "notes.txt").exists())
    }
}
