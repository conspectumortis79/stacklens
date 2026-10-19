package com.stacklens.adapter.out.tooling

import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.tooling.ToolJarRejectedException
import com.stacklens.application.port.out.tooling.ToolJarRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileToolJarStoreManifestBranchTest {
    private val config: StackLensConfigPort = mock()

    @TempDir
    lateinit var dir: Path

    @Test
    fun `an archive without any manifest is rejected for the missing marker`() {
        val store = FileToolJarStore(config.also { whenever(it.dataDir).thenReturn(dir.toString()) })

        val failure = assertThrows(ToolJarRejectedException::class.java) { store.storeImported("tool.jar", zipWithoutManifest()) }

        assertEquals(ToolJarRejection.MISSING_STACKLENS_MARKER, failure.rejection)
        val leftOver = File(dir.toFile(), "tools").listFiles()?.filter { it.name.startsWith("imported-") }.orEmpty()
        assertEquals(0, leftOver.size)
    }

    private fun zipWithoutManifest(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("payload.txt"))
            zip.write("payload".toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }
}
