package com.stacklens.adapter.out.filesystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

class ClasspathResourceAdapterReadFailureCoverageTest {
    private class ExplodingStream : FilterInputStream(ByteArrayInputStream("x".toByteArray())) {
        override fun read(): Int = throw IOException("the classpath stream broke")

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = throw IOException("the classpath stream broke")
    }

    private fun resource(
        fileName: String?,
        stream: InputStream?,
    ): Resource =
        mock<Resource>().apply {
            whenever(filename).thenReturn(fileName)
            whenever(inputStream).thenReturn(stream)
        }

    private fun adapter(
        resources: List<Resource>,
        single: Resource,
    ): ClasspathResourceAdapter =
        ClasspathResourceAdapter(
            object : ResourcePatternResolver by PathMatchingResourcePatternResolver() {
                override fun getResources(pattern: String): Array<Resource> = resources.toTypedArray()

                override fun getResource(location: String): Resource = single
            },
        )

    @Test
    fun `entries without a file name are skipped and the rest is read as text`() {
        val adapter =
            adapter(
                listOf(
                    resource("filters.toml", ByteArrayInputStream("contents".toByteArray())),
                    resource(null, ByteArrayInputStream("ignored".toByteArray())),
                ),
                resource("x.toml", ByteArrayInputStream("contents".toByteArray())),
            )

        val entries = adapter.listEntries("classpath*:textfilter/*.toml")

        assertEquals(1, entries.size)
        assertEquals("filters.toml", entries.single().filename)
        assertEquals("contents", entries.single().content)
    }

    @Test
    fun `a listed resource that breaks while it is read surfaces the io failure`() {
        val adapter = adapter(listOf(resource("broken.toml", ExplodingStream())), resource("x.toml", ExplodingStream()))

        assertThrows(IOException::class.java) { adapter.listEntries("classpath*:any") }
    }

    @Test
    fun `a text resource that breaks while it is read surfaces the io failure`() {
        val adapter = adapter(emptyList(), resource("x.toml", ExplodingStream()))

        assertThrows(IOException::class.java) { adapter.readText("textfilter/df.toml") }
    }

    @Test
    fun `a resource without any stream fails both readers`() {
        val missing = resource("none.toml", null)
        val adapter = adapter(listOf(missing), missing)

        assertThrows(NullPointerException::class.java) { adapter.listEntries("classpath*:any") }
        assertThrows(NullPointerException::class.java) { adapter.readText("textfilter/df.toml") }
    }

    @Test
    fun `a real classpath file is read through the buffered reader block`() {
        assertTrue(ClasspathResourceAdapter().readText("textfilter/df.toml").isNotBlank())
    }
}
