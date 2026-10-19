package com.stacklens.testsupport

import com.stacklens.application.port.out.process.Privilege
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.port.out.process.ShellCommand
import com.stacklens.application.port.out.process.ShellPort

class FakeShellPort(
    private val pathOfCommand: (String) -> String? = { null },
    private val firstLineOf: (String) -> String? = { null },
    private val successful: (String) -> Boolean = { false },
) : ShellPort {
    val probedCommands: MutableList<String> = mutableListOf()

    override fun commandPath(command: String): String? {
        probedCommands += command
        return pathOfCommand(command)
    }

    override fun firstNonBlankLine(command: String): String? = firstLineOf(command)

    override fun commandSucceeds(command: String): Boolean = successful(command)

    override fun shellCommand(
        script: String,
        privilege: Privilege,
    ): ShellCommand = ShellCommand(listOf("/bin/sh", "-c", script), requiresPrivilege = privilege == Privilege.ELEVATED)

    override fun run(
        command: List<String>,
        timeoutSec: Long,
    ): ProcessResult = ProcessResult(exitCode = 0, stdout = "", stderr = "")
}
