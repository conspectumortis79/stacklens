package com.stacklens.adapter.out.knowledge.bundle

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.knowledge.KnowledgeBundleArchive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class KnowledgeBundleAdapterTest {
    private val adapter = KnowledgeBundleAdapter(ObjectMapper())

    private fun buildBundle(archives: List<KnowledgeBundleArchive>): ByteArray = adapter.write(archives)

    @Test
    fun `write propagates manifest serialisation failures and closes the zip stream`() {
        val objectWriter = mock<com.fasterxml.jackson.databind.ObjectWriter>()
        whenever(objectWriter.writeValueAsBytes(any<Any>()))
            .thenThrow(
                com.fasterxml.jackson.core
                    .JsonParseException(null, "manifest failed"),
            )
        val failingMapper = mock<ObjectMapper>()
        whenever(failingMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter)

        assertThrows<com.fasterxml.jackson.core.JsonParseException> {
            KnowledgeBundleAdapter(failingMapper).write(emptyList())
        }
    }

    @Test
    fun `write with no archives produces a manifest-only bundle`() {
        val bytes = adapter.write(emptyList())

        assertTrue(adapter.inspect(bytes).isEmpty())
    }

    @Test
    fun `inspect returns empty list when manifest is missing archives array`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("MANIFEST.json"))
            zip.write("""{"format":"x"}""".toByteArray())
            zip.closeEntry()
        }
        val entries = adapter.inspect(out.toByteArray())
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `inspect parses archives array entries`() {
        val bytes =
            buildBundle(
                listOf(
                    KnowledgeBundleArchive(
                        entryName = "bases/a.slknowledge",
                        displayName = "alpha",
                        knowledgeBaseStableId = "s1",
                        knowledgeBaseName = "Alpha",
                        knowledgeBaseDescription = "desc",
                        sourceCount = 1,
                        documentCount = 2,
                        chunkCount = 3,
                        containsVectors = true,
                        bytes = byteArrayOf(1, 2, 3),
                    ),
                ),
            )
        val entries = adapter.inspect(bytes)
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals("bases/a.slknowledge", e.entryName)
        assertEquals("alpha", e.displayName)
        assertEquals("s1", e.knowledgeBaseStableId)
        assertEquals("Alpha", e.knowledgeBaseName)
        assertEquals("desc", e.knowledgeBaseDescription)
        assertEquals(1, e.sourceCount)
        assertEquals(2, e.documentCount)
        assertEquals(3, e.chunkCount)
        assertEquals(true, e.containsVectors)
    }

    @Test
    fun `readEntry returns the matching entry bytes`() {
        val bytes =
            buildBundle(
                listOf(
                    KnowledgeBundleArchive(
                        entryName = "bases/x.slknowledge",
                        displayName = "X",
                        knowledgeBaseStableId = "s1",
                        knowledgeBaseName = "X",
                        knowledgeBaseDescription = "",
                        sourceCount = 0,
                        documentCount = 0,
                        chunkCount = 0,
                        containsVectors = false,
                        bytes = byteArrayOf(7, 8, 9, 10),
                    ),
                ),
            )
        val extracted = adapter.readEntry(bytes, "bases/x.slknowledge")
        assertNotNull(extracted)
        assertEquals(4, extracted.size)
        assertEquals(7.toByte(), extracted[0])
    }

    @Test
    fun `readEntry throws when entry is not found`() {
        val bytes = buildBundle(emptyList())
        assertThrows<IllegalStateException> {
            adapter.readEntry(bytes, "missing")
        }
    }

    @Test
    fun `inspect uses default values for missing fields`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("MANIFEST.json"))
            zip.write("""{"archives":[{"entryName":"a"}]}""".toByteArray())
            zip.closeEntry()
        }
        val entries = adapter.inspect(out.toByteArray())
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals("a", e.entryName)
        assertEquals("", e.displayName)
        assertEquals(0, e.sourceCount)
        assertEquals(false, e.containsVectors)
    }
}
