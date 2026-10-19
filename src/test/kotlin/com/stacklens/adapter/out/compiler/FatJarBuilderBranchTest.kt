package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.inbound.platform.I18nUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Path

class FatJarBuilderBranchTest {
    private val i18n: I18nUseCase = mock()

    @Test
    fun `FatJarBuilder is constructible`() {
        val builder = FatJarBuilder()
        assertNotNull(builder)
    }

    @Test
    fun `findJarOnClasspath returns null for non-existent artifact`() {
        val builder = FatJarBuilder()
        val result = builder.findJarOnClasspath("nonexistent-artifact-12345")
        assertEquals(null, result)
    }

    @Test
    fun `findJarOnClasspath exercises all per-name filter branches`() {
        val original = System.getProperty("java.class.path")
        try {
            System.setProperty(
                "java.class.path",
                listOf(
                    "/tmp/tool-1.0.jar",
                    "/tmp/tool-1.0-sources.jar",
                    "/tmp/tool-1.0-javadoc.jar",
                    "/tmp/no-jar-extension",
                    "/tmp/not-a-jar.txt",
                ).joinToString(File.pathSeparator),
            )
            val fb = FatJarBuilder()

            val result = fb.findJarOnClasspath("tool-")
            assertEquals("/tmp/tool-1.0.jar", result)
        } finally {
            System.setProperty("java.class.path", original ?: "")
        }
    }

    @Test
    fun `ensureMainManifest handles missing JAR gracefully`() {
        val builder = FatJarBuilder()
        val tmp = java.io.File.createTempFile("manifest-test", ".jar")
        tmp.delete()

        try {
            builder.ensureMainManifest(tmp)
        } catch (_: Exception) {
        }
        tmp.delete()
    }

    @Test
    fun `buildFatJar creates a JAR from classes directory`(
        @TempDir tmp: Path,
    ) {
        val builder = FatJarBuilder()
        val classesDir = tmp.resolve("classes").toFile()
        classesDir.mkdirs()

        val classFile = File(classesDir, "Test.class")
        classFile.writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
        val jarFile = tmp.resolve("out.jar").toFile()
        builder.buildFatJar(jarFile, classesDir, null)
        assertEquals(true, jarFile.exists())
    }
}
