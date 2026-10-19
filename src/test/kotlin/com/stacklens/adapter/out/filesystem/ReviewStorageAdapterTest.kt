package com.stacklens.adapter.out.filesystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ReviewStorageAdapterTest {
    private fun adapterWithBaseDir(baseDir: String): ReviewStorageAdapter {
        val adapter = ReviewStorageAdapter()
        val field = ReviewStorageAdapter::class.java.getDeclaredField("baseDir")
        field.isAccessible = true
        field.set(adapter, baseDir)
        return adapter
    }

    @Test
    fun `readPromptOverride returns null when file does not exist`() {
        val adapter = ReviewStorageAdapter()
        assertNull(adapter.readPromptOverride("nonexistent-lang"))
    }

    @Test
    fun `savePromptOverride and readPromptOverride roundtrip`(
        @TempDir tempDir: Path,
    ) {
        val adapter = adapterWithBaseDir(tempDir.resolve("review").toString())
        adapter.savePromptOverride("en", "Custom prompt text")
        val result = adapter.readPromptOverride("en")
        assertEquals("Custom prompt text", result)
    }

    @Test
    fun `deletePromptOverride removes file`(
        @TempDir tempDir: Path,
    ) {
        val adapter = adapterWithBaseDir(tempDir.resolve("review").toString())
        adapter.savePromptOverride("de", "prompt")
        adapter.deletePromptOverride("de")
        assertNull(adapter.readPromptOverride("de"))
    }
}
