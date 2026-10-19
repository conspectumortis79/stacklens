package com.stacklens.application.service.essential

import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.port.out.process.ShellPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired

@ApplicationService
@ManuallyWired
class GhCopilotToolInstaller : ToolInstaller {
    override val toolId: String = "gh-copilot"

    override val displayName: String = "GitHub Copilot CLI"

    override val required: Boolean = true

    override val requiresToolId: String? = "gh"

    override fun installScript(platform: Platform): String? =
        when (platform.osType) {
            OsType.MACOS, OsType.LINUX ->
                "set +e; gh extension install github/gh-copilot 2>&1; " +
                    "gh copilot --help >/dev/null 2>&1 && echo 'gh copilot ready' && exit 0; " +
                    "echo 'gh copilot is not available — please run: gh auth login' >&2; exit 1"
            else -> null
        }

    override fun detect(
        shell: ShellPort,
        dependency: ToolInstallation?,
    ): ToolInstallation =
        ToolInstallation(
            installed = shell.commandSucceeds("$GH_COMMAND copilot --help"),
            path = dependency?.path,
        )

    private companion object {
        const val GH_COMMAND = "gh"
    }
}
