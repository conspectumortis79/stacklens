package com.stacklens.adapter.out.compiler

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FatJarBuilderIoFailureTest {
    private fun restrict(
        file: File,
        spec: String,
    ) {
        if (!file.exists()) return
        attributeView(file)?.setPermissions(PosixFilePermissions.fromString(spec))
    }

    private fun attributeView(file: File): PosixFileAttributeView? = Files.getFileAttributeView(file.toPath(), PosixFileAttributeView::class.java)

    private fun corruptJar(target: File) {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zip.write("Manifest-Version: 1.0\r\n\r\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("a/A.class"))
            zip.write(ByteArray(300) { (it % 251).toByte() })
            zip.closeEntry()
        }
        val bytes = buffer.toByteArray()
        for (index in bytes.size / 4 until bytes.size * 3 / 4) {
            bytes[index] = (bytes[index] + 7).toByte()
        }
        target.writeBytes(bytes)
    }

    @Test
    fun `an unreadable class file aborts the fat jar build`(
        @TempDir dir: Path,
    ) {
        val classes = File(dir.toFile(), "classes").apply { mkdirs() }
        val source = File(classes, "MainKt.class").apply { writeText("cas") }
        restrict(source, "---------")
        val target = File(dir.toFile(), "tool.jar")
        val builder = FatJarBuilder()
        try {
            assertThrows(Exception::class.java) { builder.buildFatJar(target, classes, null) }
        } finally {
            restrict(source, "rw-r--r--")
        }
    }

    @Test
    fun `a corrupted standard library jar aborts the fat jar build`(
        @TempDir dir: Path,
    ) {
        val classes = File(dir.toFile(), "classes").apply { mkdirs() }
        File(classes, "MainKt.class").writeText("cas")
        val stdlib = File(dir.toFile(), "stdlib.jar")
        corruptJar(stdlib)
        val target = File(dir.toFile(), "tool.jar")

        assertThrows(Exception::class.java) { FatJarBuilder().buildFatJar(target, classes, stdlib) }
    }

    @Test
    fun `a corrupted jar cannot be inspected or rewritten by the manifest fix`(
        @TempDir dir: Path,
    ) {
        val jar = File(dir.toFile(), "tool.jar")
        corruptJar(jar)
        val builder = FatJarBuilder()

        assertThrows(Exception::class.java) { builder.ensureMainManifest(jar) }
    }

    @Test
    fun `a healthy jar keeps its manifest untouched`(
        @TempDir dir: Path,
    ) {
        val jar = File(dir.toFile(), "good.jar")
        val buffer = ByteArrayOutputStream()
        java.util.jar.JarOutputStream(buffer, java.util.jar.Manifest()).use { out ->
            out.putNextEntry(ZipEntry("MainKt.class"))
            out.write(byteArrayOf(1, 2, 3))
            out.closeEntry()
        }
        jar.writeBytes(buffer.toByteArray())

        FatJarBuilder().ensureMainManifest(jar)

        JarFile(jar).use { check ->
            assertTrue(check.entries().toList().any { it.name == "MainKt.class" })
        }
    }
}
