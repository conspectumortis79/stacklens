package com.stacklens.application.service.knowledge

import com.stacklens.adapter.out.knowledge.DefaultKnowledgeHashing
import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeChunk
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class KnowledgeReindexServiceMetadataTest {
    private val basePort: KnowledgeBasePort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val indexPort: KnowledgeIndexPort = mock()
    private val indexedChunks = argumentCaptor<List<IndexedKnowledgeChunk>>()
    private val now: LocalDateTime = LocalDateTime.of(2024, 6, 1, 8, 30)

    private val service =
        KnowledgeReindexService(
            basePort = basePort,
            chunkPort = chunkPort,
            documentPort = documentPort,
            indexPort = indexPort,
            hashing = DefaultKnowledgeHashing(),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    private fun reindex(metadataJson: String?): IndexedKnowledgeChunk {
        whenever(basePort.findById(BASE_ID)) doReturn KnowledgeBase(id = BASE_ID, name = BASE_NAME, createdAt = now, updatedAt = now)
        whenever(chunkPort.findByKnowledgeBaseId(BASE_ID)) doReturn
            listOf(
                KnowledgeChunk(
                    id = 31L,
                    stableId = "chunk-31",
                    documentId = DOCUMENT_ID,
                    knowledgeBaseId = BASE_ID,
                    ordinal = 0,
                    content = "content",
                    contentHash = "hash",
                ),
            )
        whenever(documentPort.findById(DOCUMENT_ID)) doReturn
            KnowledgeDocument(
                id = DOCUMENT_ID,
                stableId = "doc-21",
                sourceId = 11L,
                knowledgeBaseId = BASE_ID,
                title = "Ticket notes",
                sourceType = KnowledgeSourceType.TICKET_CREATION,
                plainText = "body",
                contentHash = "hash",
                metadataJson = metadataJson,
                status = KnowledgeDocumentStatus.READY,
                createdAt = now,
                updatedAt = now,
            )

        val result = service.reindexBase(BASE_ID)

        assertEquals(BASE_NAME, result?.knowledgeBaseName)
        assertEquals(1, result?.chunkCount)
        verify(indexPort).replaceAllChunks(eq(BASE_ID), any(), indexedChunks.capture())
        return indexedChunks.lastValue.single()
    }

    @Test
    fun `both ticket references are read out of the document metadata`() {
        val chunk = reindex("ticketKey=PROJ-7,sprintReference=Sprint 12")

        assertEquals("PROJ-7", chunk.ticketKey)
        assertEquals("Sprint 12", chunk.sprintReference)
    }

    @Test
    fun `metadata values may contain the separator themselves`() {
        val chunk = reindex("sprintReference=board=42,ticketKey=PROJ-8")

        assertEquals("board=42", chunk.sprintReference)
        assertEquals("PROJ-8", chunk.ticketKey)
    }

    @Test
    fun `entries without a value and unknown keys are ignored`() {
        val chunk = reindex("noise,,ticketKeyNoEquals,ticketKey=PROJ-9")

        assertEquals("PROJ-9", chunk.ticketKey)
        assertNull(chunk.sprintReference)
    }

    @Test
    fun `a blank metadata value is treated as absent`() {
        val chunk = reindex("ticketKey=  ,sprintReference=")

        assertNull(chunk.ticketKey)
        assertNull(chunk.sprintReference)
    }

    @Test
    fun `the first entry of a repeated key wins`() {
        val chunk = reindex("ticketKey=first,ticketKey=second")

        assertEquals("first", chunk.ticketKey)
    }

    @Test
    fun `metadata without any ticket reference leaves both fields empty`() {
        val chunk = reindex("author=qa")

        assertNull(chunk.ticketKey)
        assertNull(chunk.sprintReference)
    }

    @Test
    fun `a document without metadata carries no ticket reference`() {
        val chunk = reindex(null)

        assertNull(chunk.ticketKey)
        assertNull(chunk.sprintReference)
    }

    companion object {
        private const val BASE_ID = 1L
        private const val BASE_NAME = "Analysen"
        private const val DOCUMENT_ID = 21L
    }
}
