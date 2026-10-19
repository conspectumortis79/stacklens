package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

class FileToolJarStoreAcceptedJarTest {
    private val config: StackLensConfigPort = mock()

    @Test
    fun `a jar carrying the stacklens marker is stored with its embedded metadata`(
        @TempDir dir: File,
    ) {
        whenever(config.dataDir).thenReturn(dir.absolutePath)
        val store = FileToolJarStore(config)
        val jar = jarWithAttributes(dir, marker = " true ", name = "bi-box", icon = "icon-key")

        val stored = store.storeImported("../../weird name..jar", jar.readBytes())

        assertEquals("bi-box", stored.embeddedName)
        assertEquals("icon-key", stored.embeddedIcon)
        assertTrue(File(stored.jarPath).exists())
        assertTrue(File(stored.jarPath).name.startsWith("imported-"))
    }

    @Test
    fun `a jar without embedded metadata still imports with null attributes`(
        @TempDir dir: File,
    ) {
        whenever(config.dataDir).thenReturn(dir.absolutePath)
        val store = FileToolJarStore(config)
        val jar = jarWithAttributes(dir, marker = "TRUE", name = null, icon = null)

        val stored = store.storeImported("plain.jar", jar.readBytes())

        assertEquals(null, stored.embeddedName)
        assertEquals(null, stored.embeddedIcon)
    }

    private fun jarWithAttributes(
        dir: File,
        marker: String?,
        name: String?,
        icon: String?,
    ): File {
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name.MAIN_CLASS] = "MainKt"
                marker?.let { mainAttributes[Attributes.Name("X-StackLens-Tool")] = it }
                name?.let { mainAttributes[Attributes.Name("X-StackLens-Name")] = it }
                icon?.let { mainAttributes[Attributes.Name("X-StackLens-Icon")] = it }
            }
        val target = File(dir, "tool-with-attributes.jar")
        JarOutputStream(target.outputStream(), manifest).use { out ->
            out.putNextEntry(ZipEntry("MainKt.class"))
            out.write("payload".toByteArray())
            out.closeEntry()
        }
        return target
    }
}
