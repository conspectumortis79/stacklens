package com.stacklens.adapter.out.knowledge.archive

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord
import com.stacklens.application.port.out.knowledge.KnowledgeBaseSnapshot
import com.stacklens.domain.knowledge.KnowledgeArchiveMagic
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException

class EncryptedKnowledgeArchiveProbeTest {
    private val mapper = ObjectMapper()

    private val writer = EncryptedKnowledgeArchiveWriter(mapper)

    private val reader = EncryptedKnowledgeArchiveReader(mapper)

    private fun snapshot(withVector: Boolean) =
        KnowledgeBaseSnapshot(
            knowledgeBaseStableId = "kb-1",
            knowledgeBaseName = "Wissensbasis",
            knowledgeBaseDescription = "Beschreibung",
            sources =
                listOf(
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-1",
                        name = "Handbuch",
                        type = "MANUAL",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 3,
                        active = true,
                    ),
                ),
            documents =
                listOf(
                    KnowledgeArchiveRecord.Document(
                        stableId = "doc-1",
                        sourceStableId = "src-1",
                        externalId = null,
                        title = "Kapitel",
                        sourceUri = null,
                        mimeType = "text/plain",
                        plainText = "Inhalt",
                        contentHash = "hash",
                        metadata = mapOf("topic" to "start"),
                    ),
                ),
            chunks =
                listOf(
                    KnowledgeArchiveRecord.Chunk(
                        stableId = "chunk-1",
                        documentStableId = "doc-1",
                        ordinal = 0,
                        sectionTitle = "Abschnitt",
                        content = "Chunk-Inhalt",
                        contentHash = "chunk-hash",
                        tokenEstimate = 4,
                        vectorBytes = if (withVector) floatArrayToBytes(floatArrayOf(1f, 2f)) else null,
                        vectorDimension = if (withVector) 2 else null,
                    ),
                ),
            embeddingProvider = "ollama",
            embeddingModel = "nomic",
            embeddingDimension = 2,
            embeddingProfile = "ollama/nomic@2",
        )

    private fun floatArrayToBytes(values: FloatArray): ByteArray {
        val buffer =
            java.nio.ByteBuffer
                .allocate(values.size * 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun writeArchive(
        options: KnowledgeExportOptions,
        password: String = "geheim",
    ): ByteArray = writer.write(snapshot(withVector = options.includeVectors), options, password)

    private fun zipOf(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `an unencrypted archive round-trips sources documents and chunks`() {
        val archive = writeArchive(KnowledgeExportOptions(includeVectors = false, includeSourceDefinitions = true, includeEmbeddingProfile = true))

        val manifest = reader.inspect(archive, "geheim")

        assertEquals("stacklens-knowledge", manifest.format)
        assertEquals(1, manifest.version)
        assertEquals("kb-1", manifest.knowledgeBaseStableId)
        assertEquals(2, manifest.embeddingDimension)
        assertEquals("ollama/nomic@2", manifest.embeddingProfile)
        assertEquals(false, manifest.containsVectors)

        val records = reader.read(archive, "geheim").toList()

        assertEquals(3, records.size)
        assertEquals("Handbuch", (records[0] as KnowledgeArchiveRecord.Source).name)
        assertEquals(mapOf("topic" to "start"), (records[1] as KnowledgeArchiveRecord.Document).metadata)
        val chunk = records[2] as KnowledgeArchiveRecord.Chunk
        assertEquals("Chunk-Inhalt", chunk.content)
        assertNull(chunk.vectorBytes)
    }

    @Test
    fun `an encrypted archive with vectors restores the embedding floats`() {
        val archive = writeArchive(KnowledgeExportOptions(includeVectors = true, includeSourceDefinitions = true, includeEmbeddingProfile = false))

        assertTrue(String(archive.copyOfRange(0, 4), Charsets.US_ASCII) == KnowledgeArchiveMagic.SLKN)
        assertEquals(true, reader.inspect(archive, "geheim").containsVectors)

        val chunk = reader.read(archive, "geheim").filterIsInstance<KnowledgeArchiveRecord.Chunk>().single()

        assertEquals(listOf(1.0f, 2.0f), chunk.vectorBytes!!.toFloatArray().toList())
        assertEquals(2, chunk.vectorDimension)
    }

    @Test
    fun `a wrong password cannot open the encrypted archive`() {
        val archive = writeArchive(KnowledgeExportOptions(includeVectors = true))

        assertThrows(AEADBadTagException::class.java) { reader.inspect(archive, "falsch") }
    }

    @Test
    fun `an encrypted archive needs a password`() {
        val archive = writeArchive(KnowledgeExportOptions(includeVectors = true))

        listOf(null, " ").forEach { password ->
            val failure = assertThrows(IllegalArgumentException::class.java) { reader.inspect(archive, password) }
            assertEquals("Password is required to decrypt the archive", failure.message)
        }
    }

    @Test
    fun `an unknown archive version is rejected`() {
        val archive = writeArchive(KnowledgeExportOptions(includeVectors = true)).copyAndSet(4, 7)

        val failure = assertThrows(IllegalArgumentException::class.java) { reader.inspect(archive, "geheim") }

        assertEquals("Unsupported archive version: 7", failure.message)
    }

    @Test
    fun `a plain zip without any manifest reports the missing manifest`() {
        val archive = zipOf(mapOf("knowledge-base.json" to """{"stableId":"kb"}"""))

        val failure = assertThrows(IllegalStateException::class.java) { reader.inspect(archive, null) }

        assertEquals("Archive is missing manifest.json", failure.message)
    }

    @Test
    fun `a bundle with an upper case manifest is rejected with the bundle hint`() {
        val archive = zipOf(mapOf("MANIFEST.json" to """{"format":"stacklens-bundle"}"""))

        val failure = assertThrows(IllegalStateException::class.java) { reader.inspect(archive, null) }

        assertTrue(failure.message!!.startsWith("Die Datei ist ein .slbundle"), "unexpected: ${failure.message}")
    }

    @Test
    fun `a chunk that points at a missing document fails the read`() {
        val archive =
            zipOf(
                mapOf(
                    "manifest.json" to """{"format":"stacklens-knowledge","version":1}""",
                    "chunks.jsonl" to """{"stableId":"chunk-9","documentStableId":"fehlt","ordinal":0,"content":"x","contentHash":"h","tokenEstimate":1}""",
                ),
            )

        val failure = assertThrows(IllegalStateException::class.java) { reader.read(archive, null).toList() }

        assertTrue(failure.message!!.contains("references missing document fehlt"), "unexpected: ${failure.message}")
    }

    @Test
    fun `blank lines and unknown entries are skipped while reading`() {
        val archive =
            zipOf(
                mapOf(
                    "manifest.json" to """{"format":"stacklens-knowledge","version":1,"embeddingDimension":0}""",
                    "sources.jsonl" to "\n" + """{"stableId":"s1","name":"n"}
""",
                    "documents.jsonl" to """{"stableId":"d1","sourceStableId":"s1","title":"t","plainText":"p","contentHash":"h","metadata":{}}""",
                    "readme.txt" to "ignored",
                    "chunks.jsonl" to """{"stableId":"c1","documentStableId":"d1","ordinal":0,"content":"c","contentHash":"h","tokenEstimate":1,"vectorEntry":"vectors/fehlend.f32"}""",
                ),
            )

        val manifest = reader.inspect(archive, null)

        assertEquals(null, manifest.embeddingDimension)

        val records = reader.read(archive, null).toList()

        assertEquals(3, records.size)
        assertEquals("n", (records[0] as KnowledgeArchiveRecord.Source).name)
        assertEquals(true, (records[0] as KnowledgeArchiveRecord.Source).active)
        assertEquals("s1", (records[1] as KnowledgeArchiveRecord.Document).sourceStableId)
        assertNull((records[2] as KnowledgeArchiveRecord.Chunk).vectorBytes)
    }

    private fun ByteArray.copyAndSet(
        index: Int,
        value: Int,
    ): ByteArray = also { this[index] = value.toByte() }

    private fun ByteArray.toFloatArray(): FloatArray {
        val buffer =
            java.nio.ByteBuffer
                .wrap(this)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        return FloatArray(buffer.remaining() / 4) { buffer.float }
    }
}
