package com.stacklens.adapter.out.autostart

import com.stacklens.domain.autostart.AutostartMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class AutostartStoresTest {
    private val marker = AutostartPaths.SERVER_MODE_MARKER

    @Test
    fun `the macos entry is absent until it is installed`(
        @TempDir home: Path,
    ) {
        val store = macosStore(home)

        assertFalse(store.isInstalled())
        assertEquals(AutostartMode.DESKTOP, store.installedMode())
    }

    @Test
    fun `installing the macos desktop entry launches the bundled app`(
        @TempDir home: Path,
    ) {
        val store = macosStore(home)

        store.install(AutostartMode.DESKTOP)

        val plist = File(home.toFile(), "Library/LaunchAgents/${AutostartPaths.MACOS_PLIST_LABEL}.plist")
        assertTrue(plist.readText().contains("/usr/bin/open"))
        assertTrue(store.isInstalled())
        assertEquals(AutostartMode.DESKTOP, store.installedMode())
    }

    @Test
    fun `installing the macos server entry records the server mode and port`(
        @TempDir home: Path,
    ) {
        val store = macosStore(home)

        store.install(AutostartMode.SERVER)

        val plist = File(home.toFile(), "Library/LaunchAgents/${AutostartPaths.MACOS_PLIST_LABEL}.plist").readText()
        assertTrue(plist.contains(marker))
        assertTrue(plist.contains("<string>--server</string>"))
        assertTrue(plist.contains("<key>STACKLENS_SERVER_PORT</key>"))
        assertTrue(plist.contains("<string>8099</string>"))
        assertEquals(AutostartMode.SERVER, store.installedMode())
    }

    @Test
    fun `uninstalling removes the macos entry`(
        @TempDir home: Path,
    ) {
        val store = macosStore(home)
        store.install(AutostartMode.DESKTOP)

        store.uninstall()

        assertFalse(store.isInstalled())
    }

    @Test
    fun `the linux entry counts as installed only with the autostart marker`(
        @TempDir home: Path,
    ) {
        val store = linuxStore(home)
        val desktop = File(home.toFile(), ".config/autostart/${AutostartPaths.LINUX_DESKTOP_FILENAME}")
        store.install(AutostartMode.DESKTOP)
        assertTrue(store.isInstalled())

        desktop.writeText("[Desktop Entry]`nX-GNOME-Autostart-enabled=true".replace("`", "\n"))
        assertTrue(store.isInstalled())

        desktop.writeText("[Desktop Entry]\nHidden=true\n")
        assertFalse(store.isInstalled())
    }

    @Test
    fun `installing the linux server entry announces the background server`(
        @TempDir home: Path,
    ) {
        val store = linuxStore(home)

        store.install(AutostartMode.SERVER)

        val desktop = File(home.toFile(), ".config/autostart/${AutostartPaths.LINUX_DESKTOP_FILENAME}").readText()
        assertTrue(desktop.contains(marker))
        assertTrue(desktop.contains("Name=StackLens (Server)"))
        assertTrue(desktop.contains("--server"))
        assertTrue(desktop.contains("http://localhost:8099"))
        assertEquals(AutostartMode.SERVER, store.installedMode())
    }

    @Test
    fun `the linux executable is resolved from the preferred installation paths`(
        @TempDir root: Path,
    ) {
        val alt =
            File(root.toFile(), "alt/StackLens").apply {
                parentFile.mkdirs()
                writeText("x")
            }
        val store = linuxStore(root.resolve("home"), altExec = alt.absolutePath)

        store.install(AutostartMode.DESKTOP)

        assertTrue(desktopOf(root).contains("Exec=${alt.absolutePath}"))
    }

    @Test
    fun `a running stacklens process is used when no installation path exists`(
        @TempDir root: Path,
    ) {
        val store =
            linuxStore(
                home = root.resolve("home"),
                altExec = File(root.toFile(), "missing/alt").absolutePath,
                optExec = File(root.toFile(), "missing/opt").absolutePath,
                processCommand = { "/opt/other/StackLens --server" },
            )

        store.install(AutostartMode.SERVER)

        assertTrue(desktopOf(root).contains("Exec=/opt/other/StackLens --server --server"))
    }

    @Test
    fun `the fallback executable path is used when nothing matches`(
        @TempDir root: Path,
    ) {
        val opt = File(root.toFile(), "missing/opt").absolutePath
        val store =
            linuxStore(
                home = root.resolve("home"),
                altExec = File(root.toFile(), "missing/alt").absolutePath,
                optExec = opt,
                processCommand = { "/usr/bin/java" },
            )

        store.install(AutostartMode.DESKTOP)

        assertTrue(desktopOf(root).contains("Exec=$opt"))
    }

    @Test
    fun `uninstalling removes the linux entry`(
        @TempDir root: Path,
    ) {
        val store = linuxStore(root.resolve("home"))
        store.install(AutostartMode.DESKTOP)

        store.uninstall()

        assertFalse(store.isInstalled())
    }

    private fun macosStore(home: Path) =
        MacosAutostartStore(
            files = AutostartFileStore(home.toFile().absolutePath),
            plistLabel = AutostartPaths.MACOS_PLIST_LABEL,
            serverModeMarker = marker,
            primaryExecPath = "/Applications/StackLens.app/Contents/MacOS/StackLens",
            serverPort = 8099,
        )

    private fun linuxStore(
        home: Path,
        altExec: String = "/usr/bin/StackLens",
        optExec: String = "/opt/stacklens/bin/StackLens",
        processCommand: () -> String? = { null },
    ) = LinuxAutostartStore(
        files = AutostartFileStore(home.toFile().absolutePath),
        desktopFileName = AutostartPaths.LINUX_DESKTOP_FILENAME,
        serverModeMarker = marker,
        altExecPath = altExec,
        optExecPath = optExec,
        serverPort = 8099,
        processCommand = processCommand,
    )

    private fun desktopOf(root: Path) = File(root.toFile(), "home/.config/autostart/${AutostartPaths.LINUX_DESKTOP_FILENAME}").readText()
}
