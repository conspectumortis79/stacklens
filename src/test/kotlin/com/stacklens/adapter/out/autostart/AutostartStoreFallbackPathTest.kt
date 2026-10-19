package com.stacklens.adapter.out.autostart

import com.stacklens.domain.autostart.AutostartMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class AutostartStoreFallbackPathTest {
    private val marker = AutostartPaths.SERVER_MODE_MARKER

    private fun desktop(home: File) = AutostartFileStore(home.absolutePath).linuxDesktopFile(AutostartPaths.LINUX_DESKTOP_FILENAME)

    private fun linuxStore(
        home: File,
        alt: String = "/usr/bin/StackLens",
        opt: String = "/opt/stacklens/bin/StackLens",
        processCommand: (() -> String?)? = null,
    ): LinuxAutostartStore {
        val files = if (processCommand == null) AutostartFileStore(home.absolutePath) else AutostartFileStore(home.absolutePath, processCommand = processCommand)
        return LinuxAutostartStore(
            files = files,
            desktopFileName = AutostartPaths.LINUX_DESKTOP_FILENAME,
            serverModeMarker = marker,
            altExecPath = alt,
            optExecPath = opt,
            serverPort = 8090,
        )
    }

    @Test
    fun `an entry without the autostart flag is not installed`(
        @TempDir home: Path,
    ) {
        val store = linuxStore(home.toFile())
        store.install(AutostartMode.DESKTOP)
        val entry = desktop(home.toFile())

        entry.writeText("[Desktop Entry]\nName=StackLens\n")
        assertFalse(store.isInstalled())

        entry.writeText("[Desktop Entry]\nX-GNOME-Autostart-enabled=true\nHidden=true\n")
        assertFalse(store.isInstalled())

        entry.writeText("[Desktop Entry]\nX-GNOME-Autostart-enabled=true\n")
        assertTrue(store.isInstalled())
    }

    @Test
    fun `the installed mode falls back to desktop for an absent or plain entry`(
        @TempDir home: Path,
    ) {
        val store = linuxStore(home.toFile())

        assertEquals(AutostartMode.DESKTOP, store.installedMode())

        desktop(home.toFile()).apply { parentFile.mkdirs() }.writeText("[Desktop Entry]\nName=StackLens\n")
        assertEquals(AutostartMode.DESKTOP, store.installedMode())

        desktop(home.toFile()).writeText("# $marker\n")
        assertEquals(AutostartMode.SERVER, store.installedMode())
    }

    @Test
    fun `the preferred installation path wins over the fallback location`(
        @TempDir root: Path,
    ) {
        val opt =
            File(root.toFile(), "opt/StackLens").apply {
                parentFile.mkdirs()
                writeText("x")
            }
        val store = linuxStore(File(root.toFile(), "home"), alt = File(root.toFile(), "missing/alt").absolutePath, opt = opt.absolutePath)

        store.install(AutostartMode.SERVER)

        assertTrue(desktop(File(root.toFile(), "home")).readText().contains("Exec=${opt.absolutePath} --server"))
    }

    @Test
    fun `the running process is used when no installation path exists and the command names stacklens`(
        @TempDir root: Path,
    ) {
        val store =
            linuxStore(
                File(root.toFile(), "home"),
                alt = File(root.toFile(), "missing/alt").absolutePath,
                opt = File(root.toFile(), "missing/opt").absolutePath,
                processCommand = { "/srv/StackLens --foreground" },
            )

        store.install(AutostartMode.DESKTOP)

        assertTrue(desktop(File(root.toFile(), "home")).readText().contains("Exec=/srv/StackLens --foreground"))
    }

    @Test
    fun `the running jvm is not mistaken for a stacklens installation`(
        @TempDir root: Path,
    ) {
        val opt = File(root.toFile(), "missing/opt").absolutePath
        val store =
            linuxStore(
                File(root.toFile(), "home"),
                alt = File(root.toFile(), "missing/alt").absolutePath,
                opt = opt,
            )

        store.install(AutostartMode.DESKTOP)

        assertTrue(desktop(File(root.toFile(), "home")).readText().contains("Exec=$opt"))
    }

    @Test
    fun `the fallback path survives an unresolvable process command`(
        @TempDir root: Path,
    ) {
        val opt = File(root.toFile(), "missing/opt").absolutePath
        val store =
            linuxStore(
                File(root.toFile(), "home"),
                alt = File(root.toFile(), "missing/alt").absolutePath,
                opt = opt,
                processCommand = { null },
            )

        store.install(AutostartMode.DESKTOP)

        assertTrue(desktop(File(root.toFile(), "home")).readText().contains("Exec=$opt"))
    }

    @Test
    fun `the macos entry uses the user bundle when the system bundle is absent`(
        @TempDir root: Path,
    ) {
        val home = File(root.toFile(), "home").apply { mkdirs() }
        val userBundle = File(home, "Applications/StackLens.app/Contents/MacOS/StackLens")
        userBundle.parentFile.mkdirs()
        userBundle.writeText("#!/bin/sh\n")
        assertTrue(userBundle.setExecutable(true))
        val store =
            MacosAutostartStore(
                files = AutostartFileStore(home.absolutePath),
                plistLabel = AutostartPaths.MACOS_PLIST_LABEL,
                serverModeMarker = marker,
                primaryExecPath = File(root.toFile(), "missing/StackLens").absolutePath,
                serverPort = 8090,
            )

        store.install(AutostartMode.SERVER)

        val plist = File(home, "Library/LaunchAgents/${AutostartPaths.MACOS_PLIST_LABEL}.plist").readText()
        assertTrue(plist.contains(userBundle.canonicalFile.absolutePath) || plist.contains(userBundle.absolutePath))
    }

    @Test
    fun `the macos entry falls back to the system bundle when nothing is executable`(
        @TempDir root: Path,
    ) {
        val home = File(root.toFile(), "home").apply { mkdirs() }
        val store =
            MacosAutostartStore(
                files = AutostartFileStore(home.absolutePath),
                plistLabel = AutostartPaths.MACOS_PLIST_LABEL,
                serverModeMarker = marker,
                primaryExecPath = "/Applications/StackLens.app/Contents/MacOS/StackLens",
                serverPort = 8090,
            )

        store.install(AutostartMode.SERVER)

        assertTrue(File(home, "Library/LaunchAgents/${AutostartPaths.MACOS_PLIST_LABEL}.plist").readText().contains("/Applications/StackLens.app"))
    }
}
