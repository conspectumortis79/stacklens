package com.stacklens.adapter.out.platform

import com.stacklens.application.port.out.platform.HostBrowserPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.PlatformPort
import org.springframework.stereotype.Component
import java.awt.Desktop
import java.net.URI

@Component
class HostBrowserAdapter(
    private val platform: PlatformPort,
    internal val desktopSupplier: () -> Desktop = { Desktop.getDesktop() },
) : HostBrowserPort {
    override fun browse(url: String) {
        try {
            start(browseCommand(platform.detect().osType, url))
        } catch (_: Exception) {
            browseWithDesktop(url)
        }
    }

    internal open fun start(command: List<String>) {
        ProcessBuilder(command).inheritIO().start()
    }

    internal open fun browseWithDesktop(url: String) {
        desktopSupplier().browse(URI(url))
    }

    private companion object {
        fun browseCommand(
            os: OsType,
            url: String,
        ): List<String> =
            when (os) {
                OsType.MACOS -> listOf("open", url)
                OsType.WINDOWS -> listOf("cmd", "/c", "start", "", url)
                else -> listOf("xdg-open", url)
            }
    }
}
