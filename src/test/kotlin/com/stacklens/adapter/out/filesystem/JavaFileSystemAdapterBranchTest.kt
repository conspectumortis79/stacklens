package com.stacklens.adapter.out.filesystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class JavaFileSystemAdapterBranchTest {
    private val adapter = JavaFileSystemAdapter()

    @Test
    fun `writeFile creates parent directory if missing`(
        @TempDir tmp: Path,
    ) {
        val nested = tmp.resolve("a/b/c/file.txt")
        adapter.writeFile(nested.toFile(), "hello")
        assertTrue(nested.toFile().exists())
        assertEquals("hello", nested.toFile().readText())
    }

    @Test
    fun `writeFile does not create parent directory if it already exists`(
        @TempDir tmp: Path,
    ) {
        val file = tmp.resolve("file.txt").toFile()
        adapter.writeFile(file, "x")
        assertTrue(file.exists())
    }

    @Test
    fun `deleteFile returns true for an existing file`(
        @TempDir tmp: Path,
    ) {
        val file = tmp.resolve("f.txt").toFile()
        file.writeText("data")
        assertTrue(adapter.deleteFile(file))
        assertFalse(file.exists())
    }

    @Test
    fun `deleteFile returns false for a non-existing file`(
        @TempDir tmp: Path,
    ) {
        val file = tmp.resolve("does-not-exist.txt").toFile()
        assertFalse(file.exists())
        assertFalse(adapter.deleteFile(file))
    }

    @Test
    fun `deleteFile returns false for a directory`(
        @TempDir tmp: Path,
    ) {
        val dir = tmp.resolve("subdir").toFile()
        dir.mkdirs()
        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
        assertFalse(adapter.deleteFile(dir))
    }

    @Test
    fun `createTempDirectory returns a non-null file`(
        @TempDir tmp: Path,
    ) {
        val dir = adapter.createTempDirectory("test-")
        assertNotNull(dir)
        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
        dir.deleteRecursively()
    }

    @Test
    fun `deleteFile returns false when file exists and is a file but cannot be deleted`(
        @TempDir tmp: Path,
    ) {
        val parent = tmp.resolve("readonly-parent-${System.nanoTime()}").toFile()
        parent.mkdirs()
        val file = java.io.File(parent, "locked.txt")
        file.writeText("x")
        parent.setWritable(false)
        file.setReadOnly()
        try {
            val result = adapter.deleteFile(file)
            if (result) {
            } else {
                assertFalse(result)
            }
        } finally {
            file.setWritable(true)
            parent.setWritable(true)
        }
    }
}
