package com.stacklens.adapter.out.autostart

import com.stacklens.application.port.out.platform.AutostartStore
import com.stacklens.domain.autostart.AutostartMode
import java.io.File

class LinuxAutostartStore(
    private val files: AutostartFileStore,
    private val desktopFileName: String,
    private val serverModeMarker: String,
    private val altExecPath: String,
    private val optExecPath: String,
    private val serverPort: Int,
    private val processCommand: () -> String? = { files.currentProcessCommand() },
) : AutostartStore {
    private fun desktopFile() = files.linuxDesktopFile(desktopFileName)

    override fun isInstalled(): Boolean {
        val file = desktopFile()
        if (!files.exists(file)) return false
        val text = files.read(file)
        return text.contains(AUTOSTART_ENABLED_MARKER) && !text.contains(HIDDEN_MARKER)
    }

    override fun installedMode(): AutostartMode =
        if (files.exists(desktopFile()) && files.read(desktopFile()).contains(serverModeMarker)) {
            AutostartMode.SERVER
        } else {
            AutostartMode.DESKTOP
        }

    override fun install(mode: AutostartMode) {
        val isServer = mode == AutostartMode.SERVER
        val execPath = resolveExecPath()
        files.write(
            desktopFile(),
            AutostartEntryTemplates.linuxDesktopEntry(
                name = if (isServer) SERVER_ENTRY_NAME else ENTRY_NAME,
                comment =
                    if (isServer) {
                        "StackLens als Hintergrund-Server (Browser: http://localhost:$serverPort)"
                    } else {
                        "StackLens automatisch starten"
                    },
                execLine = if (isServer) "$execPath --server" else execPath,
                serverModeMarker = serverModeMarker,
                isServer = isServer,
            ),
        )
    }

    override fun uninstall() {
        files.deleteWithinHome(desktopFile())
    }

    private fun resolveExecPath(): String {
        val alt = File(altExecPath)
        if (files.exists(alt)) return alt.absolutePath
        val opt = File(optExecPath)
        if (files.exists(opt)) return opt.absolutePath
        val command = processCommand()
        if (command != null && command.contains("stacklens", ignoreCase = true)) return command
        return optExecPath
    }

    private companion object {
        const val AUTOSTART_ENABLED_MARKER = "X-GNOME-Autostart-enabled=true"
        const val HIDDEN_MARKER = "Hidden=true"
        const val ENTRY_NAME = "StackLens"
        const val SERVER_ENTRY_NAME = "StackLens (Server)"
    }
}
