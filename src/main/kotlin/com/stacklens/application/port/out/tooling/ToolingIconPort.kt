package com.stacklens.application.port.out.tooling

interface ToolingIconPort {
    fun determineIcon(
        toolName: String,
        generatedCode: String,
    ): String?

    fun generateIconPng(toolName: String): ByteArray?

    fun embedIconInJar(
        jarPath: String,
        toolName: String,
        icon: String,
        iconPng: ByteArray?,
    )

    fun trySetMacOsFinderIcon(
        jarPath: String,
        iconPngBytes: ByteArray,
    )
}
