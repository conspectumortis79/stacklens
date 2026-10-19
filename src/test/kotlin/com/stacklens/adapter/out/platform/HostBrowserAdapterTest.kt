package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.port.out.platform.PlatformPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HostBrowserAdapterTest {
    private val recorded = mutableListOf<List<String>>()

    private fun adapter(os: OsType) =
        object : HostBrowserAdapter(
            platform =
                object : PlatformPort {
                    override fun detect(): Platform = Platform(os, os.wire, null, os.wire, true)
                },
        ) {
            override fun start(command: List<String>) {
                if (command.last().contains("unreachable")) throw IllegalStateException("not installed")
                recorded += command
            }
        }

    @Test
    fun `macOS opens through the open command`() {
        adapter(OsType.MACOS).browse("https://example.com")

        assertTrue(recorded.single().contains("open"))
        assertTrue(recorded.single().last() == "https://example.com")
    }

    @Test
    fun `windows starts through the cmd shell`() {
        adapter(OsType.WINDOWS).browse("https://example.com")

        assertTrue(recorded.single().contains("start"))
    }

    @Test
    fun `other platforms use xdg-open`() {
        adapter(OsType.LINUX).browse("https://example.com")

        assertTrue(recorded.single().first() == "xdg-open")
    }

    @Test
    fun `a failing browser command falls back to the desktop integration`() {
        var desktopCalls = 0
        val target =
            object : HostBrowserAdapter(
                platform =
                    object : PlatformPort {
                        override fun detect(): Platform = Platform(OsType.LINUX, "linux", null, "linux", true)
                    },
            ) {
                override fun start(command: List<String>): Unit = throw IllegalStateException("not installed")

                override fun browseWithDesktop(url: String) {
                    desktopCalls++
                }
            }

        target.browse("https://example.com")

        assertEquals(1, desktopCalls)
        assertTrue(recorded.isEmpty())
    }
}
