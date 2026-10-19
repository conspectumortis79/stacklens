package com.stacklens.application.service.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.knowledge.archive.EncryptedKnowledgeArchiveWriter
import com.stacklens.adapter.out.knowledge.bundle.KnowledgeBundleAdapter
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord
import com.stacklens.application.port.out.knowledge.KnowledgeBaseSnapshot
import com.stacklens.application.port.out.knowledge.KnowledgeBundleArchive
import com.stacklens.domain.knowledge.KnowledgeArchiveMagic
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnowledgeArchiveFormatDetectorTest {
    private val objectMapper = ObjectMapper()
    private val writer = EncryptedKnowledgeArchiveWriter(objectMapper)
    private val bundleAdapter = KnowledgeBundleAdapter(objectMapper)

    @Test
    fun `single encrypted archive is detected by SLKN magic`() {
        val bytes = singleArchiveBytes("Single")
        assertEquals(
            KnowledgeArchiveFormatDetector.Kind.SINGLE,
            KnowledgeArchiveFormatDetector.detect(bytes),
        )
    }

    @Test
    fun `bundle is detected by ZIP magic plus MANIFEST_json entry`() {
        val bytes = bundleBytes()
        assertEquals(
            KnowledgeArchiveFormatDetector.Kind.BUNDLE,
            KnowledgeArchiveFormatDetector.detect(bytes),
        )
    }

    @Test
    fun `plain zip without MANIFEST_json is rejected as UNKNOWN`() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("hello.txt"))
            zip.write("hi".toByteArray())
            zip.closeEntry()
        }
        assertEquals(
            KnowledgeArchiveFormatDetector.Kind.UNKNOWN,
            KnowledgeArchiveFormatDetector.detect(out.toByteArray()),
        )
    }

    @Test
    fun `random bytes shorter than the magic are rejected as UNKNOWN`() {
        assertEquals(
            KnowledgeArchiveFormatDetector.Kind.UNKNOWN,
            KnowledgeArchiveFormatDetector.detect(byteArrayOf(1, 2)),
        )
    }

    @Test
    fun `detector exposes the SLKN magic for cross-checking`() {
        val bytes = singleArchiveBytes("x")
        assertEquals("SLKN", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertTrue(
            KnowledgeArchiveMagic.SLKN
                .toByteArray(Charsets.US_ASCII)
                .contentEquals(byteArrayOf(0x53, 0x4C, 0x4B, 0x4E)),
        )
    }

    private fun singleArchiveBytes(name: String): ByteArray {
        val snapshot = sampleSnapshot(stableId = "b-1", name = name)
        return writer.write(snapshot, KnowledgeExportOptions(), "pw")
    }

    private fun bundleBytes(): ByteArray {
        val snapshot = sampleSnapshot(stableId = "b-1", name = "Sample")
        val innerBytes = writer.write(snapshot, KnowledgeExportOptions(), "pw")
        return bundleAdapter.write(
            listOf(
                KnowledgeBundleArchive(
                    entryName = "bases/sample.slknowledge",
                    displayName = "Sample",
                    knowledgeBaseStableId = "b-1",
                    knowledgeBaseName = "Sample",
                    knowledgeBaseDescription = "",
                    sourceCount = 0,
                    documentCount = 0,
                    chunkCount = 0,
                    containsVectors = false,
                    bytes = innerBytes,
                ),
            ),
        )
    }

    private fun sampleSnapshot(
        stableId: String,
        name: String,
    ): KnowledgeBaseSnapshot =
        KnowledgeBaseSnapshot(
            knowledgeBaseStableId = stableId,
            knowledgeBaseName = name,
            knowledgeBaseDescription = "",
            sources = emptyList<KnowledgeArchiveRecord.Source>(),
            documents = emptyList<KnowledgeArchiveRecord.Document>(),
            chunks = emptyList<KnowledgeArchiveRecord.Chunk>(),
            embeddingProvider = null,
            embeddingModel = null,
            embeddingDimension = null,
            embeddingProfile = null,
        )
}
