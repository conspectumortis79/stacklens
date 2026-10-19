package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.tooling.StoredToolJar
import com.stacklens.application.port.out.tooling.ToolJarRejectedException
import com.stacklens.application.port.out.tooling.ToolJarRejection
import com.stacklens.application.port.out.tooling.ToolJarStore
import com.stacklens.domain.util.closingAuto
import java.io.File
import java.util.jar.JarFile

class FileToolJarStore(
    private val stackLensConfig: StackLensConfigPort,
) : ToolJarStore {
    override fun contains(jarPath: String): Boolean = File(jarPath).exists()

    override fun deleteJar(jarPath: String) {
        val file = File(jarPath)
        if (file.exists()) file.delete()
    }

    override fun storeImported(
        originalFilename: String,
        fileBytes: ByteArray,
    ): StoredToolJar {
        val outputDir = File(stackLensConfig.dataDir, TOOL_SUB_DIR).apply { mkdirs() }
        val jarFile = File(outputDir, "imported-${System.currentTimeMillis()}-${sanitize(originalFilename)}")
        jarFile.writeBytes(fileBytes)
        val manifest =
            try {
                JarFile(jarFile).closingAuto { it.manifest }
            } catch (_: Exception) {
                jarFile.delete()
                throw ToolJarRejectedException(ToolJarRejection.NOT_A_JAR)
            }
        val mainAttributes = manifest?.mainAttributes
        val marker = mainAttributes?.getValue(TOOL_MARKER_ATTRIBUTE)
        if (mainAttributes == null || marker?.trim()?.lowercase() != STACKLENS_TOOL_MARKER_VALUE) {
            jarFile.delete()
            throw ToolJarRejectedException(ToolJarRejection.MISSING_STACKLENS_MARKER)
        }
        return StoredToolJar(
            jarPath = jarFile.absolutePath,
            embeddedName = mainAttributes.getValue(NAME_ATTRIBUTE),
            embeddedIcon = mainAttributes.getValue(ICON_ATTRIBUTE),
        )
    }

    private fun sanitize(originalFilename: String): String =
        originalFilename
            .replace(INVALID_CHAR_REGEX, "_")
            .replace(CONSECUTIVE_DOTS_REGEX, "_")
            .replace(LEADING_DOTS_REGEX, "_")

    private companion object {
        const val TOOL_SUB_DIR = "tools"
        const val TOOL_MARKER_ATTRIBUTE = "X-StackLens-Tool"
        const val NAME_ATTRIBUTE = "X-StackLens-Name"
        const val ICON_ATTRIBUTE = "X-StackLens-Icon"
        const val STACKLENS_TOOL_MARKER_VALUE = "true"
        val INVALID_CHAR_REGEX = Regex("[^a-zA-Z0-9_.-]")
        val CONSECUTIVE_DOTS_REGEX = Regex("\\.{2,}")
        val LEADING_DOTS_REGEX = Regex("^\\.+")
    }
}
