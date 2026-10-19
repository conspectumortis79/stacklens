package com.stacklens.adapter.out.process

import com.stacklens.application.port.out.platform.HostDirectoryPort
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.ExecutableLookupPort
import com.stacklens.application.port.out.process.ShellPort
import org.springframework.stereotype.Component
import java.io.File

@Component
class FileSystemExecutableProbe {
    fun isExecutable(path: String): Boolean = File(path).canExecute()
}

class HostExecutableLookup(
    private val shell: ShellPort,
    private val hostDirectories: HostDirectoryPort,
    private val platform: PlatformPort,
    private val contributors: List<ExecutablePathContributor>,
    private val executableProbe: FileSystemExecutableProbe,
) : ExecutableLookupPort {
    override fun resolve(
        executable: String,
        preferredPaths: List<String>,
    ): String? {
        val probed = runCatching { shell.commandPath(executable) }.getOrNull()?.trim()
        if (!probed.isNullOrBlank() && executableProbe.isExecutable(probed)) return probed
        val candidates = preferredPaths + directoriesFor(executable).map { "$it/$executable" }
        return candidates.firstOrNull { executableProbe.isExecutable(it) }
    }

    override fun directoriesFor(executable: String): List<String> {
        val host =
            ExecutableHost(
                osType = platform.detect().osType,
                homeDirectory = hostDirectories.homeDirectory(),
            )
        return (
            contributors
                .filter { it.supports(executable) }
                .flatMap { it.directories(executable, host) } +
                commonDirectories(host)
        ).distinct()
    }

    private fun commonDirectories(host: ExecutableHost): List<String> =
        listOf(
            "${host.homeDirectory}/.local/bin",
            "/usr/local/bin",
            "/snap/bin",
            "/opt/homebrew/bin",
            "/opt/homebrew/sbin",
            "/usr/bin",
            "/usr/sbin",
        )
}
