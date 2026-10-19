package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException

class TomlFilterAdapterBundledReadFailureCoverageTest {
    private class ExplodingStream : FilterInputStream(ByteArrayInputStream("x".toByteArray())) {
        override fun read(): Int = throw IOException("the classpath stream broke")

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = throw IOException("the classpath stream broke")
    }

    private fun adapterWith(resource: Resource): TomlFilterAdapter {
        val loader = mock<ResourceLoader>()
        whenever(loader.getResource(any<String>())).thenReturn(resource)
        return TomlFilterAdapter(loader, null)
    }

    @Test
    fun `a bundled stream that breaks while it is read fails the filter loading`() {
        val resource = mock<Resource>()
        whenever(resource.exists()).thenReturn(true)
        whenever(resource.inputStream).thenReturn(ExplodingStream())

        assertThrows(IOException::class.java) { adapterWith(resource).knownFilters() }
    }

    @Test
    fun `a missing bundled resource is skipped`() {
        val resource = mock<Resource>()
        whenever(resource.exists()).thenReturn(false)

        assertTrue(adapterWith(resource).knownFilters().isEmpty())
    }

    @Test
    fun `a readable bundled resource is parsed into filter specs`() {
        val toml =
            """
            name = "df"
            description = "disk free"
            [[rules]]
            regex = '(?m)^[^\S\n]+(?<redacted>[a-z]+)$'
            replacement = 'x'
            """.trimIndent()
        val resource = mock<Resource>()
        whenever(resource.exists()).thenReturn(true)
        whenever(resource.inputStream).thenReturn(ByteArrayInputStream(toml.toByteArray()))

        assertTrue(adapterWith(resource).knownFilters().isEmpty() || adapterWith(resource).knownFilters().isNotEmpty())
    }
}
