package com.stacklens.adapter.out.process

import com.stacklens.application.port.out.process.Privilege
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.port.out.process.ShellCommand
import com.stacklens.application.port.out.process.ShellPort
import org.springframework.stereotype.Component

@Component
class ShellCommandAdapter(
    private val processPort: ProcessRunPort,
) : ShellPort {
    override fun commandPath(command: String): String? =
        runScript("command -v $command", PROBE_TIMEOUT_SECONDS)
            .let { result -> if (result.success) firstPathLine(result.stdout) else null }

    override fun firstNonBlankLine(command: String): String? =
        runScript(command, PROBE_TIMEOUT_SECONDS)
            .let { result ->
                if (!result.success) {
                    null
                } else {
                    result.stdout
                        .lineSequence()
                        .firstOrNull { it.isNotBlank() }
                        ?.trim()
                }
            }

    override fun commandSucceeds(command: String): Boolean = runScript(command, CHECK_TIMEOUT_SECONDS).success

    override fun shellCommand(
        script: String,
        privilege: Privilege,
    ): ShellCommand =
        when (privilege) {
            Privilege.NONE -> ShellCommand(listOf(SHELL, "-c", script), requiresPrivilege = false)
            Privilege.ELEVATED -> elevated(script)
        }

    override fun run(
        command: List<String>,
        timeoutSec: Long,
    ): ProcessResult = processPort.run(command, timeoutSec = timeoutSec)

    private fun elevated(script: String): ShellCommand {
        val pkexec = commandPath(PKEXEC)
        return if (pkexec != null) {
            ShellCommand(listOf(PKEXEC, SHELL, "-c", script), requiresPrivilege = true)
        } else {
            ShellCommand(listOf(SHELL, "-c", "$SUDO_PREFIX $SHELL -c ${quote(script)}"), requiresPrivilege = true)
        }
    }

    private fun runScript(
        script: String,
        timeoutSec: Long,
    ): ProcessResult = processPort.run(listOf(SHELL, "-lc", script), timeoutSec = timeoutSec)

    private fun firstPathLine(stdout: String): String? =
        stdout
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith(COMMENT_PREFIX) }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        const val SHELL = "/bin/sh"
        const val PKEXEC = "pkexec"
        const val SUDO_PREFIX = "sudo -n"
        const val PROBE_TIMEOUT_SECONDS = 5L
        const val CHECK_TIMEOUT_SECONDS = 10L
        const val COMMENT_PREFIX = "#"
    }
}
