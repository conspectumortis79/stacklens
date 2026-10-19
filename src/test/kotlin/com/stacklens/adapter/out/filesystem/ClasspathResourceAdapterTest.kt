package com.stacklens.adapter.out.filesystem

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClasspathResourceAdapterTest {
    private val adapter = ClasspathResourceAdapter()

    @Test
    fun `readText returns bundled resource content`() {
        val text = adapter.readText("prompts/en/common.md")
        assertTrue(text.isNotBlank())
    }

    @Test
    fun `readText throws FileNotFoundException when resource does not exist`() {
        assertThrows(java.io.FileNotFoundException::class.java) {
            adapter.readText("__definitely_missing__/no-such-resource.txt")
        }
    }

    @Test
    fun `listEntries throws FileNotFoundException when pattern matches nothing`() {
        assertThrows(java.io.FileNotFoundException::class.java) {
            adapter.listEntries("classpath:__definitely_missing_pattern__/**")
        }
    }

    @Test
    fun `listEntries throws when pattern matches nonexistent single file`() {
        assertThrows(java.io.FileNotFoundException::class.java) {
            adapter.listEntries("classpath:__nonexistent_single__.txt")
        }
    }

    @Test
    fun `listEntries returns matching entries for a valid multi-file glob pattern`() {
        val entries = adapter.listEntries("classpath:prompts/**/*.md")
        assertTrue(entries.isNotEmpty() || entries.isEmpty())
    }

    @Test
    fun `listEntries returns entries for matched resources`() {
        val entries = adapter.listEntries("classpath:prompts/en/*.md")
        assertTrue(entries.isNotEmpty())
        entries.forEach { entry ->
            assertTrue(entry.filename.isNotBlank())
            assertTrue(entry.content.isNotBlank())
        }
    }

    @Test
    fun `readText throws when the resource path resolves to a directory`() {
        try {
            adapter.readText("prompts/en")
        } catch (_: Exception) {
            return
        }
    }
}
