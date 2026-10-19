package com.stacklens.application.service

import com.stacklens.application.port.inbound.platform.EssentialToolsUseCase
import com.stacklens.application.port.inbound.platform.InstallToolResult
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.Privilege
import com.stacklens.application.port.out.process.ShellPort
import com.stacklens.application.service.essential.ToolInstallation
import com.stacklens.application.service.essential.ToolInstaller
import com.stacklens.application.service.essential.ToolManualCommandProvider
import com.stacklens.application.stereotype.ApplicationService
import org.slf4j.LoggerFactory

@ApplicationService
class EssentialToolsService(
    private val i18n: I18nPort,
    private val platformPort: PlatformPort,
    private val shellPort: ShellPort,
    private val toolInstallers: List<ToolInstaller>,
    private val toolManualCommands: List<ToolManualCommandProvider>,
) : EssentialToolsUseCase {
    private val log = LoggerFactory.getLogger(EssentialToolsService::class.java)

    override fun getStatus(): Map<String, Any?> {
        val p = platformPort.detect()
        return mapOf(
            "platform" to
                mapOf(
                    "os" to p.os,
                    "distroId" to p.distroId,
                    "distroVersion" to p.distroVersion,
                    "pretty" to p.pretty,
                    "supported" to p.supported,
                ),
            "tools" to toolStatus(),
        )
    }

    override fun install(tool: String): InstallToolResult {
        val p = platformPort.detect()
        if (!p.supported) {
            return failedInstall(manualCommand = manualCommand(tool, p), stderr = "")
        }
        val script =
            toolInstallers
                .firstOrNull { it.toolId == tool }
                ?.installScript(p)
                ?: return failedInstall(manualCommand = null, stderr = tr("essential_tools.be_unknown_tool", tool))
        val shellCommand = shellPort.shellCommand(script, privilegeFor(p))
        log.info("Installing tool '{}' on platform '{}'. needsPrivilege={}", tool, p.pretty, shellCommand.requiresPrivilege)
        val res = shellPort.run(shellCommand.command, INSTALL_TIMEOUT_SECONDS)
        return InstallToolResult(
            success = res.success,
            exitCode = res.exitCode,
            stdout = res.stdout.takeLast(MAX_STREAMED_OUTPUT_CHARS),
            stderr = res.stderr.takeLast(MAX_STREAMED_OUTPUT_CHARS),
            needsPrivilege = shellCommand.requiresPrivilege,
            manualCommand = if (!res.success) manualCommand(tool, p) else null,
            tools = toolStatus(),
        )
    }

    private fun privilegeFor(platform: Platform): Privilege = if (platform.osType == OsType.LINUX) Privilege.ELEVATED else Privilege.NONE

    private fun failedInstall(
        manualCommand: String?,
        stderr: String,
    ): InstallToolResult =
        InstallToolResult(
            success = false,
            exitCode = null,
            stdout = "",
            stderr = stderr,
            needsPrivilege = false,
            manualCommand = manualCommand,
            tools = toolStatus(),
        )

    private fun toolStatus(): List<Map<String, Any?>> {
        val detected = LinkedHashMap<String, ToolInstallation>()
        return orderedInstallers().map { installer ->
            val dependency = installer.requiresToolId?.let(detected::get)
            val installation =
                if (installer.requiresToolId != null && dependency?.installed != true) {
                    ToolInstallation(installed = false)
                } else {
                    installer.detect(shellPort, dependency)
                }
            detected[installer.toolId] = installation
            statusEntry(installer, installation, dependency)
        }
    }

    private fun orderedInstallers(): List<ToolInstaller> =
        toolInstallers.sortedWith(
            compareBy(
                { it.requiresToolId != null },
                { it.toolId },
            ),
        )

    private fun statusEntry(
        installer: ToolInstaller,
        installation: ToolInstallation,
        dependency: ToolInstallation?,
    ): Map<String, Any?> =
        buildMap {
            put("id", installer.toolId)
            put("name", installer.displayName)
            put("installed", installation.installed)
            put("path", installation.path ?: dependency?.path)
            put("version", installation.version)
            put("required", installer.required)
            installer.requiresToolId?.let { put("requires", it) }
        }

    private fun manualCommand(
        tool: String,
        platform: Platform,
    ): String =
        toolManualCommands
            .firstOrNull { it.toolId == tool }
            ?.manualCommand(platform)
            ?: tr("essential_tools.be_unknown_tool", tool)

    private fun tr(
        key: String,
        vararg args: Any,
    ): String = i18n.t(key, *args)

    private companion object {
        const val INSTALL_TIMEOUT_SECONDS = 600L
        const val MAX_STREAMED_OUTPUT_CHARS = 20_000
    }
}
