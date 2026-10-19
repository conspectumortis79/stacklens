package com.stacklens.application.service.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.knowledge.bundle.KnowledgeBundleAdapter
import com.stacklens.application.port.out.knowledge.KnowledgeBundleArchive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnowledgeBundleRoundtripTest {
    private val objectMapper = ObjectMapper()
    private val adapter = KnowledgeBundleAdapter(objectMapper)

    @Test
    fun `bundle roundtrip preserves every contained archive`() {
        val archiveA =
            sampleArchive(
                entryName = "bases/base-a.slknowledge",
                displayName = "Base A",
                stableId = "b-A",
                bytes = byteArrayOf(0x53, 0x4C, 0x4B, 0x4E, 1, 2, 3, 4),
            )
        val archiveB =
            sampleArchive(
                entryName = "bases/base-b.slknowledge",
                displayName = "Base B",
                stableId = "b-B",
                bytes = byteArrayOf(0x53, 0x4C, 0x4B, 0x4E, 5, 6, 7, 8),
            )

        val bundleBytes = adapter.write(listOf(archiveA, archiveB))

        assertEquals(0x50, bundleBytes[0].toInt() and 0xFF)
        assertEquals(0x4B, bundleBytes[1].toInt() and 0xFF)
        assertEquals(0x03, bundleBytes[2].toInt() and 0xFF)
        assertEquals(0x04, bundleBytes[3].toInt() and 0xFF)

        val entries = adapter.inspect(bundleBytes)
        assertEquals(2, entries.size)
        assertEquals("Base A", entries[0].displayName)
        assertEquals("Base B", entries[1].displayName)
        assertEquals("b-A", entries[0].knowledgeBaseStableId)
        assertEquals("b-B", entries[1].knowledgeBaseStableId)

        assertTrue(archiveA.bytes.contentEquals(adapter.readEntry(bundleBytes, archiveA.entryName)))
        assertTrue(archiveB.bytes.contentEquals(adapter.readEntry(bundleBytes, archiveB.entryName)))
    }

    @Test
    fun `bundle does not contain the legacy KB-END separator`() {
        val archive =
            sampleArchive(
                entryName = "bases/only.slknowledge",
                displayName = "Only",
                stableId = "b-1",
                bytes = byteArrayOf(0x53, 0x4C, 0x4B, 0x4E, 9, 9, 9),
            )
        val bundleBytes = adapter.write(listOf(archive))
        val text = String(bundleBytes, Charsets.UTF_8)
        assertFalse(text.contains("---KB-END---"), "bundle must not embed the legacy separator")
        assertFalse(text.contains("KB-END"), "bundle must not embed any KB-END token")
    }

    @Test
    fun `inspect fails when bundle is missing manifest`() {
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("garbage.txt"))
            zip.write("noop".toByteArray())
            zip.closeEntry()
        }
        val ex = runCatching { adapter.inspect(baos.toByteArray()) }
        assertTrue(
            ex.isFailure,
            "inspect must fail when the bundle is missing MANIFEST.json",
        )
        assertNotNull(ex.exceptionOrNull()?.message)
    }

    private fun sampleArchive(
        entryName: String,
        displayName: String,
        stableId: String,
        bytes: ByteArray,
    ): KnowledgeBundleArchive =
        KnowledgeBundleArchive(
            entryName = entryName,
            displayName = displayName,
            knowledgeBaseStableId = stableId,
            knowledgeBaseName = displayName,
            knowledgeBaseDescription = "desc-$stableId",
            sourceCount = 1,
            documentCount = 2,
            chunkCount = 3,
            containsVectors = false,
            bytes = bytes,
        )
}
