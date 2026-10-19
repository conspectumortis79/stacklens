package com.stacklens.adapter.out.knowledge

import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LuceneKnowledgeIndexAdapterTicketKeyTest {
    private val adapter =
        LuceneKnowledgeIndexAdapter(
            hashing =
                com.stacklens.adapter.out.knowledge
                    .DefaultKnowledgeHashing(),
            indexDir = "/tmp/stacklens-ticket-key-test-${System.nanoTime()}",
        )

    @Test
    fun `searchByTicketKey returns every chunk that carries the key`() {
        val baseId = 5001L
        val chunks =
            listOf(
                chunk(baseId, chunkId = 10L, docId = 100L, key = "BINIT-38092"),
                chunk(baseId, chunkId = 11L, docId = 100L, key = "BINIT-38092"),
                chunk(baseId, chunkId = 12L, docId = 100L, key = "BINIT-38092"),
                chunk(baseId, chunkId = 20L, docId = 200L, key = "BINIT-38090"),
            )
        adapter.replaceAllChunks(baseId, "g0", chunks)

        val hits = adapter.searchByTicketKey(baseId, "BINIT-38092")
        assertEquals(3, hits.size)
        assertEquals(setOf(10L, 11L, 12L), hits.map { it.chunkId }.toSet())
        hits.forEach { assertEquals("BINIT-38092", it.ticketKey) }
        hits.forEach { assertTrue(it.score > 1.0, "expected score > 1.0, got ${it.score}") }
    }

    @Test
    fun `searchByTicketKey is case sensitive on the stored key`() {
        val baseId = 5002L
        adapter.replaceAllChunks(
            baseId,
            "g0",
            listOf(chunk(baseId, chunkId = 1L, docId = 1L, key = "BINIT-38092")),
        )

        assertEquals(1, adapter.searchByTicketKey(baseId, "BINIT-38092").size)
        assertEquals(0, adapter.searchByTicketKey(baseId, "binit-38092").size)
    }

    @Test
    fun `searchByTicketKey returns empty list for a missing key`() {
        val baseId = 5003L
        adapter.replaceAllChunks(
            baseId,
            "g0",
            listOf(chunk(baseId, chunkId = 1L, docId = 1L, key = "BINIT-38092")),
        )
        assertEquals(emptyList<IndexedKnowledgeChunk>(), adapter.searchByTicketKey(baseId, "BINIT-99999"))
        assertEquals(emptyList<IndexedKnowledgeChunk>(), adapter.searchByTicketKey(baseId, ""))
    }

    @Test
    fun `searchByTicketKey returns empty list when the base has no chunks with the field`() {
        val baseId = 5004L
        adapter.replaceAllChunks(
            baseId,
            "g0",
            listOf(
                chunk(baseId, chunkId = 1L, docId = 1L, key = null),
                chunk(baseId, chunkId = 2L, docId = 2L, key = null),
            ),
        )
        assertEquals(0, adapter.searchByTicketKey(baseId, "BINIT-38092").size)
    }

    @Test
    fun `searchByTicketKey also returns the stored sprintReference when one is present`() {
        val baseId = 5005L
        adapter.replaceAllChunks(
            baseId,
            "g0",
            listOf(
                chunk(
                    baseId,
                    chunkId = 1L,
                    docId = 1L,
                    key = "BINIT-38092",
                    sprint = "binit_2026/35-36",
                ),
            ),
        )
        val hit = adapter.searchByTicketKey(baseId, "BINIT-38092").single()
        assertEquals("binit_2026/35-36", hit.sprintReference)
        assertEquals("BINIT-38092", hit.ticketKey)
        assertNotNull(hit.citation)
    }

    @Test
    fun `searchByTicketKey does not return chunks from another knowledge base`() {
        val baseA = 5006L
        val baseB = 5007L
        adapter.replaceAllChunks(
            baseA,
            "g0",
            listOf(chunk(baseA, chunkId = 1L, docId = 1L, key = "BINIT-38092")),
        )
        adapter.replaceAllChunks(
            baseB,
            "g0",
            listOf(chunk(baseB, chunkId = 2L, docId = 2L, key = "BINIT-38092")),
        )
        assertEquals(1, adapter.searchByTicketKey(baseA, "BINIT-38092").size)
        assertEquals(1, adapter.searchByTicketKey(baseB, "BINIT-38092").size)
    }

    private fun chunk(
        baseId: Long,
        chunkId: Long,
        docId: Long,
        key: String?,
        sprint: String? = null,
    ): IndexedKnowledgeChunk =
        IndexedKnowledgeChunk(
            chunkId = chunkId,
            documentId = docId,
            knowledgeBaseId = baseId,
            title = "$key – sample title",
            sectionTitle = null,
            content = "Body of $key",
            sourceType = "JIRA_SPRINT",
            sourceUri = "https://example.atlassian.net/browse/$key",
            embedding = null,
            score = 0.0,
            citation = "KB:document-$docId#chunk-0",
            ticketKey = key,
            sprintReference = sprint,
        )
}
