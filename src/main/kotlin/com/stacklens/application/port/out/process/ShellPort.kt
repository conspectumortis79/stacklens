package com.stacklens.application.port.out.process

enum class Privilege {
    NONE,
    ELEVATED,
}

data class ShellCommand(
    val command: List<String>,
    val requiresPrivilege: Boolean,
)

interface ShellPort {
    fun commandPath(command: String): String?

    fun firstNonBlankLine(command: String): String?

    fun commandSucceeds(command: String): Boolean

    fun shellCommand(
        script: String,
        privilege: Privilege,
    ): ShellCommand

    fun run(
        command: List<String>,
        timeoutSec: Long,
    ): ProcessResult
}
