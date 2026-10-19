package com.stacklens.adapter.out.knowledge

import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LuceneKnowledgeIndexAdapterBranchTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var adapter: LuceneKnowledgeIndexAdapter
    private val baseId: Long = 99L

    @BeforeEach
    fun setUp() {
        adapter =
            LuceneKnowledgeIndexAdapter(
                hashing =
                    com.stacklens.adapter.out.knowledge
                        .DefaultKnowledgeHashing(),
                indexDir = tmp.toAbsolutePath().toString(),
            )
    }

    @AfterEach
    fun tearDown() {
        tmp.toFile().deleteRecursively()
    }

    private fun chunk(
        id: Long,
        documentId: Long = 1L,
        knowledgeBaseId: Long = baseId,
        content: String = "chunk-$id",
        title: String = "title",
        sourceType: String = "MANUAL",
        sourceUri: String? = "https://example.com/source",
        citation: String = "doc:$documentId",
    ): IndexedKnowledgeChunk =
        IndexedKnowledgeChunk(
            chunkId = id,
            documentId = documentId,
            knowledgeBaseId = knowledgeBaseId,
            title = title,
            sectionTitle = null,
            content = content,
            sourceType = sourceType,
            sourceUri = sourceUri,
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
            score = 0.0,
            citation = citation,
        )

    @Test
    fun `activeGeneration is null when the knowledge base has not been written yet`() {
        assertNull(adapter.activeGeneration(baseId))
    }

    @Test
    fun `replaceAllChunks writes a new generation and exposes it via activeGeneration`() {
        val chunks = listOf(chunk(id = 1L), chunk(id = 2L))
        adapter.replaceAllChunks(baseId, generationId = "g-1", chunks = chunks)
        assertEquals("g-1", adapter.activeGeneration(baseId))
    }

    @Test
    fun `replaceAllChunks for the same base creates separate generations when the id changes`() {
        adapter.replaceAllChunks(baseId, "g-1", listOf(chunk(id = 1L)))
        adapter.replaceAllChunks(baseId, "g-2", listOf(chunk(id = 2L)))
        assertEquals("g-2", adapter.activeGeneration(baseId))
    }

    @Test
    fun `replaceAllChunks with an empty chunk list still flips the active generation`() {
        adapter.replaceAllChunks(baseId, "g-empty", emptyList())
        assertEquals("g-empty", adapter.activeGeneration(baseId))
    }

    @Test
    fun `appendChunks adds to the active generation without changing the generation id`() {
        adapter.appendChunks(baseId, "g-1", listOf(chunk(id = 1L)))
        adapter.appendChunks(baseId, "g-1", listOf(chunk(id = 2L)))
        assertEquals("g-1", adapter.activeGeneration(baseId))
    }

    @Test
    fun `appendChunks for a different generation updates the active generation marker`() {
        adapter.appendChunks(baseId, "g-a", listOf(chunk(id = 1L)))
        adapter.appendChunks(baseId, "g-b", listOf(chunk(id = 2L)))
        assertEquals("g-b", adapter.activeGeneration(baseId))
    }

    @Test
    fun `removeDocument removes the chunks of a single document from the index`() {
        adapter.appendChunks(
            baseId,
            "g-1",
            listOf(chunk(id = 1L, documentId = 11L), chunk(id = 2L, documentId = 22L)),
        )
        adapter.removeDocument(baseId, 11L)
        assertEquals("g-1", adapter.activeGeneration(baseId))
    }

    @Test
    fun `removeDocument on a non-existent document is a no-op`() {
        adapter.appendChunks(baseId, "g-1", listOf(chunk(id = 1L, documentId = 11L)))
        adapter.removeDocument(baseId, 99L)
        assertEquals("g-1", adapter.activeGeneration(baseId))
    }

    @Test
    fun `removeDocument on a knowledge base that has no index is a no-op`() {
        adapter.removeDocument(baseId, 1L)
        assertNull(adapter.activeGeneration(baseId))
    }

    @Test
    fun `clearKnowledgeBase removes the entire base directory`() {
        adapter.appendChunks(baseId, "g-1", listOf(chunk(id = 1L)))
        assertNotNull(adapter.activeGeneration(baseId))
        adapter.clearKnowledgeBase(baseId)
        assertNull(adapter.activeGeneration(baseId))
    }

    @Test
    fun `clearKnowledgeBase is safe to call on a non-existent base`() {
        adapter.clearKnowledgeBase(999L)
        assertNull(adapter.activeGeneration(999L))
    }

    @Test
    fun `activeGeneration returns the most recent generation name after appendChunks`() {
        adapter.appendChunks(baseId, "first", listOf(chunk(id = 1L)))
        adapter.appendChunks(baseId, "second", listOf(chunk(id = 2L)))
        assertEquals("second", adapter.activeGeneration(baseId))
    }

    @Test
    fun `replacing a generation discards the previous chunks for the same base`() {
        adapter.appendChunks(baseId, "old", listOf(chunk(id = 1L)))
        adapter.replaceAllChunks(baseId, "new", listOf(chunk(id = 2L)))
        assertEquals("new", adapter.activeGeneration(baseId))
    }

    @Test
    fun `writing a knowledge base never disturbs a sibling base`() {
        adapter.appendChunks(baseId, "g-99", listOf(chunk(id = 1L)))
        assertNull(adapter.activeGeneration(123L))
    }
}
