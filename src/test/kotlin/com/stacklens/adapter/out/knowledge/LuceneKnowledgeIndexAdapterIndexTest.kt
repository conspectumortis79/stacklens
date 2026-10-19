package com.stacklens.adapter.out.knowledge

import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import com.stacklens.testsupport.lucene.UnreadableTermDictionaryCodec
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.KnnFloatVectorField
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.store.FSDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LuceneKnowledgeIndexAdapterIndexTest {
    private val hashing = DefaultKnowledgeHashing()

    private val query = floatArrayOf(1f, 0f, 0f, 0f)

    private fun tempDir(): Path = Files.createTempDirectory("stacklens-lucene-adapter")

    private fun adapterAt(dir: Path): LuceneKnowledgeIndexAdapter =
        LuceneKnowledgeIndexAdapter(
            hashing = hashing,
            indexDir = dir.toString(),
        )

    private fun chunk(
        chunkId: Long,
        documentId: Long = chunkId,
        embedding: FloatArray? = query,
        ticketKey: String? = null,
        sprintReference: String? = null,
        sectionTitle: String? = null,
        sourceUri: String? = null,
        citation: String = "KB:document-$documentId#chunk-$chunkId",
    ): IndexedKnowledgeChunk =
        IndexedKnowledgeChunk(
            chunkId = chunkId,
            documentId = documentId,
            knowledgeBaseId = KB,
            title = "title $chunkId",
            sectionTitle = sectionTitle,
            content = "content $chunkId",
            sourceType = "MANUAL",
            sourceUri = sourceUri,
            embedding = embedding,
            score = 0.0,
            citation = citation,
            ticketKey = ticketKey,
            sprintReference = sprintReference,
        )

    private fun rawDocument(
        chunkId: String? = "1",
        documentId: String? = "2",
        content: String? = "body",
        title: String? = "title",
        sourceType: String? = "MANUAL",
        sectionTitle: String? = null,
        sourceUri: String? = null,
        ticketKey: String? = null,
        sprintReference: String? = null,
        ordinal: Int = 0,
        storedVector: ByteArray? = hashing.floatArrayToBytes(query),
        withIndexedVector: Boolean = true,
    ): Document {
        val doc = Document()
        doc.add(StringField("active", "true", Field.Store.YES))
        chunkId?.let { doc.add(StringField("chunkId", it, Field.Store.YES)) }
        documentId?.let { doc.add(StringField("documentId", it, Field.Store.YES)) }
        sourceType?.let { doc.add(StringField("sourceType", it, Field.Store.YES)) }
        ticketKey?.let { doc.add(StringField("ticketKey", it, Field.Store.YES)) }
        sprintReference?.let { doc.add(StringField("sprintReference", it, Field.Store.YES)) }
        sectionTitle?.let { doc.add(TextField("sectionTitle", it, Field.Store.YES)) }
        title?.let { doc.add(TextField("title", it, Field.Store.YES)) }
        content?.let { doc.add(TextField("content", it, Field.Store.YES)) }
        doc.add(StoredField("ordinal", ordinal.toLong()))
        sourceUri?.let { doc.add(StoredField("sourceUri", it)) }
        storedVector?.let { doc.add(StoredField("vector", it)) }
        if (withIndexedVector) {
            doc.add(KnnFloatVectorField("vector", query, VectorSimilarityFunction.COSINE))
        }
        return doc
    }

    private fun writeRawIndex(
        dir: Path,
        documents: List<Document>,
        codec: org.apache.lucene.codecs.Codec? = null,
    ) {
        Files.createDirectories(dir)
        FSDirectory.open(dir).use { store ->
            val config = IndexWriterConfig(StandardAnalyzer()).setOpenMode(IndexWriterConfig.OpenMode.CREATE)
            codec?.let { config.setCodec(it) }
            IndexWriter(store, config).use { writer ->
                documents.forEach(writer::addDocument)
                writer.commit()
            }
        }
    }

    @Test fun `searchByVector ignores empty query vectors and non positive limits`() {
        val adapter = adapterAt(tempDir())
        assertTrue(adapter.searchByVector(KB, FloatArray(0), 5).isEmpty())
        assertTrue(adapter.searchByVector(KB, query, 0).isEmpty())
        assertTrue(adapter.searchByVector(KB, query, -1).isEmpty())
    }

    @Test fun `both searches and the generation lookup ignore an unindexed knowledge base`() {
        val adapter = adapterAt(tempDir())
        assertTrue(adapter.searchByVector(404L, query, 5).isEmpty())
        assertTrue(adapter.searchByTicketKey(404L, "KEY-1").isEmpty())
        assertNull(adapter.activeGeneration(404L))
        adapter.removeDocument(404L, 1L)
    }

    @Test fun `searchByTicketKey ignores blank keys`() {
        val adapter = adapterAt(tempDir())
        assertTrue(adapter.searchByTicketKey(KB, "   ").isEmpty())
        assertTrue(adapter.searchByTicketKey(KB, "").isEmpty())
    }

    @Test fun `searches and deletion ignore a generation directory that vanished`() {
        val dir = tempDir()
        val base = Files.createDirectories(dir.resolve("base-$KB"))
        Files.createSymbolicLink(base.resolve("g0"), dir.resolve("already-deleted-generation"))
        val adapter = adapterAt(dir)

        assertNull(adapter.activeGeneration(KB))
        assertTrue(adapter.searchByVector(KB, query, 5).isEmpty())
        assertTrue(adapter.searchByTicketKey(KB, "KEY-1").isEmpty())
        adapter.removeDocument(KB, 1L)
    }

    @Test fun `activeGeneration returns null for an empty base directory without creating g0`() {
        val dir = tempDir()
        Files.createDirectories(dir.resolve("base-$KB"))
        val adapter = adapterAt(dir)

        assertNull(adapter.activeGeneration(KB))
        assertFalse(Files.exists(dir.resolve("base-$KB").resolve("g0")))
    }

    @Test fun `generation lookup returns nothing when the base holds only loose files`() {
        val dir = tempDir()
        val base = Files.createDirectories(dir.resolve("base-$KB"))
        Files.writeString(base.resolve("stray.txt"), "stray")
        val adapter = adapterAt(dir)

        assertNull(adapter.activeGeneration(KB))
        assertTrue(adapter.searchByVector(KB, query, 5).isEmpty())
        assertTrue(adapter.searchByTicketKey(KB, "KEY-1").isEmpty())
    }

    @Test fun `searchByVector maps complete documents and skips unusable ones`() {
        val dir = tempDir()
        writeRawIndex(
            dir.resolve("base-$KB").resolve("g0"),
            listOf(
                rawDocument(chunkId = "10", content = null),
                rawDocument(chunkId = "not-a-number"),
                rawDocument(chunkId = "12", documentId = null),
                rawDocument(chunkId = "13", documentId = "3", title = null, sourceType = null, storedVector = null),
                rawDocument(
                    chunkId = "14",
                    documentId = "4",
                    content = "full body",
                    title = "full title",
                    sourceType = "CONFLUENCE",
                    sectionTitle = "full section",
                    sourceUri = "https://example.test/page",
                    ticketKey = "KEY-14",
                    sprintReference = "sprint-9",
                    ordinal = 7,
                ),
            ),
        )
        val adapter = adapterAt(dir)

        val hits = adapter.searchByVector(KB, query, 10)

        assertEquals(listOf(13L, 14L), hits.map { it.chunkId })
        val defaults = hits.first()
        assertEquals("", defaults.title)
        assertEquals("MANUAL", defaults.sourceType)
        assertNull(defaults.embedding)
        val complete = hits.last()
        assertEquals("full section", complete.sectionTitle)
        assertEquals("https://example.test/page", complete.sourceUri)
        assertEquals("KEY-14", complete.ticketKey)
        assertEquals("sprint-9", complete.sprintReference)
        assertEquals("KB:document-4#chunk-7", complete.citation)
        assertNotNull(complete.embedding)
        assertEquals(query.toList(), complete.embedding?.toList())
    }

    @Test fun `searchByVector collects at most the requested number of hits`() {
        val dir = tempDir()
        writeRawIndex(
            dir.resolve("base-$KB").resolve("g0"),
            (1L..4L).map { rawDocument(chunkId = "$it", documentId = "$it", content = "body $it") },
        )
        val adapter = adapterAt(dir)

        assertEquals(2, adapter.searchByVector(KB, query, 2).size)
        assertEquals(4, adapter.searchByVector(KB, query, 8).size)
    }

    @Test fun `searchByVector propagates a query dimension mismatch as an error`() {
        val adapter = adapterAt(tempDir())
        adapter.replaceAllChunks(KB, "g0", listOf(chunk(1)))

        assertThrows(IllegalStateException::class.java) {
            adapter.searchByVector(KB, floatArrayOf(1f, 0f), 3)
        }
        assertThrows(IllegalStateException::class.java) {
            adapter.searchByVector(KB, floatArrayOf(1f, 0f, 0f, 0f, 0f), 3)
        }
    }

    @Test fun `searchByTicketKey maps complete documents and skips unusable ones`() {
        val dir = tempDir()
        writeRawIndex(
            dir.resolve("base-$KB").resolve("g0"),
            listOf(
                rawDocument(chunkId = "20", ticketKey = "KEY-9", content = null, withIndexedVector = false),
                rawDocument(chunkId = "bad", ticketKey = "KEY-9", withIndexedVector = false),
                rawDocument(chunkId = "22", documentId = null, ticketKey = "KEY-9", withIndexedVector = false),
                rawDocument(
                    chunkId = "23",
                    documentId = "3",
                    ticketKey = "KEY-9",
                    title = null,
                    sourceType = null,
                    storedVector = null,
                    withIndexedVector = false,
                ),
                rawDocument(
                    chunkId = "24",
                    documentId = "4",
                    content = "ticket body",
                    title = "ticket title",
                    sourceType = "JIRA",
                    sectionTitle = "ticket section",
                    sourceUri = "https://jira.test/browse/KEY-9",
                    ticketKey = "KEY-9",
                    sprintReference = "sprint-9",
                    ordinal = 2,
                    withIndexedVector = false,
                ),
                rawDocument(chunkId = "25", ticketKey = "KEY-other", withIndexedVector = false),
            ),
        )
        val adapter = adapterAt(dir)

        val hits = adapter.searchByTicketKey(KB, "KEY-9")

        assertEquals(listOf(23L, 24L), hits.map { it.chunkId })
        assertEquals(1.5, hits.first().score)
        assertEquals("MANUAL", hits.first().sourceType)
        assertNull(hits.first().embedding)
        val complete = hits.last()
        assertEquals("ticket section", complete.sectionTitle)
        assertEquals("KEY-9", complete.ticketKey)
        assertEquals("sprint-9", complete.sprintReference)
        assertEquals("KB:document-4#chunk-2", complete.citation)
    }

    @Test fun `searchByTicketKey caps the result set for a ticket with many chunks`() {
        val dir = tempDir()
        writeRawIndex(
            dir.resolve("base-$KB").resolve("g0"),
            (1..40).map { rawDocument(chunkId = "$it", documentId = "$it", ticketKey = "KEY-40", withIndexedVector = false) },
        )
        val adapter = adapterAt(dir)

        assertEquals(32, adapter.searchByTicketKey(KB, "KEY-40").size)
    }

    @Test fun `searchByTicketKey propagates a term dictionary that cannot be read`() {
        val dir = tempDir()
        writeRawIndex(
            dir.resolve("base-$KB").resolve("g0"),
            listOf(rawDocument(chunkId = "31", documentId = "31", ticketKey = "KEY-31", withIndexedVector = false, storedVector = null)),
            codec = UnreadableTermDictionaryCodec(),
        )
        val adapter = adapterAt(dir)

        assertThrows(IllegalStateException::class.java) {
            adapter.searchByTicketKey(KB, "KEY-31")
        }
    }

    @Test fun `appendChunks skips empty input and indexes every optional field`() {
        val dir = tempDir()
        val adapter = adapterAt(dir)
        adapter.appendChunks(KB, "g0", emptyList())
        assertNull(adapter.activeGeneration(KB))

        adapter.appendChunks(
            KB,
            "g0",
            listOf(
                chunk(1, ticketKey = "   ", sprintReference = " ", embedding = null),
                chunk(2, ticketKey = "KEY-2", sprintReference = "sprint-2", sectionTitle = "section", embedding = FloatArray(0), sourceUri = "file:///tmp/2"),
                chunk(3, ticketKey = "KEY-3", embedding = query),
                chunk(4, documentId = 4, citation = "KB:document-4#chunk-without-digits"),
                chunk(5, documentId = 5, citation = "KB:document-5#chunk-99999999999"),
            ),
        )

        assertEquals(listOf(3L, 4L, 5L), adapter.searchByVector(KB, query, 5).map { it.chunkId })
        assertEquals(listOf(2L), adapter.searchByTicketKey(KB, "KEY-2").map { it.chunkId })
        assertEquals(listOf(3L), adapter.searchByTicketKey(KB, "KEY-3").map { it.chunkId })
        assertTrue(adapter.searchByTicketKey(KB, "   ").isEmpty())
    }

    @Test fun `replaceAllChunks drops stale generations and rewrites the requested one`() {
        val dir = tempDir()
        val adapter = adapterAt(dir)
        adapter.replaceAllChunks(KB, "g0", listOf(chunk(1)))
        assertEquals(listOf(1L), adapter.searchByVector(KB, query, 5).map { it.chunkId })

        adapter.replaceAllChunks(KB, "g1", listOf(chunk(2, embedding = floatArrayOf(0f, 1f, 0f, 0f))))

        assertEquals("g1", adapter.activeGeneration(KB))
        assertFalse(Files.exists(dir.resolve("base-$KB").resolve("g0")))
        assertEquals(listOf(2L), adapter.searchByVector(KB, query, 5).map { it.chunkId })
    }

    @Test fun `replaceAllChunks replaces a generation directory that already exists`() {
        val dir = tempDir()
        val adapter = adapterAt(dir)
        adapter.replaceAllChunks(KB, "g0", listOf(chunk(1), chunk(2, documentId = 2)))
        assertEquals(2, adapter.searchByVector(KB, query, 5).size)

        adapter.replaceAllChunks(KB, "g0", listOf(chunk(3)))

        assertEquals(listOf(3L), adapter.searchByVector(KB, query, 5).map { it.chunkId })
    }

    @Test fun `replaceAllChunks clears a generation directory that sits outside the base directory`() {
        val dir = tempDir()
        val stale = Files.createDirectories(dir.resolve("base-$KB").resolve("retired"))
        Files.writeString(stale.resolve("old.seg"), "old")
        val outside = Files.createDirectories(dir.resolve("legacy-generation"))
        Files.writeString(outside.resolve("stale.seg"), "stale")
        val adapter = adapterAt(dir)

        adapter.replaceAllChunks(KB, "../legacy-generation", listOf(chunk(1)))

        assertFalse(Files.exists(stale))
        assertFalse(Files.exists(outside.resolve("stale.seg")))
        assertTrue(
            Files
                .list(outside)
                .use { it.toList() }
                .isNotEmpty(),
        )
    }

    @Test fun `removeDocument deletes only the chunks of the requested document`() {
        val dir = tempDir()
        val adapter = adapterAt(dir)
        adapter.replaceAllChunks(
            KB,
            "g0",
            listOf(
                chunk(1, documentId = 100),
                chunk(2, documentId = 200),
                chunk(3, documentId = 100),
            ),
        )

        adapter.removeDocument(KB, 100L)

        assertEquals(listOf(2L), adapter.searchByVector(KB, query, 5).map { it.chunkId })
        adapter.removeDocument(KB, 999L)
        assertEquals(listOf(2L), adapter.searchByVector(KB, query, 5).map { it.chunkId })
    }

    @Test fun `clearKnowledgeBase removes the tree and tolerates an unknown base`() {
        val dir = tempDir()
        val adapter = adapterAt(dir)
        adapter.clearKnowledgeBase(404L)

        adapter.replaceAllChunks(KB, "g0", listOf(chunk(1)))
        assertNotNull(adapter.activeGeneration(KB))

        adapter.clearKnowledgeBase(KB)

        assertNull(adapter.activeGeneration(KB))
        assertFalse(Files.exists(dir.resolve("base-$KB")))
    }

    private companion object {
        const val KB = 9L
    }
}
