package com.stacklens.adapter.out.filesystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class JavaFileSystemAdapterTest {
    private lateinit var adapter: JavaFileSystemAdapter

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        adapter = JavaFileSystemAdapter()
    }

    @Test
    fun `writeFile creates parent directories if needed and writes content`() {
        val target = tempDir.resolve("nested/dir/file.txt").toFile()
        assertFalse(target.parentFile!!.exists())
        adapter.writeFile(target, "hello world")
        assertTrue(target.exists())
        assertEquals("hello world", target.readText(Charsets.UTF_8))
    }

    @Test
    fun `writeFile overwrites an existing file`() {
        val target = tempDir.resolve("file.txt").toFile()
        target.writeText("old")
        adapter.writeFile(target, "new")
        assertEquals("new", target.readText(Charsets.UTF_8))
    }

    @Test
    fun `writeBytes creates parent directories and writes binary content`() {
        val target = tempDir.resolve("nested/bin/data.bin").toFile()
        adapter.writeBytes(target, byteArrayOf(1, 2, 3))
        assertTrue(target.exists())
        assertEquals(listOf<Byte>(1, 2, 3), target.readBytes().toList())
    }

    @Test
    fun `homeDirectory points at the user home`() {
        assertEquals(File(System.getProperty("user.home", "")).absolutePath, adapter.homeDirectory().absolutePath)
    }

    @Test
    fun `listEntries lists the directory children`() {
        val dir = File(tempDir.toFile(), "entries").apply { mkdirs() }
        File(dir, "a.txt").writeText("a")
        File(dir, "b.txt").writeText("b")
        assertEquals(setOf("a.txt", "b.txt"), adapter.listEntries(dir).map { it.name }.toSet())
    }

    @Test
    fun `listEntries returns an empty list for an unreadable directory`() {
        assertTrue(adapter.listEntries(File(tempDir.toFile(), "missing")).isEmpty())
    }

    @Test
    fun `writeFile throws when path is a directory`() {
        val dir = tempDir.resolve("isDir").toFile()
        dir.mkdirs()
        assertThrows<Exception> {
            adapter.writeFile(dir, "content")
        }
    }

    @Test
    fun `deleteFile returns true for a regular file that exists`() {
        val target = tempDir.resolve("file.txt").toFile()
        target.writeText("x")
        assertTrue(adapter.deleteFile(target))
        assertFalse(target.exists())
    }

    @Test
    fun `deleteFile returns false for a non-existing file`() {
        val target = tempDir.resolve("nope.txt").toFile()
        assertFalse(adapter.deleteFile(target))
    }

    @Test
    fun `deleteFile returns false for a directory`() {
        val dir = tempDir.resolve("dir").toFile()
        dir.mkdirs()
        assertFalse(adapter.deleteFile(dir))
        assertTrue(dir.exists())
    }

    @Test
    fun `createTempDirectory creates a directory with the given prefix`() {
        val tmp = adapter.createTempDirectory("stacklens-test-")
        try {
            assertNotNull(tmp)
            assertTrue(tmp.exists())
            assertTrue(tmp.isDirectory)
            assertTrue(tmp.name.startsWith("stacklens-test-"))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `createTempDirectory returns a usable directory that is empty by default`() {
        val tmp = adapter.createTempDirectory("agent-test-")
        try {
            assertTrue(tmp.exists())
            val entries = tmp.listFiles()
            assertNotNull(entries)
            assertEquals(0, entries!!.size)
        } finally {
            tmp.delete()
        }
    }
}
