package com.stacklens.shared

object ToolJarCommand {
    fun of(
        javaExecutable: String,
        jarPath: String,
    ): List<String> = listOf(javaExecutable, "-jar", jarPath)
}
