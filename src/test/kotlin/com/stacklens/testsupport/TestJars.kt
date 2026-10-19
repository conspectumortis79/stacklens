package com.stacklens.testsupport

import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TestJars {
    fun jar(
        target: File,
        mainClass: String? = null,
        toolMarker: String? = null,
        entries: Map<String, ByteArray> = emptyMap(),
        directories: List<String> = emptyList(),
    ): File {
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                if (mainClass != null) mainAttributes[Attributes.Name.MAIN_CLASS] = mainClass
                if (toolMarker != null) mainAttributes[Attributes.Name("X-StackLens-Tool")] = toolMarker
            }
        return write(target, JarOutputStream(target.outputStream(), manifest), entries, directories)
    }

    fun zip(
        target: File,
        entries: Map<String, ByteArray> = emptyMap(),
        directories: List<String> = emptyList(),
    ): File = write(target, ZipOutputStream(target.outputStream()), entries, directories)

    fun entryNames(jarFile: File): List<String> =
        withJarFile(jarFile) { file ->
            java.util.Collections
                .list(file.entries())
                .map { entry -> entry.name }
        }

    fun entryBytes(
        jarFile: File,
        entryName: String,
    ): ByteArray? =
        withJarFile(jarFile) { file ->
            val entry = file.getJarEntry(entryName) ?: return@withJarFile null
            file.getInputStream(entry).use { it.readBytes() }
        }

    fun manifestOf(jarFile: File): Manifest? = withJarFile(jarFile) { it.manifest }

    fun bytesOf(text: String): ByteArray = text.toByteArray()

    private inline fun <T> withJarFile(
        file: File,
        block: (JarFile) -> T,
    ): T = JarFile(file).use(block)

    private fun write(
        target: File,
        out: ZipOutputStream,
        entries: Map<String, ByteArray>,
        directories: List<String>,
    ): File {
        target.parentFile?.mkdirs()
        out.use { stream ->
            directories.forEach { name ->
                stream.putNextEntry(ZipEntry(if (name.endsWith("/")) name else "$name/"))
                stream.closeEntry()
            }
            entries.forEach { (name, content) ->
                stream.putNextEntry(ZipEntry(name))
                stream.write(content)
                stream.closeEntry()
            }
        }
        return target
    }
}
