package com.stacklens.adapter.out.filesystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.nio.charset.StandardCharsets

class ClasspathResourceAdapterCoverageTest {
    private val adapter = ClasspathResourceAdapter()

    private fun inject(vararg resources: Resource) {
        val resolver = mock<PathMatchingResourcePatternResolver>()
        whenever(resolver.getResources(any<String>())).thenReturn(resources)
        val field = ClasspathResourceAdapter::class.java.getDeclaredField("resolver")
        field.isAccessible = true
        field.set(adapter, resolver)
    }

    private class NamedResource(
        private val text: String,
        private val fileName: String?,
    ) : ByteArrayResource(text.toByteArray(StandardCharsets.UTF_8)) {
        override fun getFilename(): String? = fileName
    }

    @Test
    fun `listEntries pairs every named resource with its utf8 content`() {
        inject(NamedResource("first", "first.txt"))

        val entries = adapter.listEntries("classpath:prompts/*.txt")

        assertEquals(1, entries.size)
        assertEquals("first.txt", entries.first().filename)
        assertEquals("first", entries.first().content)
    }

    @Test
    fun `listEntries skips resources without a file name`() {
        inject(
            NamedResource("nameless", null),
            NamedResource("named", "named.txt"),
        )

        val entries = adapter.listEntries("classpath:prompts/*.txt")

        assertEquals(listOf("named.txt"), entries.map { it.filename })
    }

    @Test
    fun `readText loads a classpath resource as utf8`() {
        val text = adapter.readText("static/i18n/en.json")

        assertTrue(text.isNotBlank())
        assertTrue(text.contains("\""))
    }

    private class BrokenResource(
        private val failure: () -> Nothing,
    ) : ByteArrayResource("ignored".toByteArray(StandardCharsets.UTF_8)) {
        override fun getInputStream(): java.io.InputStream = failure()

        override fun getFilename(): String? = "broken.txt"
    }

    @Test
    fun `listEntries propagates a resource that cannot be opened`() {
        inject(BrokenResource { throw java.io.IOException("stream closed") })

        assertThrows(java.io.IOException::class.java) { adapter.listEntries("classpath:prompts/*.txt") }
    }

    @Test
    fun `readText propagates a resource that cannot be opened`() {
        val resolver = mock<PathMatchingResourcePatternResolver>()
        whenever(resolver.getResource(any<String>())).thenReturn(BrokenResource { throw java.io.FileNotFoundException("missing") })
        val field = ClasspathResourceAdapter::class.java.getDeclaredField("resolver")
        field.isAccessible = true
        field.set(adapter, resolver)

        assertThrows(java.io.FileNotFoundException::class.java) { adapter.readText("prompts/absent.txt") }
    }
}
