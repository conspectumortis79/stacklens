package com.stacklens.adapter.out.autostart

object AutostartEntryTemplates {
    fun macosServerPlist(
        exec: String,
        homeDir: String,
        port: Int,
        label: String,
        serverModeMarker: String,
    ): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>Label</key>
            <string>$label</string>
            <!-- $serverModeMarker -->
            <key>ProgramArguments</key>
            <array>
                <string>$exec</string>
                <string>--server</string>
            </array>
            <key>EnvironmentVariables</key>
            <dict>
                <key>STACKLENS_SERVER_MODE</key>
                <string>1</string>
                <key>STACKLENS_SERVER_PORT</key>
                <string>$port</string>
            </dict>
            <key>RunAtLoad</key>
            <true/>
            <key>KeepAlive</key>
            <true/>
            <key>StandardOutPath</key>
            <string>$homeDir/Library/Logs/StackLens-server.log</string>
            <key>StandardErrorPath</key>
            <string>$homeDir/Library/Logs/StackLens-server.err.log</string>
        </dict>
        </plist>
        """.trimIndent()

    fun macosDesktopPlist(label: String): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>Label</key>
            <string>$label</string>
            <key>ProgramArguments</key>
            <array>
                <string>/usr/bin/open</string>
                <string>-a</string>
                <string>StackLens</string>
            </array>
            <key>RunAtLoad</key>
            <true/>
        </dict>
        </plist>
        """.trimIndent()

    fun linuxDesktopEntry(
        name: String,
        comment: String,
        execLine: String,
        serverModeMarker: String,
        isServer: Boolean,
    ): String {
        val markerLine = if (isServer) "# $serverModeMarker" else ""
        return """
            [Desktop Entry]
            Type=Application
            Name=$name
            Comment=$comment
            Exec=$execLine
            Hidden=false
            NoDisplay=false
            X-GNOME-Autostart-enabled=true
            $markerLine
            """.trimIndent()
    }
}
