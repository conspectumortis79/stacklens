package com.stacklens.application.port.out.repo

fun interface ShellCommandRunner {
    operator fun invoke(vararg cmd: String): String?
}
