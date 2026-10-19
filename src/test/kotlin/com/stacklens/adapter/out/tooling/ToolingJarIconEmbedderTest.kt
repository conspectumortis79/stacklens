package com.stacklens.adapter.out.tooling

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ToolingJarIconEmbedderTest {
    private val embedder = ToolingJarIconEmbedder()

    @Test
    fun `embedIconInJar ignores a jar that does not exist`(
        @TempDir dir: Path,
    ) {
        val missing = File(dir.toFile(), "missing.jar")

        embedder.embedIconInJar(missing.absolutePath, "Tool", "bi-x", byteArrayOf(1))

        assertFalse(File("${missing.absolutePath}.tmp").exists())
        assertFalse(missing.exists())
    }

    @Test
    fun `embedIconInJar creates a manifest when the archive has none`(
        @TempDir dir: Path,
    ) {
        val jar = File(dir.toFile(), "plain.zip")
        ZipOutputStream(jar.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("Foo.class"))
            zip.write(byteArrayOf(0, 1, 2, 3))
            zip.closeEntry()
        }

        embedder.embedIconInJar(jar.absolutePath, "MyTool", "bi-database", byteArrayOf(0x89.toByte(), 0x50))

        JarFile(jar).use { rewritten ->
            val manifest = rewritten.manifest
            assertNotNull(manifest)
            assertEquals("1.0", manifest.mainAttributes.getValue(Attributes.Name.MANIFEST_VERSION))
            assertEquals("bi-database", manifest.mainAttributes.getValue("X-StackLens-Icon"))
            assertEquals("MyTool", manifest.mainAttributes.getValue("X-StackLens-Name"))
        }
    }

    @Test
    fun `embedIconInJar copies entries, keeps directories and appends the png`(
        @TempDir dir: Path,
    ) {
        val jar = jarFile(dir.toFile(), "tool.jar")
        val iconPng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        embedder.embedIconInJar(jar.absolutePath, "My Tool", "bi-rocket", iconPng)

        JarFile(jar).use { rewritten ->
            assertNotNull(rewritten.getJarEntry("meta/"))
            assertEquals(
                "bytecode",
                rewritten.getInputStream(rewritten.getJarEntry("Main.class")).readBytes().decodeToString(),
            )
            assertArrayEquals(iconPng, rewritten.getInputStream(rewritten.getJarEntry("META-INF/stacklens-icon.png")).readBytes())
        }
    }

    @Test
    fun `embedIconInJar replaces stale manifest attributes and a stale icon entry`(
        @TempDir dir: Path,
    ) {
        val jar = File(dir.toFile(), "stale.jar")
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name("X-StackLens-Icon")] = "bi-old"
                mainAttributes[Attributes.Name("X-StackLens-Name")] = "OldTool"
            }
        JarOutputStream(jar.outputStream(), manifest).use { out ->
            out.putNextEntry(JarEntry("META-INF/stacklens-icon.png"))
            out.write(byteArrayOf(9, 9, 9))
            out.closeEntry()
        }
        val newPng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D)

        embedder.embedIconInJar(jar.absolutePath, "NewTool", "bi-new", newPng)

        JarFile(jar).use { rewritten ->
            assertEquals("bi-new", rewritten.manifest.mainAttributes.getValue("X-StackLens-Icon"))
            assertEquals("NewTool", rewritten.manifest.mainAttributes.getValue("X-StackLens-Name"))
            assertArrayEquals(newPng, rewritten.getInputStream(rewritten.getJarEntry("META-INF/stacklens-icon.png")).readBytes())
        }
    }

    @Test
    fun `embedIconInJar keeps the archive without an icon when no png is given`(
        @TempDir dir: Path,
    ) {
        val jar = jarFile(dir.toFile(), "plain.jar")

        embedder.embedIconInJar(jar.absolutePath, "Plain", "bi-file", null)

        JarFile(jar).use { rewritten ->
            assertNull(rewritten.getJarEntry("META-INF/stacklens-icon.png"))
            assertEquals("Plain", rewritten.manifest.mainAttributes.getValue("X-StackLens-Name"))
        }
    }

    @Test
    fun `embedIconInJar drops an archive that cannot be read without leaving a temp file`(
        @TempDir dir: Path,
    ) {
        val broken = File(dir.toFile(), "broken.jar").apply { writeText("this is not a zip container") }

        embedder.embedIconInJar(broken.absolutePath, "Broken", "bi-x", byteArrayOf(9))

        assertTrue(broken.exists())
        assertFalse(File("${broken.absolutePath}.tmp").exists())
    }

    private fun jarFile(
        dir: File,
        name: String,
    ): File {
        val jar = File(dir, name)
        val manifest = Manifest().apply { mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0" }
        JarOutputStream(jar.outputStream(), manifest).use { out ->
            out.putNextEntry(JarEntry("Main.class"))
            out.write("bytecode".toByteArray())
            out.closeEntry()
            out.putNextEntry(JarEntry("meta/"))
            out.closeEntry()
        }
        return jar
    }
}
