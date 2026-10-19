package com.stacklens.adapter.out.filesystem

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClasspathResourceAdapterReadCoverageTest {
    private val adapter = ClasspathResourceAdapter()

    @Test
    fun `a matching classpath entry is listed with its content`() {
        val entries = adapter.listEntries("classpath*:META-INF/services/org.apache.lucene.codecs.Codec")

        assertTrue(entries.isNotEmpty())
        assertTrue(entries.all { it.filename == "org.apache.lucene.codecs.Codec" })
        assertTrue(entries.first().content.isNotBlank())
    }

    @Test
    fun `a classpath resource is read as text`() {
        val text = adapter.readText("webview-noscroll.css")

        assertTrue(text.isNotBlank())
    }

    @Test
    fun `listing a pattern without matches returns no entries`() {
        assertTrue(adapter.listEntries("classpath*:no/such/folder/*.none").isEmpty())
    }
}
