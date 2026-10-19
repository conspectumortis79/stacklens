package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.TestJars
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.util.jar.Attributes
import java.util.jar.JarFile

class FatJarBuilderIoCoverageTest {
    private val builder = FatJarBuilder()

    @Test
    fun `buildFatJar copies class files and the stdlib entries into one archive`(
        @TempDir dir: File,
    ) {
        val classes = File(dir, "classes").apply { mkdirs() }
        File(classes, "MainKt.class").writeText("fake-class")
        File(classes, "nested").mkdirs()
        File(classes, "nested/Other.class").writeText("other-class")
        File(classes, "empty-dir-marker").mkdirs()

        val stdlib =
            TestJars.jar(
                File(dir, "stdlib.jar"),
                mainClass = "kotlin.LazyKt",
                entries =
                    mapOf(
                        "kotlin/Lazy.class" to TestJars.bytesOf("lazy"),
                        "MainKt.class" to TestJars.bytesOf("duplicate"),
                        "META-INF/OK.SF" to TestJars.bytesOf("signature"),
                        "META-INF/OK.DSA" to TestJars.bytesOf("digest"),
                        "META-INF/OK.RSA" to TestJars.bytesOf("rsa"),
                    ),
                directories = listOf("kotlin/internal"),
            )

        val fat = File(dir, "fat.jar")
        builder.buildFatJar(fat, classes, stdlib)

        val entryNames = jarEntryNames(fat)
        assertTrue(entryNames.contains("MainKt.class"))
        assertTrue(entryNames.contains("nested/Other.class"))
        assertTrue(entryNames.contains("kotlin/Lazy.class"))
        assertTrue(entryNames.contains("kotlin/internal/"))
        assertEquals(null, entryNames.firstOrNull { it.endsWith(".SF") })
        assertEquals("fake-class", TestJars.entryBytes(fat, "MainKt.class")?.decodeToString())
    }

    @Test
    fun `buildFatJar tolerates a missing stdlib jar`(
        @TempDir dir: File,
    ) {
        val classes = File(dir, "classes").apply { mkdirs() }
        File(classes, "A.class").writeText("a")

        builder.buildFatJar(File(dir, "fat.jar"), classes, File(dir, "missing.jar"))

        assertTrue(File(dir, "fat.jar").exists())
    }

    @Test
    fun `ensureMainManifest rewrites an archive that lacks the tool marker`(
        @TempDir dir: File,
    ) {
        val jar =
            TestJars.jar(
                File(dir, "tool.jar"),
                mainClass = "MainKt",
                entries = mapOf("A.class" to TestJars.bytesOf("a")),
            )

        builder.ensureMainManifest(jar)

        val manifest = JarFile(jar).use { file -> file.manifest }
        assertNotNull(manifest)
        assertEquals("true", manifest.mainAttributes.getValue("X-StackLens-Tool"))
        assertEquals("MainKt", manifest.mainAttributes.getValue(Attributes.Name.MAIN_CLASS))
        assertEquals("a", TestJars.entryBytes(jar, "A.class")?.decodeToString())
    }

    @Test
    fun `ensureMainManifest keeps an archive that already carries the marker`(
        @TempDir dir: File,
    ) {
        val jar =
            TestJars.jar(
                File(dir, "tool.jar"),
                mainClass = "MainKt",
                toolMarker = "true",
                entries = mapOf("B.class" to TestJars.bytesOf("b")),
            )

        builder.ensureMainManifest(jar)

        assertEquals("b", TestJars.entryBytes(jar, "B.class")?.decodeToString())
    }

    @Test
    fun `ensureMainManifest reports an unreadable archive through an io exception`(
        @TempDir dir: File,
    ) {
        val broken = File(dir, "broken.jar").apply { writeText("not-a-zip") }

        assertThrows<IOException> { builder.ensureMainManifest(broken) }

        assertTrue(broken.exists())
    }

    @Test
    fun `findJarOnClasspath skips sources and javadoc artefacts`() {
        val classpath = "/libs/kotlin-compiler-embeddable-sources.jar:/libs/kotlin-compiler-embeddable-javadoc.jar:/libs/kotlin-compiler-embeddable-2.0.jar"

        assertEquals("/libs/kotlin-compiler-embeddable-2.0.jar", FatJarBuilder { classpath }.findJarOnClasspath("kotlin-compiler-embeddable"))
        assertEquals(null, FatJarBuilder { null }.findJarOnClasspath("kotlin-compiler-embeddable"))
        assertEquals(null, FatJarBuilder { classpath }.findJarOnClasspath("absent"))
    }

    private fun jarEntryNames(jar: File): List<String> =
        JarFile(jar).use { file ->
            file
                .entries()
                .toList()
                .map { entry -> entry.name }
        }
}
