package com.stacklens.adapter.out.autostart

import com.stacklens.application.port.out.platform.OsType
import com.stacklens.domain.autostart.AutostartMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutostartPathsTest {
    @Test
    fun `the entry paths and markers keep their published values`() {
        assertEquals("com.stacklens.app", AutostartPaths.MACOS_PLIST_LABEL)
        assertEquals("StackLens.desktop", AutostartPaths.LINUX_DESKTOP_FILENAME)
        assertEquals("STACKLENS_SERVER_MODE=1", AutostartPaths.SERVER_MODE_MARKER)
        assertEquals("/Applications/StackLens.app/Contents/MacOS/StackLens", AutostartPaths.MACOS_PRIMARY_EXEC)
        assertEquals("/usr/bin/StackLens", AutostartPaths.LINUX_ALT_EXEC)
        assertEquals("/opt/stacklens/bin/StackLens", AutostartPaths.LINUX_OPT_EXEC)
    }

    @Test
    fun `the platform detection maps the host name onto the os types`() {
        assertEquals(OsType.MACOS, OsType.fromOsName("mac os x"))
        assertEquals(OsType.LINUX, OsType.fromOsName("LINUX"))
        assertEquals(OsType.WINDOWS, OsType.fromOsName("windows 10"))
        assertEquals(OsType.UNSUPPORTED, OsType.fromOsName("freebsd"))
    }

    @Test
    fun `the platform wire values match the REST payload of the autostart endpoints`() {
        assertEquals("macos", OsType.MACOS.wire)
        assertEquals("linux", OsType.LINUX.wire)
        assertEquals("unsupported", OsType.UNSUPPORTED.wire)
    }

    @Test
    fun `a mode is parsed from the request payload case-insensitively`() {
        assertEquals(AutostartMode.SERVER, AutostartMode.fromWire("SERVER"))
        assertEquals(AutostartMode.SERVER, AutostartMode.fromWire(" server "))
        assertEquals(AutostartMode.DESKTOP, AutostartMode.fromWire("anything else"))
        assertEquals(AutostartMode.DESKTOP, AutostartMode.fromWire(null))
    }

    @Test
    fun `the server plist carries label, exec, port and the mode marker`() {
        val plist =
            AutostartEntryTemplates.macosServerPlist(
                exec = "/opt/stacklens/bin/StackLens",
                homeDir = "/home/tester",
                port = 8099,
                label = "com.stacklens.app",
                serverModeMarker = AutostartPaths.SERVER_MODE_MARKER,
            )

        assertTrue(plist.contains("<string>/opt/stacklens/bin/StackLens</string>"))
        assertTrue(plist.contains("<string>--server</string>"))
        assertTrue(plist.contains("<string>8099</string>"))
        assertTrue(plist.contains(AutostartPaths.SERVER_MODE_MARKER))
        assertTrue(plist.contains("/home/tester/Library/Logs/StackLens-server.log"))
    }

    @Test
    fun `the desktop plist launches the bundled application`() {
        val plist = AutostartEntryTemplates.macosDesktopPlist("com.stacklens.app")

        assertTrue(plist.contains("<string>/usr/bin/open</string>"))
        assertTrue(plist.contains("<string>-a</string>"))
        assertTrue(plist.contains("<string>StackLens</string>"))
        assertTrue(!plist.contains(AutostartPaths.SERVER_MODE_MARKER))
    }

    @Test
    fun `the linux entry differs between desktop and server mode`() {
        val desktop =
            AutostartEntryTemplates.linuxDesktopEntry(
                name = "StackLens",
                comment = "StackLens automatisch starten",
                execLine = "/usr/bin/StackLens",
                serverModeMarker = AutostartPaths.SERVER_MODE_MARKER,
                isServer = false,
            )
        val server =
            AutostartEntryTemplates.linuxDesktopEntry(
                name = "StackLens (Server)",
                comment = "Hintergrund-Server",
                execLine = "/usr/bin/StackLens --server",
                serverModeMarker = AutostartPaths.SERVER_MODE_MARKER,
                isServer = true,
            )

        assertTrue(desktop.contains("Name=StackLens"))
        assertTrue(desktop.contains("X-GNOME-Autostart-enabled=true"))
        assertTrue(!desktop.contains(AutostartPaths.SERVER_MODE_MARKER))
        assertTrue(server.contains("Name=StackLens (Server)"))
        assertTrue(server.contains("# ${AutostartPaths.SERVER_MODE_MARKER}"))
    }
}
