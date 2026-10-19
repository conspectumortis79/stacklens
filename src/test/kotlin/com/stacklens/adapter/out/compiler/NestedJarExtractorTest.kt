package com.stacklens.adapter.out.compiler

import com.stacklens.testsupport.FakeNestedJarSource
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class NestedJarExtractorTest {
    @Test
    fun `extract writes the bytes of the nested jar into a temp file`() {
        val source = FakeNestedJarSource(payloads = mapOf(NESTED_URL to byteArrayOf(1, 2, 3, 4)))

        val extracted = NestedJarExtractor(source).extract(NESTED_URL, "stacklens-test-")

        assertNotNull(extracted)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), extracted!!.readBytes())
        assertTrue(extracted.name.startsWith("stacklens-test-"))
        assertTrue(extracted.name.endsWith(".jar"))
    }

    @Test
    fun `extract returns null when the nested jar cannot be opened`() {
        val source = FakeNestedJarSource(payloads = emptyMap())

        assertNull(NestedJarExtractor(source).extract(NESTED_URL, "stacklens-test-"))
        assertEquals(listOf(NESTED_URL), source.requestedUrls)
    }

    @Test
    fun `extract returns null for an unreachable jar url when using the production source`(
        @TempDir tmp: Path,
    ) {
        val missing = File(tmp.toFile(), "absent.jar")

        assertNull(NestedJarExtractor().extract("jar:${missing.toURI()}!/MANIFEST.MF", "stacklens-test-"))
    }

    @Test
    fun `url nested jar source reads a real entry out of a jar on disk`(
        @TempDir tmp: Path,
    ) {
        val jar = manifestOnlyJar(File(tmp.toFile(), "payload.jar"))

        val bytes = UrlNestedJarSource().open("jar:${jar.toURI()}!/META-INF/MANIFEST.MF").readAllBytes()

        assertTrue(String(bytes).contains("Manifest-Version"))
    }

    @Test
    fun `extract copies a real jar entry into a non empty temp file`(
        @TempDir tmp: Path,
    ) {
        val jar = manifestOnlyJar(File(tmp.toFile(), "payload.jar"))

        val extracted = NestedJarExtractor().extract("jar:${jar.toURI()}!/META-INF/MANIFEST.MF", "stacklens-real-")

        assertNotNull(extracted)
        assertTrue(extracted!!.length() > 0L)
    }

    @Test
    fun `temp jar writer keeps the given prefix and jar suffix`(
        @TempDir tmp: Path,
    ) {
        val seed = File(tmp.toFile(), "seed.bin")
        seed.writeBytes(byteArrayOf(9, 8, 7))

        val written = TempJarWriter().write("stacklens-writer-", seed.inputStream())

        assertTrue(written.name.startsWith("stacklens-writer-"))
        assertTrue(written.name.endsWith(".jar"))
        assertArrayEquals(byteArrayOf(9, 8, 7), written.readBytes())
    }

    private fun manifestOnlyJar(target: File): File {
        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            }
        JarOutputStream(FileOutputStream(target), manifest).use { }
        return target
    }

    private companion object {
        const val NESTED_URL = "jar:file:/stacklens/libs/kotlin-compiler-embeddable-2.3.20.jar!/"
    }
}
