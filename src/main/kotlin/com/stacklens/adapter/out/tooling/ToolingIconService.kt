package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.tooling.ToolingIconPort
import org.springframework.stereotype.Component

@Component
class ToolingIconService(
    private val aiPlanner: ToolingIconAiPlanner,
    private val pngRenderer: ToolingIconPngRenderer,
    private val jarIconEmbedder: ToolingJarIconEmbedder,
    private val finderIconInstaller: ToolingFinderIconInstaller,
) : ToolingIconPort {
    override fun determineIcon(
        toolName: String,
        generatedCode: String,
    ): String? = aiPlanner.determineIcon(toolName, generatedCode)

    override fun generateIconPng(toolName: String): ByteArray? = pngRenderer.generateIconPng(toolName)

    override fun embedIconInJar(
        jarPath: String,
        toolName: String,
        icon: String,
        iconPng: ByteArray?,
    ) = jarIconEmbedder.embedIconInJar(jarPath, toolName, icon, iconPng)

    override fun trySetMacOsFinderIcon(
        jarPath: String,
        iconPngBytes: ByteArray,
    ) = finderIconInstaller.trySetMacOsFinderIcon(jarPath, iconPngBytes)
}
