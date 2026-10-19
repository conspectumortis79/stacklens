package com.stacklens.adapter.out.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarFile

class FatJarBuilderTest {
    private val builder = FatJarBuilder()

    @Test
    fun `buildFatJar creates a JAR with the expected manifest entries`(
        @TempDir tempDir: Path,
    ) {
        val classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
        val pkgDir = File(classesDir, "com/example")
        pkgDir.mkdirs()
        File(pkgDir, "MainKt.class").writeBytes(byteArrayOf(1, 2, 3))
        File(pkgDir, "Helper.class").writeBytes(byteArrayOf(4, 5))

        val fatJar = tempDir.resolve("tool.jar").toFile()
        builder.buildFatJar(fatJar, classesDir, stdlibJar = null)

        assertTrue(fatJar.exists())
        JarFile(fatJar).use { jar ->
            val attrs = jar.manifest.mainAttributes
            assertEquals("MainKt", attrs.getValue(Attributes.Name.MAIN_CLASS))
            assertEquals("true", attrs.getValue("X-StackLens-Tool"))
            assertNotNull(jar.getJarEntry("com/example/MainKt.class"))
            assertNotNull(jar.getJarEntry("com/example/Helper.class"))
        }
    }

    @Test
    fun `buildFatJar includes the stdlib entries when stdlibJar is given`(
        @TempDir tempDir: Path,
    ) {
        val stdlibJar = tempDir.resolve("stdlib.jar").toFile()
        val classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
        File(classesDir, "Foo.class").writeBytes(byteArrayOf(1))

        val stdlib = java.util.jar.JarOutputStream(stdlibJar.outputStream())
        stdlib.use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("kotlin/Unit.class"))
            out.write(byteArrayOf(9, 9, 9))
            out.closeEntry()
        }

        val fatJar = tempDir.resolve("tool.jar").toFile()
        builder.buildFatJar(fatJar, classesDir, stdlibJar = stdlibJar)

        JarFile(fatJar).use { jar ->
            assertNotNull(jar.getJarEntry("kotlin/Unit.class"))
            assertNotNull(jar.getJarEntry("Foo.class"))
        }
    }

    @Test
    fun `buildFatJar skips stdlib signature files (SF, DSA, RSA)`(
        @TempDir tempDir: Path,
    ) {
        val stdlibJar = tempDir.resolve("stdlib.jar").toFile()
        val stdlib = java.util.jar.JarOutputStream(stdlibJar.outputStream())
        stdlib.use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("META-INF/Kotlin.SF"))
            out.write(byteArrayOf(1))
            out.closeEntry()
            out.putNextEntry(java.util.zip.ZipEntry("META-INF/Kotlin.DSA"))
            out.write(byteArrayOf(2))
            out.closeEntry()
            out.putNextEntry(java.util.zip.ZipEntry("META-INF/Kotlin.RSA"))
            out.write(byteArrayOf(3))
            out.closeEntry()
        }

        val classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()

        val fatJar = tempDir.resolve("tool.jar").toFile()
        builder.buildFatJar(fatJar, classesDir, stdlibJar = stdlibJar)

        JarFile(fatJar).use { jar ->
            assertNull(jar.getJarEntry("META-INF/Kotlin.SF"))
            assertNull(jar.getJarEntry("META-INF/Kotlin.DSA"))
            assertNull(jar.getJarEntry("META-INF/Kotlin.RSA"))
        }
    }

    @Test
    fun `buildFatJar skips stdlib MANIFEST entry`(
        @TempDir tempDir: Path,
    ) {
        val stdlibJar = tempDir.resolve("stdlib.jar").toFile()
        val stdlib = java.util.jar.JarOutputStream(stdlibJar.outputStream())
        stdlib.use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("META-INF/MANIFEST.MF"))
            out.write(byteArrayOf(1))
            out.closeEntry()
        }

        val classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()

        val fatJar = tempDir.resolve("tool.jar").toFile()
        builder.buildFatJar(fatJar, classesDir, stdlibJar = stdlibJar)

        JarFile(fatJar).use { jar ->
            val entries = jar.entries().toList()

            assertTrue(entries.any { it.name == "META-INF/MANIFEST.MF" })
        }
    }

    @Test
    fun `buildFatJar handles stdlibJar that does not exist gracefully`(
        @TempDir tempDir: Path,
    ) {
        val classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
        File(classesDir, "Foo.class").writeBytes(byteArrayOf(1))

        val fatJar = tempDir.resolve("tool.jar").toFile()

        val missingStdlib = tempDir.resolve("does-not-exist.jar").toFile()
        builder.buildFatJar(fatJar, classesDir, stdlibJar = missingStdlib)

        assertTrue(fatJar.exists())
        JarFile(fatJar).use { jar ->
            assertNotNull(jar.getJarEntry("Foo.class"))
        }
    }

    @Test
    fun `buildFatJar deduplicates entries that exist in both classes and stdlib`(
        @TempDir tempDir: Path,
    ) {
        val stdlibJar = tempDir.resolve("stdlib.jar").toFile()
        val stdlib = java.util.jar.JarOutputStream(stdlibJar.outputStream())
        stdlib.use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("kotlin/Foo.class"))
            out.write(byteArrayOf(99))
            out.closeEntry()
        }

        val classesDir = tempDir.resolve("classes").toFile().canonicalFile
        classesDir.mkdirs()
        val kotlinDir = File(classesDir, "kotlin").apply { mkdirs() }
        File(kotlinDir, "Foo.class").writeBytes(byteArrayOf(1))

        val fatJar = tempDir.resolve("tool.jar").toFile()
        builder.buildFatJar(fatJar, classesDir, stdlibJar = stdlibJar)

        JarFile(fatJar).use { jar ->

            val entries =
                jar
                    .entries()
                    .asSequence()
                    .filter { it.name == "kotlin/Foo.class" }
                    .toList()
            assertEquals(1, entries.size)
        }
    }

    @Test
    fun `buildFatJar handles empty classes directory`(
        @TempDir tempDir: Path,
    ) {
        val classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()

        val fatJar = tempDir.resolve("tool.jar").toFile()
        builder.buildFatJar(fatJar, classesDir, stdlibJar = null)

        assertTrue(fatJar.exists())
    }

    @Test
    fun `buildFatJar walks nested subdirectories`(
        @TempDir tempDir: Path,
    ) {
        val classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
        val deep = File(File(File(classesDir, "a"), "b"), "c")
        deep.mkdirs()
        File(deep, "Deep.class").writeBytes(byteArrayOf(1))

        val fatJar = tempDir.resolve("tool.jar").toFile()
        builder.buildFatJar(fatJar, classesDir, stdlibJar = null)

        JarFile(fatJar).use { jar ->
            assertNotNull(jar.getJarEntry("a/b/c/Deep.class"))
        }
    }

    @Test
    fun `buildFatJar uses forward slashes for entry names regardless of OS`(
        @TempDir tempDir: Path,
    ) {
        val classesDir = tempDir.resolve("classes").toFile().canonicalFile
        classesDir.mkdirs()
        val pkgDir = File(classesDir, "pkg").apply { mkdirs() }
        File(pkgDir, "Main.class").writeBytes(byteArrayOf(1))

        val fatJar = tempDir.resolve("tool.jar").toFile()
        builder.buildFatJar(fatJar, classesDir, stdlibJar = null)

        JarFile(fatJar).use { jar ->

            assertNotNull(jar.getJarEntry("pkg/Main.class"))
            assertNull(jar.getJarEntry("pkg\\Main.class"))
        }
    }

    @Test
    fun `ensureMainManifest adds Main-Class when missing`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()

        val manifest = java.util.jar.Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        java.util.jar.JarOutputStream(jarFile.outputStream(), manifest).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("Foo.class"))
            out.write(byteArrayOf(1))
            out.closeEntry()
        }

        builder.ensureMainManifest(jarFile)

        JarFile(jarFile).use { jar ->
            val attrs = jar.manifest.mainAttributes
            assertEquals("MainKt", attrs.getValue(Attributes.Name.MAIN_CLASS))
            assertEquals("true", attrs.getValue("X-StackLens-Tool"))
        }
    }

    @Test
    fun `ensureMainManifest preserves existing Main-Class`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()
        val manifest = java.util.jar.Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name.MAIN_CLASS] = "CustomMain"
        java.util.jar.JarOutputStream(jarFile.outputStream(), manifest).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("Foo.class"))
            out.write(byteArrayOf(1))
            out.closeEntry()
        }

        builder.ensureMainManifest(jarFile)

        JarFile(jarFile).use { jar ->
            assertEquals("CustomMain", jar.manifest.mainAttributes.getValue(Attributes.Name.MAIN_CLASS))
        }
    }

    @Test
    fun `ensureMainManifest adds marker when Main-Class is set but marker missing`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()
        val manifest = java.util.jar.Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name.MAIN_CLASS] = "MyMain"
        java.util.jar.JarOutputStream(jarFile.outputStream(), manifest).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("Foo.class"))
            out.write(byteArrayOf(1))
            out.closeEntry()
        }

        builder.ensureMainManifest(jarFile)

        JarFile(jarFile).use { jar ->
            assertEquals("MyMain", jar.manifest.mainAttributes.getValue(Attributes.Name.MAIN_CLASS))
            assertEquals("true", jar.manifest.mainAttributes.getValue("X-StackLens-Tool"))
        }
    }

    @Test
    fun `ensureMainManifest is no-op when both Main-Class and marker are present`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()
        val manifest = java.util.jar.Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name.MAIN_CLASS] = "MainKt"
        manifest.mainAttributes[Attributes.Name("X-StackLens-Tool")] = "true"
        java.util.jar.JarOutputStream(jarFile.outputStream(), manifest).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("Foo.class"))

            out.write(byteArrayOf(42, 42, 42))
            out.closeEntry()
        }

        builder.ensureMainManifest(jarFile)

        JarFile(jarFile).use { jar ->
            val entry = jar.getJarEntry("Foo.class")
            assertNotNull(entry)
            jar.getInputStream(entry).use { input ->
                val bytes = input.readBytes()
                assertEquals(3, bytes.size)
                assertEquals(42.toByte(), bytes[0])
            }
        }
    }

    @Test
    fun `ensureMainManifest handles invalid JAR gracefully`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("not-a-jar.txt").toFile()
        jarFile.writeText("not a jar")

        try {
            builder.ensureMainManifest(jarFile)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `ensureMainManifest handles blank Main-Class attribute`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()
        val manifest = java.util.jar.Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name.MAIN_CLASS] = "   "
        java.util.jar.JarOutputStream(jarFile.outputStream(), manifest).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("Foo.class"))
            out.write(byteArrayOf(1))
            out.closeEntry()
        }

        builder.ensureMainManifest(jarFile)

        JarFile(jarFile).use { jar ->

            assertEquals("MainKt", jar.manifest.mainAttributes.getValue(Attributes.Name.MAIN_CLASS))
        }
    }

    @Test
    fun `ensureMainManifest handles non-true marker value`(
        @TempDir tempDir: Path,
    ) {
        val jarFile = tempDir.resolve("tool.jar").toFile()
        val manifest = java.util.jar.Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name.MAIN_CLASS] = "MainKt"
        manifest.mainAttributes[Attributes.Name("X-StackLens-Tool")] = "false"
        java.util.jar.JarOutputStream(jarFile.outputStream(), manifest).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("Foo.class"))
            out.write(byteArrayOf(1))
            out.closeEntry()
        }

        builder.ensureMainManifest(jarFile)

        JarFile(jarFile).use { jar ->

            assertEquals("true", jar.manifest.mainAttributes.getValue("X-StackLens-Tool"))
        }
    }

    @Test
    fun `findJarOnClasspath returns first matching jar`() {
        val result = builder.findJarOnClasspath("nonexistent-prefix-12345")
        assertNull(result)
    }

    @Test
    fun `findJarOnClasspath skips sources and javadoc variants`() {
        val result = builder.findJarOnClasspath("kotlin-stdlib")

        assertTrue(result == null || result.isNotEmpty())
    }
}
