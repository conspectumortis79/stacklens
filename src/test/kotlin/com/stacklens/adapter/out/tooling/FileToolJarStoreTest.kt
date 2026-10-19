package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.tooling.ToolJarRejectedException
import com.stacklens.application.port.out.tooling.ToolJarRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class FileToolJarStoreTest {
    private val stackLensConfig: StackLensConfigPort = mock()

    private fun storeIn(dir: Path) = FileToolJarStore(stackLensConfig.also { whenever(it.dataDir).thenReturn(dir.toString()) })

    @Test
    fun `contains reports an existing path`(
        @TempDir dir: Path,
    ) {
        val file = File(dir.toFile(), "tool.jar").apply { writeText("x") }
        assertTrue(storeIn(dir).contains(file.absolutePath))
    }

    @Test
    fun `contains reports a missing path`(
        @TempDir dir: Path,
    ) = assertFalse(storeIn(dir).contains(File(dir.toFile(), "missing.jar").absolutePath))

    @Test
    fun `deleteJar removes an existing file`() {
        val file = File.createTempFile("tool-", ".jar")
        val store = storeIn(file.parentFile.toPath())
        store.deleteJar(file.absolutePath)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteJar ignores a missing file`() {
        val missing = File.createTempFile("tool-", ".jar").apply { delete() }
        storeIn(missing.parentFile.toPath()).deleteJar(missing.absolutePath)
    }

    @Test
    fun `storeImported keeps the embedded name and icon`(
        @TempDir dir: Path,
    ) {
        val stored = storeIn(dir).storeImported("tool.jar", markerJar(name = "Embedded", icon = "bi-tools"))
        assertEquals("Embedded", stored.embeddedName)
        assertEquals("bi-tools", stored.embeddedIcon)
        assertTrue(File(stored.jarPath).isFile)
    }

    @Test
    fun `storeImported falls back to the requested filename attributes`(
        @TempDir dir: Path,
    ) {
        val stored = storeIn(dir).storeImported("tool.jar", markerJar())
        assertEquals(null, stored.embeddedName)
        assertEquals(null, stored.embeddedIcon)
    }

    @Test
    fun `storeImported writes below the tools directory with a sanitised name`(
        @TempDir dir: Path,
    ) {
        val stored = storeIn(dir).storeImported("../../evil na/me.jar", markerJar())
        val file = File(stored.jarPath)
        assertEquals(File(dir.toFile(), "tools").absolutePath, file.parent)
        assertTrue(file.name.startsWith("imported-"))
        assertFalse(file.name.contains(".."))
        assertFalse(file.name.contains("/"))
    }

    @Test
    fun `storeImported rejects bytes that are not a jar and leaves no file behind`(
        @TempDir dir: Path,
    ) {
        val store = storeIn(dir)
        val error =
            assertThrows(ToolJarRejectedException::class.java) {
                store.storeImported("tool.jar", "not a jar".toByteArray())
            }
        assertEquals(ToolJarRejection.NOT_A_JAR, error.rejection)
        assertTrue(File(dir.toFile(), "tools").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `storeImported rejects jars without the StackLens marker and leaves no file behind`(
        @TempDir dir: Path,
    ) {
        val store = storeIn(dir)
        val error =
            assertThrows(ToolJarRejectedException::class.java) {
                store.storeImported("tool.jar", jarWithoutMarker())
            }
        assertEquals(ToolJarRejection.MISSING_STACKLENS_MARKER, error.rejection)
        assertTrue(File(dir.toFile(), "tools").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `storeImported rejects a marker value that is not true`(
        @TempDir dir: Path,
    ) {
        val store = storeIn(dir)
        val error =
            assertThrows(ToolJarRejectedException::class.java) {
                store.storeImported("tool.jar", markerJar(marker = "false"))
            }
        assertEquals(ToolJarRejection.MISSING_STACKLENS_MARKER, error.rejection)
    }

    @Test
    fun `storeImported accepts a marker value with mixed case and spaces`(
        @TempDir dir: Path,
    ) {
        val stored = storeIn(dir).storeImported("tool.jar", markerJar(marker = " True "))
        assertTrue(File(stored.jarPath).isFile)
    }

    private fun markerJar(
        marker: String = "true",
        name: String? = null,
        icon: String? = null,
    ): ByteArray {
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                if (marker.isNotEmpty()) mainAttributes.putValue("X-StackLens-Tool", marker)
                name?.let { mainAttributes.putValue("X-StackLens-Name", it) }
                icon?.let { mainAttributes.putValue("X-StackLens-Icon", it) }
            }
        return jarBytes(manifest)
    }

    private fun jarWithoutMarker(): ByteArray {
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes.putValue("X-Other", "value")
            }
        return jarBytes(manifest)
    }

    private fun jarBytes(manifest: Manifest): ByteArray {
        val out = ByteArrayOutputStream()
        JarOutputStream(out, manifest).use { jar ->
            jar.putNextEntry(JarEntry("placeholder.txt"))
            jar.write("content".toByteArray())
            jar.closeEntry()
        }
        return out.toByteArray()
    }
}
