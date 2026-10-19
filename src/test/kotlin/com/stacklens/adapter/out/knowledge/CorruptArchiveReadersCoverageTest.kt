package com.stacklens.adapter.out.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.knowledge.archive.EncryptedKnowledgeArchiveReader
import com.stacklens.adapter.out.knowledge.bundle.KnowledgeBundleAdapter
import com.stacklens.testsupport.CorruptZips
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.zip.ZipException

class CorruptArchiveReadersCoverageTest {
    private val mapper = ObjectMapper()
    private val reader = EncryptedKnowledgeArchiveReader(mapper)
    private val bundle = KnowledgeBundleAdapter(mapper)

    private val unreadable = CorruptZips.unreadableEntry("manifest.json")

    @Test
    fun `inspecting a single archive with broken entry headers fails inside the zip reader`() {
        assertThrows(ZipException::class.java) { reader.inspect(unreadable, null) }
    }

    @Test
    fun `reading a single archive with broken entry headers fails inside the zip reader`() {
        assertThrows(ZipException::class.java) { reader.read(unreadable, null).toList() }
    }

    @Test
    fun `the vector pass fails when a stored vector cannot be decompressed`() {
        val payload =
            CorruptZips.zipWithCorruptedData(
                entries =
                    mapOf(
                        "manifest.json" to """{"format":"stacklens-knowledge"}""".toByteArray(),
                        "vectors/blob.bin" to "abcdefgh".repeat(400).toByteArray(),
                    ),
                corruptEntry = "vectors/blob.bin",
            )

        assertThrows(java.util.zip.ZipException::class.java) { reader.read(payload, null).toList() }
    }

    @Test
    fun `inspecting a bundle with broken entry headers fails inside the zip reader`() {
        assertThrows(ZipException::class.java) { bundle.inspect(unreadable) }
    }

    @Test
    fun `reading one bundle entry with broken entry headers fails inside the zip reader`() {
        assertThrows(ZipException::class.java) { bundle.readEntry(unreadable, "MANIFEST.json") }
    }
}
