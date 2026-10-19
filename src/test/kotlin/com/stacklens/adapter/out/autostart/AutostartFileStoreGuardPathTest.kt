package com.stacklens.adapter.out.autostart

import com.stacklens.application.port.out.platform.AutostartStorageException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class AutostartFileStoreGuardPathTest {
    @Test
    fun `directory checks and plain reads are answered by the file system`(
        @TempDir home: Path,
    ) {
        val store = AutostartFileStore(home.toFile().absolutePath)
        val autostartDir = store.linuxAutostartDir().apply { mkdirs() }

        assertFalse(store.isDirectory(File(home.toFile(), "missing")))
        assertTrue(store.isDirectory(autostartDir))

        val entry = store.linuxDesktopFile("StackLens.desktop")
        store.write(entry, "content")
        assertEquals("content", store.read(entry))
        assertTrue(store.exists(entry))
    }

    @Test
    fun `executable candidates are judged by the operating system`(
        @TempDir home: Path,
    ) {
        val store = AutostartFileStore(home.toFile().absolutePath)
        val runnable = File(home.toFile(), "runner").apply { writeText("#!/bin/sh\n") }

        assertFalse(store.isExecutable(runnable.absolutePath))
        assertTrue(runnable.setExecutable(true))
        assertTrue(store.isExecutable(runnable.absolutePath))
        assertFalse(store.isExecutable(File(home.toFile(), "absent").absolutePath))
    }

    @Test
    fun `the running jvm command is reported when the process exposes it`(
        @TempDir home: Path,
    ) {
        val store = AutostartFileStore(home.toFile().absolutePath)

        assertTrue(store.currentProcessCommand()!!.contains("java"))
    }

    @Test
    fun `an absent process command is passed through`(
        @TempDir home: Path,
    ) {
        val store = AutostartFileStore(home.toFile().absolutePath, processCommand = { null })

        assertEquals(null, store.currentProcessCommand())
    }

    @Test
    fun `writing to a parentless path fails instead of creating a root entry`() {
        val store = AutostartFileStore("/tmp")

        assertThrows(IOException::class.java) { store.write(File("/"), "nope") }
    }

    @Test
    fun `a failing canonicalisation keeps the guard on the absolute path`() {
        val dir = Files.createTempDirectory("autost-canonical").toFile().canonicalFile
        val inside = File(dir, "StackLens.desktop").apply { writeText("x") }
        val outside = File(Files.createTempDirectory("autost-outside").toFile(), "alien.desktop").apply { writeText("x") }
        val store =
            AutostartFileStore(
                homeDir = dir.absolutePath,
                canonicalizer = { file -> throw IOException("cannot resolve ${file.name}") },
            )

        store.deleteWithinHome(inside)
        assertFalse(inside.exists())

        val failure = assertThrows(AutostartStorageException::class.java) { store.deleteWithinHome(outside) }
        assertEquals(AutostartFileStore.KEY_DELETE_FAILED, failure.i18nKey)
        assertEquals(outside.absolutePath, failure.path)
        assertTrue(outside.exists())
    }

    @Test
    fun `a canonical home that cannot be resolved keeps the plain home prefix`() {
        val dir = Files.createTempDirectory("autost-home").toFile().canonicalFile
        val entry = File(dir, "StackLens.desktop").apply { writeText("x") }
        val store =
            AutostartFileStore(
                homeDir = dir.absolutePath,
                canonicalizer = { file -> if (file == dir) throw IOException("home unreadable") else file.canonicalFile },
            )

        store.deleteWithinHome(entry)

        assertFalse(entry.exists())
    }

    @Test
    fun `an attribute lookup that fails refuses the deletion`() {
        val dir = Files.createTempDirectory("autost-attrs").toFile()
        val entry = File(dir, "StackLens.desktop").apply { writeText("x") }
        val store =
            AutostartFileStore(
                homeDir = dir.absolutePath,
                regularFileMarker = { throw IOException("attributes unavailable") },
            )

        assertThrows(AutostartStorageException::class.java) { store.deleteWithinHome(entry) }
        assertTrue(entry.exists())
    }

    @Test
    fun `a file that cannot be removed is reported instead of silently ignored`() {
        val dir = Files.createTempDirectory("autost-delete").toFile()
        val entry = File(dir, "StackLens.desktop").apply { writeText("x") }
        val store = AutostartFileStore(dir.canonicalFile.absolutePath, regularFileMarker = { true })
        assertTrue(File(dir.canonicalFile.absolutePath).setWritable(false))

        try {
            assertThrows(AutostartStorageException::class.java) { store.deleteWithinHome(entry) }
        } finally {
            File(dir.canonicalFile.absolutePath).setWritable(true)
        }
    }

    @Test
    fun `an existing directory is recognised while a plain file is not`(
        @TempDir home: Path,
    ) {
        val store = AutostartFileStore(home.toFile().absolutePath)
        val plain = File(home.toFile(), "plain.txt").apply { writeText("x") }

        assertTrue(store.isDirectory(home.toFile()))
        assertFalse(store.isDirectory(plain))
        assertEquals(File(home.toFile(), ".config/autostart").path, store.linuxAutostartDir().path)
    }
}
