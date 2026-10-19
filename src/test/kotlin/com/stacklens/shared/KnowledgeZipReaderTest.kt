package com.stacklens.shared

import com.stacklens.shared.readCappedBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class KnowledgeZipReaderTest {
    private val payload: ByteArray =
        zipOf(
            linkedMapOf(
                "manifest.json" to """{"format":"stacklens-knowledge"}""",
                "sources.jsonl" to "line one\n\nline two\n",
                "vectors/a.f32" to "vector-a",
                "vectors/b.f32" to "vector-b",
                "chunks.jsonl" to "chunk",
            ),
        )

    @Test
    fun `findEntry returns the requested entry only`() {
        assertEquals("""{"format":"stacklens-knowledge"}""", KnowledgeZipReader.findEntry(payload, "manifest.json")?.toString(Charsets.UTF_8))
        assertNull(KnowledgeZipReader.findEntry(payload, "missing.json"))
    }

    @Test
    fun `readEntryAsString decodes utf eight`() {
        assertEquals("chunk", KnowledgeZipReader.readEntryAsString(payload, "chunks.jsonl"))
        assertNull(KnowledgeZipReader.readEntryAsString(payload, "nope"))
    }

    @Test
    fun `sources entry keeps its raw line structure`() {
        val raw = KnowledgeZipReader.readEntryAsString(payload, "sources.jsonl")

        assertEquals("line one\n\nline two\n", raw)
    }

    @Test
    fun `entries collects the named entries plus a prefix in one pass`() {
        val entries =
            KnowledgeZipReader.entries(
                payload,
                names = setOf("chunks.jsonl", "manifest.json"),
                prefix = KnowledgeZipReader.VECTORS_PREFIX,
            )

        assertEquals(setOf("manifest.json", "chunks.jsonl", "vectors/a.f32", "vectors/b.f32"), entries.keys)
        assertEquals("vector-b", entries["vectors/b.f32"]?.toString(Charsets.UTF_8))
        assertTrue(entries.none { it.key == "sources.jsonl" })
    }

    @Test
    fun `entries without names or prefix return an empty map`() {
        assertEquals(emptyMap<String, ByteArray>(), KnowledgeZipReader.entries(payload))
    }

    @Test
    fun `readCappedBytes copies a stream and refuses oversized payloads`() {
        val bytes = ByteArray(20_000) { (it % 251).toByte() }

        assertEquals(bytes.toList(), ByteArrayInputStream(bytes).readCappedBytes().toList())

        val failure =
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
                ByteArrayInputStream(bytes).readCappedBytes(maxSize = 1000)
            }

        assertEquals("Archive exceeds maximum supported size", failure.message)
    }

    private fun zipOf(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
