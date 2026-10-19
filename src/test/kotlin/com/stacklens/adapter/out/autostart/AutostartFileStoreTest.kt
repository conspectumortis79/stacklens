package com.stacklens.adapter.out.autostart

import com.stacklens.application.port.out.platform.AutostartStorageException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class AutostartFileStoreTest {
    @Test
    fun `paths are resolved below the home directory`() {
        val store = AutostartFileStore("/home/tester")

        assertEquals("/home/tester/.config/autostart/StackLens.desktop", store.linuxDesktopFile("StackLens.desktop").path)
        assertEquals("/home/tester/Library/LaunchAgents/com.stacklens.app.plist", store.macosPlist("com.stacklens.app").path)
        assertEquals("/home/tester/Applications/StackLens.app/Contents/MacOS/StackLens", store.homeApplicationsMacosExec())
    }

    @Test
    fun `a written entry lands below the home tree and can be removed again`(
        @TempDir home: Path,
    ) {
        val store = AutostartFileStore(home.toFile().absolutePath)
        val entry = store.linuxDesktopFile(AutostartPaths.LINUX_DESKTOP_FILENAME)

        store.write(entry, "X-GNOME-Autostart-enabled=true")

        assertTrue(entry.exists())
        assertTrue(store.exists(entry))
        store.deleteWithinHome(entry)
        assertFalse(entry.exists())
    }

    @Test
    fun `deleting a path outside the home tree is refused`(
        @TempDir root: Path,
    ) {
        val home = File(root.toFile(), "home").apply { mkdirs() }
        val alien = File(root.toFile(), "alien.txt").apply { writeText("x") }
        val store = AutostartFileStore(home.absolutePath)

        val failure = assertThrows(AutostartStorageException::class.java) { store.deleteWithinHome(alien) }

        assertEquals(AutostartFileStore.KEY_DELETE_FAILED, failure.i18nKey)
        assertEquals(alien.canonicalFile.absolutePath, failure.path)
        assertTrue(alien.exists())
    }

    @Test
    fun `a directory is never treated as an autostart entry`(
        @TempDir home: Path,
    ) {
        val store = AutostartFileStore(home.toFile().absolutePath)
        val dir = store.linuxAutostartDir().apply { mkdirs() }

        val failure = assertThrows(AutostartStorageException::class.java) { store.deleteWithinHome(dir) }

        assertTrue(dir.exists())
        assertEquals(AutostartFileStore.KEY_DELETE_FAILED, failure.i18nKey)
    }

    @Test
    fun `removing an absent entry changes nothing`(
        @TempDir home: Path,
    ) {
        val store = AutostartFileStore(home.toFile().absolutePath)

        store.deleteWithinHome(store.macosPlist(AutostartPaths.MACOS_PLIST_LABEL))

        assertFalse(store.macosPlist(AutostartPaths.MACOS_PLIST_LABEL).exists())
    }
}
