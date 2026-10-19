package com.stacklens.adapter.out.filesystem

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClasspathResourceAdapterEntriesTest {
    private val adapter = ClasspathResourceAdapter()

    @Test
    fun `bundled text filter definitions are readable through the adapter`() {
        val entries = adapter.listEntries("classpath*:textfilter/*.toml")

        assertTrue(entries.isNotEmpty())
        assertTrue(entries.all { it.filename.endsWith(".toml") })
        assertTrue(entries.first().content.isNotBlank())
    }

    @Test
    fun `a pattern without matches yields no entries`() {
        assertTrue(adapter.listEntries("classpath*:no-such-directory/*.nope").isEmpty())
    }

    @Test
    fun `a single classpath resource is returned as text`() {
        val text = adapter.readText("textfilter/ansible-playbook.toml")

        assertTrue(text.isNotBlank())
    }
}
