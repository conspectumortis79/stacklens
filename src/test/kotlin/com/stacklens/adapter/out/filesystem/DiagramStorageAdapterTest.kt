package com.stacklens.adapter.out.filesystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DiagramStorageAdapterTest {
    private val adapter = DiagramStorageAdapter()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `saveDiagram writes the file and returns the absolute path`() {
        val path = adapter.saveDiagram(tempDir.toString(), "diagram.mmd", "graph TD; A-->B")
        assertEquals(File(tempDir.toFile(), "diagram.mmd").absolutePath, path)
        assertEquals("graph TD; A-->B", File(path).readText())
    }

    @Test
    fun `saveDiagram sanitises unsafe filename characters`() {
        val path = adapter.saveDiagram(tempDir.toString(), "weird name?.mmd", "x")
        val f = File(path)
        assertTrue(f.name.contains("weird_name_"))
        assertTrue(!f.name.contains("?"))
    }

    @Test
    fun `saveDiagram throws when the directory does not exist`() {
        assertThrows<IllegalArgumentException> {
            adapter.saveDiagram("/this/does/not/exist-${System.nanoTime()}", "f", "x")
        }
    }

    @Test
    fun `saveDiagram throws when the path is not a directory`() {
        val f = tempDir.resolve("file.txt").toFile()
        f.writeText("x")
        assertThrows<IllegalArgumentException> {
            adapter.saveDiagram(f.absolutePath, "g", "x")
        }
    }
}
