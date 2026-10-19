package com.stacklens.adapter.out.autostart

import com.stacklens.application.port.out.platform.AutostartStore
import com.stacklens.domain.autostart.AutostartMode

class MacosAutostartStore(
    private val files: AutostartFileStore,
    private val plistLabel: String,
    private val serverModeMarker: String,
    private val primaryExecPath: String,
    private val serverPort: Int,
) : AutostartStore {
    private fun plist() = files.macosPlist(plistLabel)

    override fun isInstalled(): Boolean = files.exists(plist())

    override fun installedMode(): AutostartMode =
        if (isInstalled() && files.read(plist()).contains(serverModeMarker)) {
            AutostartMode.SERVER
        } else {
            AutostartMode.DESKTOP
        }

    override fun install(mode: AutostartMode) {
        val content =
            if (mode == AutostartMode.SERVER) {
                AutostartEntryTemplates.macosServerPlist(
                    exec = resolveExecPath(),
                    homeDir = files.homeDir,
                    port = serverPort,
                    label = plistLabel,
                    serverModeMarker = serverModeMarker,
                )
            } else {
                AutostartEntryTemplates.macosDesktopPlist(plistLabel)
            }
        files.write(plist(), content)
    }

    override fun uninstall() {
        files.deleteWithinHome(plist())
    }

    private fun resolveExecPath(): String {
        val candidates = listOf(primaryExecPath, files.homeApplicationsMacosExec())
        return candidates.firstOrNull { files.isExecutable(it) } ?: candidates.first()
    }
}
