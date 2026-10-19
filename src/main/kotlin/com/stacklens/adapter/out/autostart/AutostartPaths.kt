package com.stacklens.adapter.out.autostart

object AutostartPaths {
    const val MACOS_PLIST_LABEL: String = "com.stacklens.app"
    const val LINUX_DESKTOP_FILENAME: String = "StackLens.desktop"
    const val SERVER_MODE_MARKER: String = "STACKLENS_SERVER_MODE=1"
    const val MACOS_PRIMARY_EXEC: String = "/Applications/StackLens.app/Contents/MacOS/StackLens"
    const val LINUX_ALT_EXEC: String = "/usr/bin/StackLens"
    const val LINUX_OPT_EXEC: String = "/opt/stacklens/bin/StackLens"
}
