package com.stacklens.application.service.essential

import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class GhToolManualCommand : ToolManualCommandProvider {
    override val toolId: String = "gh"

    override fun manualCommand(platform: Platform): String =
        when (platform.osType) {
            OsType.MACOS -> "brew install gh"
            OsType.LINUX -> "sudo apt update && sudo apt install -y gh   # after adding the cli.github.com repository"
            OsType.WINDOWS -> "winget install --id GitHub.cli"
            else -> "Please install 'gh' manually: https://cli.github.com/"
        }
}
