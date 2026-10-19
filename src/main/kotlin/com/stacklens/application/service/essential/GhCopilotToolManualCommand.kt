package com.stacklens.application.service.essential

import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.stereotype.ApplicationService

@ApplicationService
class GhCopilotToolManualCommand : ToolManualCommandProvider {
    override val toolId: String = "gh-copilot"

    override fun manualCommand(platform: Platform): String =
        "gh auth login   # if not already authenticated\n" +
            "gh extension install github/gh-copilot"
}
