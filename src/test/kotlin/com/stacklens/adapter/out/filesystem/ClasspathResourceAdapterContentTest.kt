package com.stacklens.adapter.out.filesystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClasspathResourceAdapterContentTest {
    private val adapter = ClasspathResourceAdapter()

    @Test
    fun `listEntries reads every resource matching the pattern`() {
        val entries = adapter.listEntries("classpath:textfilter/ansible-playbook.toml")

        assertEquals(1, entries.size)
        assertEquals("ansible-playbook.toml", entries.single().filename)
        assertTrue(entries.single().content.isNotBlank())
    }

    @Test
    fun `listEntries yields nothing for a pattern without matches`() {
        assertEquals(emptyList<Any>(), adapter.listEntries("classpath:textfilter/no-such-*.toml"))
    }

    @Test
    fun `readText returns the full resource body`() {
        val text = adapter.readText("loading.html")

        assertTrue(text.isNotBlank())
        assertEquals(text, adapter.readText("loading.html"))
    }
}
