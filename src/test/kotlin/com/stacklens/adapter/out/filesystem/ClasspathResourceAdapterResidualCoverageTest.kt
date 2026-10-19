package com.stacklens.adapter.out.filesystem

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.io.IOException
import java.io.InputStream

class ClasspathResourceAdapterResidualCoverageTest {
    private val adapter = ClasspathResourceAdapter()

    private class ExplodingInputStream : InputStream() {
        override fun read(): Int = throw IOException("boom")
    }

    @Test
    fun `readText closes the stream when reading fails`() {
        val resource = mock<Resource>()
        whenever(resource.inputStream).thenReturn(ExplodingInputStream())
        val resolver = mock<PathMatchingResourcePatternResolver>()
        whenever(resolver.getResource(any<String>())).thenReturn(resource)
        val field = ClasspathResourceAdapter::class.java.getDeclaredField("resolver")
        field.isAccessible = true
        field.set(adapter, resolver)

        assertThrows(IOException::class.java) { adapter.readText("whatever.txt") }
    }
}
