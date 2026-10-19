package com.stacklens.application.service.essential

import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.port.out.process.ShellPort

data class ToolInstallation(
    val installed: Boolean,
    val path: String? = null,
    val version: String? = null,
)

interface ToolInstaller {
    val toolId: String

    val displayName: String

    val required: Boolean

    val requiresToolId: String?

    fun installScript(platform: Platform): String?

    fun detect(
        shell: ShellPort,
        dependency: ToolInstallation?,
    ): ToolInstallation
}
