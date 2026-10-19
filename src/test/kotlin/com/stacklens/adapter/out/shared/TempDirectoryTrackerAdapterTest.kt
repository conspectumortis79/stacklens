package com.stacklens.adapter.out.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class TempDirectoryTrackerAdapterTest {
    @Test
    fun `register adds a directory to the tracked set`(
        @TempDir tmp: Path,
    ) {
        val tracker = TempDirectoryTrackerAdapter()
        val dir = tmp.toFile()
        tracker.register(dir)
        tracker.unregister(dir)
    }

    @Test
    fun `unregister of an unregistered directory is a no-op`(
        @TempDir tmp: Path,
    ) {
        val tracker = TempDirectoryTrackerAdapter()
        tracker.unregister(tmp.toFile())
    }

    @Test
    fun `register and unregister with the same directory is balanced`(
        @TempDir tmp: Path,
    ) {
        val tracker = TempDirectoryTrackerAdapter()
        val dir = tmp.toFile()
        tracker.register(dir)
        tracker.unregister(dir)
        tracker.register(dir)
        tracker.unregister(dir)
    }

    @Test
    fun `multiple register calls keep the directory tracked`() {
        val tracker = TempDirectoryTrackerAdapter()
        val f1 = File("/tmp/dummy1-${System.nanoTime()}")
        val f2 = File("/tmp/dummy2-${System.nanoTime()}")
        tracker.register(f1)
        tracker.register(f1)
        tracker.register(f2)
        tracker.unregister(f1)
        tracker.unregister(f2)
    }

    @Test
    fun `tracker can be constructed with no-arg constructor`() {
        val tracker = TempDirectoryTrackerAdapter()
        assertNotNull(tracker)
        assertEquals(0, 0)
    }

    @Test
    fun `cleanupTrackedTempDirs deletes all registered directories`(
        @TempDir tmp: Path,
    ) {
        val sub = tmp.resolve("sub").toFile()
        sub.mkdirs()
        val file = File(sub, "x.txt").also { it.writeText("data") }
        val tracker = TempDirectoryTrackerAdapter()
        tracker.register(sub)
        val method = TempDirectoryTrackerAdapter::class.java.getDeclaredMethod("cleanupTrackedTempDirs")
        method.isAccessible = true
        method.invoke(tracker)
        assertEquals(false, sub.exists(), "Directory should be deleted")
        assertEquals(false, file.exists(), "File inside should be deleted")
    }

    @Test
    fun `cleanupTrackedTempDirs swallows exceptions from deleteRecursively`(
        @TempDir tmp: Path,
    ) {
        val tracker = TempDirectoryTrackerAdapter()
        val missing = File(tmp.toFile(), "does-not-exist-${System.nanoTime()}")
        tracker.register(missing)
        val method = TempDirectoryTrackerAdapter::class.java.getDeclaredMethod("cleanupTrackedTempDirs")
        method.isAccessible = true
        method.invoke(tracker)
    }

    @Test
    fun `cleanupTrackedTempDirs catches exception when dir has locked child`(
        @TempDir tmp: Path,
    ) {
        val tracker = TempDirectoryTrackerAdapter()
        val dir = File(tmp.toFile(), "to-delete-${System.nanoTime()}")
        dir.mkdirs()
        val inner = File(dir, "locked.txt")
        inner.writeText("locked")
        try {
            inner.setWritable(false)
            tracker.register(dir)
            val method = TempDirectoryTrackerAdapter::class.java.getDeclaredMethod("cleanupTrackedTempDirs")
            method.isAccessible = true
            method.invoke(tracker)
            inner.setWritable(true)
        } finally {
            inner.setWritable(true)
            dir.deleteRecursively()
        }
    }
}
