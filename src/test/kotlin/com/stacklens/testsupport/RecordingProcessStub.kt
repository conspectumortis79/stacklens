package com.stacklens.testsupport

import java.io.File

class RecordingProcessStub(
    directory: File,
    private val name: String = "probe-executable",
    private vararg val script: String,
) {
    val argumentsFile: File = File(directory, "$name-arguments.txt")

    val executable: File =
        File(directory, name).apply {
            writeText(
                buildString {
                    appendLine("#!/bin/sh")
                    appendLine("""printf '%s\n' "${'$'}@" > "${argumentsFile.absolutePath}" """)
                    script.forEach { appendLine(it) }
                },
            )
            setExecutable(true)
        }

    fun recordedArguments(): List<String> = if (argumentsFile.exists()) argumentsFile.readLines().filter { it.isNotEmpty() } else emptyList()
}
