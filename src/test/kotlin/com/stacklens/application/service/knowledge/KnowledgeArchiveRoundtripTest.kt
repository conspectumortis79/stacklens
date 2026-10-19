package com.stacklens.application.service.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.knowledge.archive.EncryptedKnowledgeArchiveReader
import com.stacklens.adapter.out.knowledge.archive.EncryptedKnowledgeArchiveWriter
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnowledgeArchiveRoundtripTest {
    private val objectMapper = ObjectMapper()
    private val reader = EncryptedKnowledgeArchiveReader(objectMapper)
    private val writer = EncryptedKnowledgeArchiveWriter(objectMapper)

    @Test
    fun `archive roundtrip preserves source, document, and chunk`() {
        val snapshot =
            com.stacklens.application.port.out.knowledge.KnowledgeBaseSnapshot(
                knowledgeBaseStableId = "b-1",
                knowledgeBaseName = "Test Base",
                knowledgeBaseDescription = "Description",
                sources =
                    listOf(
                        KnowledgeArchiveRecord.Source(
                            stableId = "s-1",
                            name = "Manual note",
                            type = "MANUAL",
                            rootUrl = null,
                            externalConfigName = null,
                            externalReference = null,
                            includeChildren = false,
                            includeComments = false,
                            maxDepth = 10,
                            active = true,
                        ),
                    ),
                documents =
                    listOf(
                        KnowledgeArchiveRecord.Document(
                            stableId = "d-1",
                            sourceStableId = "s-1",
                            externalId = null,
                            title = "doc",
                            sourceUri = null,
                            mimeType = "text/plain",
                            plainText = "hello world",
                            contentHash = "abc",
                            metadata = mapOf("k" to "v"),
                        ),
                    ),
                chunks =
                    listOf(
                        KnowledgeArchiveRecord.Chunk(
                            stableId = "c-1",
                            documentStableId = "d-1",
                            ordinal = 0,
                            sectionTitle = null,
                            content = "hello world",
                            contentHash = "abc",
                            tokenEstimate = 2,
                            vectorBytes = null,
                            vectorDimension = null,
                        ),
                    ),
                embeddingProvider = "OLLAMA",
                embeddingModel = "nomic-embed-text",
                embeddingDimension = 768,
                embeddingProfile = "ollama|http://localhost:11434|nomic-embed-text|768|chunker-v1|1600|200",
            )
        val bytes =
            writer.write(
                snapshot,
                com.stacklens.domain.model.knowledge
                    .KnowledgeExportOptions(),
                "pw1234",
            )
        val manifest = reader.inspect(bytes, "pw1234")
        assertEquals("Test Base", manifest.knowledgeBaseName)
        assertEquals(1, manifest.sourceCount)
        assertEquals(1, manifest.documentCount)
        assertEquals(1, manifest.chunkCount)
        assertEquals("OLLAMA", manifest.embeddingProvider)
        val records = reader.read(bytes, "pw1234").toList()
        val source = records.filterIsInstance<KnowledgeArchiveRecord.Source>().single()
        val document = records.filterIsInstance<KnowledgeArchiveRecord.Document>().single()
        val chunk = records.filterIsInstance<KnowledgeArchiveRecord.Chunk>().single()
        assertEquals("s-1", source.stableId)
        assertEquals("d-1", document.stableId)
        assertEquals("s-1", document.sourceStableId)
        assertEquals("c-1", chunk.stableId)
        assertEquals("d-1", chunk.documentStableId)
        assertEquals("hello world", document.plainText)
    }

    @Test
    fun `inspect fails on wrong password`() {
        val snapshot =
            com.stacklens.application.port.out.knowledge.KnowledgeBaseSnapshot(
                knowledgeBaseStableId = "b-1",
                knowledgeBaseName = "x",
                knowledgeBaseDescription = "",
                sources = emptyList(),
                documents = emptyList(),
                chunks = emptyList(),
                embeddingProvider = null,
                embeddingModel = null,
                embeddingDimension = null,
                embeddingProfile = null,
            )
        val bytes =
            writer.write(
                snapshot,
                com.stacklens.domain.model.knowledge
                    .KnowledgeExportOptions(),
                "right",
            )
        val thrown = runCatching { reader.inspect(bytes, "wrong") }
        assertTrue(thrown.isFailure, "expected wrong password to fail inspection")
    }

    @org.junit.jupiter.api.Test
    fun `archive roundtrip with multiple chunks that have vectors`() {
        val snapshot =
            com.stacklens.application.port.out.knowledge.KnowledgeBaseSnapshot(
                knowledgeBaseStableId = "b-2",
                knowledgeBaseName = "With vectors",
                knowledgeBaseDescription = "",
                sources = emptyList(),
                documents =
                    listOf(
                        com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord.Document(
                            stableId = "d-1",
                            sourceStableId = "s-1",
                            externalId = null,
                            title = "doc",
                            sourceUri = null,
                            mimeType = "text/plain",
                            plainText = "the document",
                            contentHash = "hd",
                            metadata = emptyMap(),
                        ),
                    ),
                chunks =
                    listOf(
                        com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord.Chunk(
                            stableId = "c-1",
                            documentStableId = "d-1",
                            ordinal = 0,
                            sectionTitle = null,
                            content = "first chunk",
                            contentHash = "h1",
                            tokenEstimate = 1,
                            vectorBytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7),
                            vectorDimension = 2,
                        ),
                        com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord.Chunk(
                            stableId = "c-2",
                            documentStableId = "d-1",
                            ordinal = 1,
                            sectionTitle = null,
                            content = "second chunk",
                            contentHash = "h2",
                            tokenEstimate = 1,
                            vectorBytes = byteArrayOf(8, 9, 10, 11, 12, 13, 14, 15),
                            vectorDimension = 2,
                        ),
                        com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord.Chunk(
                            stableId = "c-3",
                            documentStableId = "d-1",
                            ordinal = 2,
                            sectionTitle = null,
                            content = "third chunk without vector",
                            contentHash = "h3",
                            tokenEstimate = 1,
                            vectorBytes = null,
                            vectorDimension = null,
                        ),
                    ),
                embeddingProvider = "OLLAMA",
                embeddingModel = "nomic-embed-text",
                embeddingDimension = 2,
                embeddingProfile = "test",
            )
        val bytes =
            writer.write(
                snapshot,
                com.stacklens.domain.model.knowledge
                    .KnowledgeExportOptions(),
                "right",
            )
        val manifest = reader.inspect(bytes, "right")
        assertEquals(3, manifest.chunkCount)
        assertEquals("OLLAMA", manifest.embeddingProvider)
        val records = reader.read(bytes, "right").toList()
        val chunks = records.filterIsInstance<com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord.Chunk>()
        assertEquals(3, chunks.size)
        assertEquals(listOf("c-1", "c-2", "c-3"), chunks.map { it.stableId })
        assertEquals(listOf("first chunk", "second chunk", "third chunk without vector"), chunks.map { it.content })
        assertEquals(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7).toList(), chunks[0].vectorBytes?.toList())
        assertEquals(byteArrayOf(8, 9, 10, 11, 12, 13, 14, 15).toList(), chunks[1].vectorBytes?.toList())
        assertNull(chunks[2].vectorBytes)
    }

    @Test
    fun `legacy export-all format (with KB-END separator) cannot be imported`() {
        val snapshotA = sampleSnapshot(stableId = "b-A", name = "Base A")
        val snapshotB = sampleSnapshot(stableId = "b-B", name = "Base B")
        val bytes =
            writer.write(snapshotA, options(), "pw1234") +
                "\n---KB-END---\n".toByteArray(Charsets.UTF_8) +
                writer.write(snapshotB, options(), "pw1234")
        assertEquals("SLKN", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
        val ex =
            assertThrows(javax.crypto.AEADBadTagException::class.java) {
                reader.inspect(bytes, "pw1234")
            }
        assertNotNull(ex.message)
    }

    @Test
    fun `single-archive reader does not misinterpret a bundle as a plain zip`() {
        val bundleAdapter =
            com.stacklens.adapter.out.knowledge.bundle.KnowledgeBundleAdapter(
                objectMapper,
            )
        val snapshot =
            com.stacklens.application.port.out.knowledge.KnowledgeBaseSnapshot(
                knowledgeBaseStableId = "b-bundle",
                knowledgeBaseName = "Bundle base",
                knowledgeBaseDescription = "",
                sources = emptyList(),
                documents = emptyList(),
                chunks = emptyList(),
                embeddingProvider = null,
                embeddingModel = null,
                embeddingDimension = null,
                embeddingProfile = null,
            )
        val innerBytes = writer.write(snapshot, options(), "pw")
        val bundleBytes =
            bundleAdapter.write(
                listOf(
                    com.stacklens.application.port.out.knowledge.KnowledgeBundleArchive(
                        entryName = "bases/bundle-base.slknowledge",
                        displayName = "Bundle base",
                        knowledgeBaseStableId = "b-bundle",
                        knowledgeBaseName = "Bundle base",
                        knowledgeBaseDescription = "",
                        sourceCount = 0,
                        documentCount = 0,
                        chunkCount = 0,
                        containsVectors = false,
                        bytes = innerBytes,
                    ),
                ),
            )
        assertEquals(0x50, bundleBytes[0].toInt() and 0xFF)
        assertEquals(0x4B, bundleBytes[1].toInt() and 0xFF)
        val ex =
            assertThrows(IllegalStateException::class.java) {
                reader.inspect(bundleBytes, "pw")
            }
        assertTrue(
            ex.message!!.contains("bundle", ignoreCase = true),
            "error must hint that a bundle was misdetected, got: ${ex.message}",
        )
    }

    private fun options() =
        com.stacklens.domain.model.knowledge
            .KnowledgeExportOptions()

    private fun sampleSnapshot(
        stableId: String,
        name: String,
    ) = com.stacklens.application.port.out.knowledge.KnowledgeBaseSnapshot(
        knowledgeBaseStableId = stableId,
        knowledgeBaseName = name,
        knowledgeBaseDescription = "",
        sources = emptyList(),
        documents = emptyList(),
        chunks = emptyList(),
        embeddingProvider = null,
        embeddingModel = null,
        embeddingDimension = null,
        embeddingProfile = null,
    )
}
