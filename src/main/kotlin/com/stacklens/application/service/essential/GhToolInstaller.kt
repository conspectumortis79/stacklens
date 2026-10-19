package com.stacklens.application.service.essential

import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.port.out.process.ShellPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired

@ApplicationService
@ManuallyWired
class GhToolInstaller : ToolInstaller {
    override val toolId: String = "gh"

    override val displayName: String = "GitHub CLI (gh)"

    override val required: Boolean = true

    override val requiresToolId: String? = null

    override fun installScript(platform: Platform): String? =
        when (platform.osType) {
            OsType.MACOS -> "set -e; brew install gh"
            OsType.LINUX -> linuxGhInstallScript()
            else -> null
        }

    override fun detect(
        shell: ShellPort,
        dependency: ToolInstallation?,
    ): ToolInstallation {
        val path = shell.commandPath(toolId)
        val version = path?.let { shell.firstNonBlankLine("$toolId --version") }
        return ToolInstallation(installed = path != null, path = path, version = version)
    }

    private fun linuxGhInstallScript(): String =
        """
        set -e
        export DEBIAN_FRONTEND=noninteractive
        type -p curl >/dev/null || apt-get update && apt-get install -y curl
        mkdir -p -m 755 /etc/apt/keyrings
        out=${'$'}(mktemp)
        curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg -o "${'$'}out"
        cat "${'$'}out" > /etc/apt/keyrings/githubcli-archive-keyring.gpg
        chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg
        echo "deb [arch=${'$'}(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
            > /etc/apt/sources.list.d/github-cli.list
        apt-get update
        apt-get install -y gh
        """.trimIndent()
}
