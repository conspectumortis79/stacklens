package com.stacklens.adapter.out.compiler

import com.stacklens.domain.util.closingAuto
import org.slf4j.LoggerFactory
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

class FatJarBuilder(
    private val classPathSupplier: () -> String? = { System.getProperty("java.class.path") },
) {
    private val log = LoggerFactory.getLogger(FatJarBuilder::class.java)

    fun buildFatJar(
        fatJar: File,
        classesDir: File,
        stdlibJar: File?,
    ) {
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name.MAIN_CLASS] = "MainKt"
                mainAttributes[Attributes.Name("X-StackLens-Tool")] = "true"
            }
        val seen = mutableSetOf<String>()

        JarOutputStream(fatJar.outputStream(), manifest).use { out ->

            classesDir.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                val entryName =
                    classesDir
                        .toPath()
                        .relativize(file.toPath())
                        .toString()
                        .replace('\\', '/')
                seen += entryName
                out.putNextEntry(ZipEntry(entryName))
                file.inputStream().closingAuto { it.copyTo(out) }
                out.closeEntry()
            }

            if (stdlibJar != null && stdlibJar.exists()) {
                JarFile(stdlibJar).closingAuto { stdlib ->
                    stdlib.entries().asSequence().forEach { entry ->
                        if (entry.name == "META-INF/MANIFEST.MF") return@forEach
                        if (entry.name.endsWith(".SF") ||
                            entry.name.endsWith(".DSA") ||
                            entry.name.endsWith(".RSA")
                        ) {
                            return@forEach
                        }
                        if (!seen.add(entry.name)) return@forEach
                        out.putNextEntry(ZipEntry(entry.name))
                        if (!entry.isDirectory) {
                            stdlib.getInputStream(entry).closingAuto { it.copyTo(out) }
                        }
                        out.closeEntry()
                    }
                }
            }
        }
    }

    fun ensureMainManifest(jarFile: File) {
        var existingMainClass: String? = null
        var hasToolMarker = false

        try {
            JarFile(jarFile).closingAuto { jar ->
                val attrs = jar.manifest?.mainAttributes
                existingMainClass = attrs?.getValue(Attributes.Name.MAIN_CLASS)?.takeIf { it.isNotBlank() }
                hasToolMarker = attrs?.getValue("X-StackLens-Tool")?.trim()?.lowercase() == "true"
            }
        } catch (_: Exception) {
        }
        if (existingMainClass != null && hasToolMarker) return

        val effectiveMainClass = existingMainClass ?: "MainKt"
        log.info("[ensureMainManifest] Setze Main-Class: {} und X-StackLens-Tool: true in {}", effectiveMainClass, jarFile.name)
        val tmpFile = File(jarFile.parent, jarFile.name + ".manifest-fix.tmp")
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name.MAIN_CLASS] = effectiveMainClass
                mainAttributes[Attributes.Name("X-StackLens-Tool")] = "true"
            }
        JarOutputStream(tmpFile.outputStream(), manifest).use { out ->
            JarFile(jarFile).closingAuto { jar ->
                jar.entries().asSequence().forEach { entry ->
                    if (entry.name == "META-INF/MANIFEST.MF") return@forEach
                    out.putNextEntry(ZipEntry(entry.name))
                    if (!entry.isDirectory) jar.getInputStream(entry).closingAuto { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
        tmpFile.renameTo(jarFile)
    }

    fun findJarOnClasspath(namePrefix: String): String? {
        val entries = classPathSupplier()?.split(File.pathSeparator) ?: return null
        return entries.firstOrNull { entry ->
            val name = File(entry).name
            name.startsWith(namePrefix) &&
                name.endsWith(".jar") &&
                !name.contains("sources") &&
                !name.contains("javadoc")
        }
    }
}
