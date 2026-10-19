package com.stacklens.application.service.knowledge

import com.stacklens.domain.knowledge.KnowledgeArchiveMagic
import com.stacklens.testsupport.TestJars
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class KnowledgeArchiveFormatDetectorBundleTest {
    @Test
    fun `a zip that carries a manifest entry is detected as a bundle`(
        @TempDir dir: File,
    ) {
        val zip = TestJars.zip(File(dir, "bundle.zip"), entries = mapOf("MANIFEST.json" to TestJars.bytesOf("{}")))

        assertEquals(KnowledgeArchiveFormatDetector.Kind.BUNDLE, KnowledgeArchiveFormatDetector.detect(zip.readBytes()))
    }

    @Test
    fun `a zip without a manifest entry stays unknown even when other entries exist`(
        @TempDir dir: File,
    ) {
        val zip =
            TestJars.zip(
                File(dir, "other.zip"),
                entries = mapOf("README.md" to TestJars.bytesOf("hi"), "knowledge.slkn" to TestJars.bytesOf("x")),
            )

        assertEquals(KnowledgeArchiveFormatDetector.Kind.UNKNOWN, KnowledgeArchiveFormatDetector.detect(zip.readBytes()))
    }

    @Test
    fun `an empty zip is reported as unknown`() {
        val bytes = ByteArrayOutputStream().also { stream -> ZipOutputStream(stream).use { } }.toByteArray()

        assertEquals(KnowledgeArchiveFormatDetector.Kind.UNKNOWN, KnowledgeArchiveFormatDetector.detect(bytes))
    }

    @Test
    fun `the single archive magic wins over the zip magic`() {
        val single = KnowledgeArchiveMagic.SLKN.toByteArray(Charsets.US_ASCII) + byteArrayOf(1, 2, 3, 4)

        assertEquals(KnowledgeArchiveFormatDetector.Kind.SINGLE, KnowledgeArchiveFormatDetector.detect(single))
        assertEquals(KnowledgeArchiveFormatDetector.Kind.UNKNOWN, KnowledgeArchiveFormatDetector.detect(byteArrayOf(1)))
    }

    @Test
    fun `a manifest entry that only appears after other entries is still found`() {
        val bytes =
            ByteArrayOutputStream()
                .also { stream ->
                    ZipOutputStream(stream).use { zip ->
                        listOf("first.txt", "second.txt", "MANIFEST.json").forEach { name ->
                            zip.putNextEntry(ZipEntry(name))
                            zip.write(name.toByteArray())
                            zip.closeEntry()
                        }
                    }
                }.toByteArray()

        assertEquals(KnowledgeArchiveFormatDetector.Kind.BUNDLE, KnowledgeArchiveFormatDetector.detect(bytes))
    }
}
