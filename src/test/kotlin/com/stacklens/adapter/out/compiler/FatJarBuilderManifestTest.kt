package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.TestJars
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.jar.Attributes

class FatJarBuilderManifestTest {
    private val builder = FatJarBuilder()

    @Test
    fun `buildFatJar copies stdlib entries and keeps directory entries empty`(
        @TempDir tmp: Path,
    ) {
        val classesDir = File(tmp.toFile(), "classes").apply { mkdirs() }
        File(classesDir, "Main.class").writeBytes(byteArrayOf(1))
        val stdlib =
            TestJars.jar(
                File(tmp.toFile(), "stdlib.jar"),
                entries = mapOf("kotlin/Unit.class" to TestJars.bytesOf("unit")),
                directories = listOf("kotlin"),
            )
        val fatJar = File(tmp.toFile(), "tool.jar")

        builder.buildFatJar(fatJar, classesDir, stdlib)

        assertEquals(
            listOf("META-INF/MANIFEST.MF", "Main.class", "kotlin/", "kotlin/Unit.class"),
            TestJars.entryNames(fatJar).sorted(),
        )
        assertEquals(emptyList<Byte>(), TestJars.entryBytes(fatJar, "kotlin/")!!.toList())
        assertEquals(listOf<Byte>('u'.code.toByte()), TestJars.entryBytes(fatJar, "kotlin/Unit.class")!!.take(1))
    }

    @Test
    fun `buildFatJar skips stdlib entries that the classes directory already provides`(
        @TempDir tmp: Path,
    ) {
        val classesDir = File(tmp.toFile(), "classes").apply { mkdirs() }
        File(classesDir, "Main.class").writeBytes(byteArrayOf(1, 2))
        val stdlib =
            TestJars.jar(
                File(tmp.toFile(), "stdlib.jar"),
                entries = mapOf("Main.class" to TestJars.bytesOf("from-stdlib")),
            )
        val fatJar = File(tmp.toFile(), "tool.jar")

        builder.buildFatJar(fatJar, classesDir, stdlib)

        assertEquals(listOf("META-INF/MANIFEST.MF", "Main.class"), TestJars.entryNames(fatJar).sorted())
        assertEquals(listOf<Byte>(1.toByte(), 2.toByte()), TestJars.entryBytes(fatJar, "Main.class")!!.toList())
    }

    @Test
    fun `ensureMainManifest adds the default main class for a jar without a manifest`(
        @TempDir tmp: Path,
    ) {
        val jarFile = TestJars.zip(File(tmp.toFile(), "no-manifest.jar"), entries = mapOf("Foo.class" to TestJars.bytesOf("foo")))

        builder.ensureMainManifest(jarFile)

        val manifest = TestJars.manifestOf(jarFile)
        assertNotNull(manifest)
        assertEquals("MainKt", manifest!!.mainAttributes.getValue(Attributes.Name.MAIN_CLASS))
        assertEquals(listOf("Foo.class", "META-INF/MANIFEST.MF"), TestJars.entryNames(jarFile).sorted())
    }

    @Test
    fun `ensureMainManifest keeps an existing main class and adds the tool marker`(
        @TempDir tmp: Path,
    ) {
        val jarFile =
            TestJars.jar(
                File(tmp.toFile(), "tool.jar"),
                mainClass = "com.example.Entry",
                entries = mapOf("com/example/Entry.class" to TestJars.bytesOf("entry")),
                directories = listOf("com/example"),
            )

        builder.ensureMainManifest(jarFile)

        val manifest = requireNotNull(TestJars.manifestOf(jarFile))
        assertEquals("com.example.Entry", manifest.mainAttributes.getValue(Attributes.Name.MAIN_CLASS))
        assertEquals("true", manifest.mainAttributes.getValue("X-StackLens-Tool"))
        assertEquals(emptyList<Byte>(), TestJars.entryBytes(jarFile, "com/example/")!!.toList())
    }

    @Test
    fun `ensureMainManifest throws when the jar cannot be opened for the rewrite`(
        @TempDir tmp: Path,
    ) {
        val gone = File(tmp.toFile(), "gone.jar")

        assertThrows(java.io.IOException::class.java) { builder.ensureMainManifest(gone) }
    }

    @Test
    fun `findJarOnClasspath returns null when the classpath is not readable`() {
        assertNull(FatJarBuilder(classPathSupplier = { null }).findJarOnClasspath("kotlin-stdlib"))
    }

    @Test
    fun `findJarOnClasspath returns the first usable entry and skips unusable candidates`() {
        val classpath =
            listOf(
                "/libs/kotlin-stdlib-notes.txt",
                "/libs/kotlin-stdlib-javadoc.jar",
                "/libs/kotlin-stdlib-sources.jar",
                "/libs/kotlin-stdlib-2.3.20.jar",
                "/libs/kotlin-stdlib-2.4.0.jar",
            ).joinToString(File.pathSeparator)

        val found = FatJarBuilder(classPathSupplier = { classpath }).findJarOnClasspath("kotlin-stdlib")

        assertEquals("/libs/kotlin-stdlib-2.3.20.jar", found)
    }

    @Test
    fun `findJarOnClasspath returns null when no entry matches the prefix`() {
        val classpath = listOf("/libs/kotlin-reflect.jar", "/libs/other.jar").joinToString(File.pathSeparator)

        assertNull(FatJarBuilder(classPathSupplier = { classpath }).findJarOnClasspath("kotlin-stdlib"))
    }
}
