package com.stacklens.adapter.out.mcp

import com.stacklens.application.port.out.mcp.McpEnvironmentPort
import com.stacklens.application.port.out.mcp.McpNodeLocator
import com.stacklens.application.port.out.mcp.McpProcessSpawnerPort
import com.stacklens.application.port.out.mcp.McpScriptLocator
import com.stacklens.application.port.out.platform.OsType
import org.springframework.stereotype.Component
import java.io.File

@Component
class DefaultMcpScriptLocator(
    private val env: McpEnvironmentPort = DefaultMcpEnvironmentPort(),
) : McpScriptLocator {
    override fun locateScript(): File? {
        val cwd = env.getCurrentDir()
        val javaHome = env.getJavaHome()
        val candidates = mutableListOf<File>()
        val override =
            env.getEnv("STACKLENS_MCP_SCRIPT")
                ?: env.getProperty("stacklens.mcp.script")
        if (!override.isNullOrBlank()) candidates += File(override)
        candidates += File(cwd, "mcp-server/dist/index.js")
        candidates += File(cwd, "../mcp-server/dist/index.js")
        if (javaHome != null) {
            javaHome.parentFile?.parentFile?.parentFile?.let {
                candidates += File(it, "app/mcp-server/dist/index.js")
            }
            javaHome.parentFile?.let {
                candidates += File(it, "app/mcp-server/dist/index.js")
            }
        }
        return candidates.firstOrNull { it.exists() && it.isFile }?.absoluteFile
    }
}

@Component
class DefaultMcpNodeLocator(
    private val env: McpEnvironmentPort = DefaultMcpEnvironmentPort(),
    private val spawner: McpProcessSpawnerPort = DefaultMcpProcessSpawnerPort(),
    private val loginShells: List<String> = DEFAULT_LOGIN_SHELLS,
    private val wellKnownPathTemplates: List<String> = DEFAULT_WELL_KNOWN_TEMPLATES,
    private val bundledToolchainDir: File = File("build/nodejs"),
) : McpNodeLocator {
    companion object {
        val DEFAULT_LOGIN_SHELLS: List<String> = listOf("/bin/zsh", "/bin/bash", "/bin/sh")

        val DEFAULT_WELL_KNOWN_TEMPLATES: List<String> =
            listOf(
                "/opt/homebrew/bin/node",
                "/usr/local/bin/node",
                "{home}/.nvm/versions/node",
                "{home}/.asdf/shims/node",
                "{home}/.nodenv/shims/node",
                "{home}/.volta/bin/node",
                "/usr/local/n/versions/node",
                "/opt/local/bin/node",
            )
    }

    override fun locateNode(): String? {
        val isWin = OsType.fromOsName(env.getOsName()) == OsType.WINDOWS
        val cmd = if (isWin) "node.exe" else "node"

        val pathEnv = env.getEnv("PATH").orEmpty()
        for (dir in pathEnv.split(File.pathSeparator)) {
            val f = File(dir, cmd)
            if (f.isFile && f.canExecute() && nodeMajorVersion(f.absolutePath) >= 20) return f.absolutePath
        }

        if (!isWin) {
            for (shell in loginShells) {
                if (!File(shell).canExecute()) continue
                runCatching {
                    val out =
                        spawner.runAndCapture(
                            listOf(shell, "-l", "-c", "command -v node 2>/dev/null"),
                            null,
                            3,
                        )
                    if (!out.isNullOrBlank() &&
                        File(out).isFile &&
                        File(out).canExecute() &&
                        nodeMajorVersion(out) >= 20
                    ) {
                        return out
                    }
                }
                break
            }
        }

        if (!isWin) {
            val home = env.getUserHome().orEmpty()
            val candidates = wellKnownPathTemplates.map { it.replace("{home}", home) }
            for (path in candidates) {
                val f = File(path)
                if (f.isFile && f.canExecute() && nodeMajorVersion(f.absolutePath) >= 20) return f.absolutePath
                if (f.isDirectory) {
                    val found =
                        f
                            .walkTopDown()
                            .filter { it.name == cmd && it.isFile && it.canExecute() }
                            .sortedDescending()
                            .firstOrNull { nodeMajorVersion(it.absolutePath) >= 20 }
                    if (found != null) return found.absolutePath
                }
            }
        }

        val bundled =
            bundledToolchainDir
                .walkTopDown()
                .filter { it.name == cmd && it.isFile && it.canExecute() }
                .firstOrNull { nodeMajorVersion(it.absolutePath) >= 20 }
        return bundled?.absolutePath
    }

    private fun nodeMajorVersion(path: String): Int {
        val out = spawner.runAndCapture(listOf(path, "--version"), null, 3).orEmpty()
        return out.removePrefix("v").substringBefore('.').toIntOrNull() ?: 0
    }
}
