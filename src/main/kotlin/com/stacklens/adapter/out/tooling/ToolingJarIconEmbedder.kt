package com.stacklens.adapter.out.tooling

import com.stacklens.domain.util.closingAuto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

@Component
class ToolingJarIconEmbedder {
    private val log = LoggerFactory.getLogger(ToolingJarIconEmbedder::class.java)

    fun embedIconInJar(
        jarPath: String,
        toolName: String,
        icon: String,
        iconPng: ByteArray?,
    ) {
        val jarFile = File(jarPath)
        if (!jarFile.exists()) return
        val tempFile = File("$jarPath.tmp")
        try {
            JarFile(jarFile).closingAuto { jar ->
                val manifest =
                    jar.manifest ?: Manifest().apply {
                        mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                    }
                manifest.mainAttributes[Attributes.Name("X-StackLens-Icon")] = icon
                manifest.mainAttributes[Attributes.Name("X-StackLens-Name")] = toolName
                JarOutputStream(tempFile.outputStream(), manifest).closingAuto { out ->
                    jar
                        .entries()
                        .asSequence()
                        .filter { it.name != "META-INF/MANIFEST.MF" && it.name != "META-INF/stacklens-icon.png" }
                        .forEach { entry ->
                            out.putNextEntry(ZipEntry(entry.name))
                            if (!entry.isDirectory) jar.getInputStream(entry).closingAuto { it.copyTo(out) }
                            out.closeEntry()
                        }
                    if (iconPng != null) {
                        out.putNextEntry(ZipEntry("META-INF/stacklens-icon.png"))
                        out.write(iconPng)
                        out.closeEntry()
                    }
                }
            }
            jarFile.delete()
            tempFile.renameTo(jarFile)
        } catch (e: Exception) {
            log.warn("[Tooling] Icon embedding in JAR failed for '{}': {}", toolName, e.message)
            tempFile.delete()
        }
    }
}
