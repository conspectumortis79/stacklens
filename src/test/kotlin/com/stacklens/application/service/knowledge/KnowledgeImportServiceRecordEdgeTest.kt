package com.stacklens.application.service.knowledge

import com.stacklens.adapter.out.knowledge.DefaultKnowledgeHashing
import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveManifest
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveReaderPort
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeChunk
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class KnowledgeImportServiceRecordEdgeTest {
    private val basePort = mock<KnowledgeBasePort>()

    private val sourcePort = mock<KnowledgeSourcePort>()

    private val documentPort = mock<KnowledgeDocumentPort>()

    private val chunkPort = mock<KnowledgeChunkPort>()

    private val readerPort = mock<KnowledgeArchiveReaderPort>()

    private val indexPort = mock<KnowledgeIndexPort>()

    private val now = LocalDateTime.of(2024, 1, 1, 0, 0)

    private val service =
        knowledgeImportService(
            basePort = basePort,
            sourcePort = sourcePort,
            documentPort = documentPort,
            chunkPort = chunkPort,
            readerPort = readerPort,
            indexPort = indexPort,
            hashing = DefaultKnowledgeHashing(),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
            fileReadWritePort = mock(),
            bundlePort = mock(),
        )

    private val archiveBytes = byteArrayOf(1, 2, 3)

    private fun manifest(name: String) =
        KnowledgeArchiveManifest(
            format = "stacklens-knowledge",
            version = 1,
            exportedAt = now.toString(),
            stacklensVersion = "1.0.0",
            knowledgeBaseStableId = "kb-stable",
            knowledgeBaseName = name,
            knowledgeBaseDescription = "from archive",
            sourceCount = 1,
            documentCount = 1,
            chunkCount = 1,
            containsVectors = false,
            embeddingProvider = null,
            embeddingModel = null,
            embeddingDimension = null,
            embeddingProfile = null,
        )

    private fun stubRead(
        manifest: KnowledgeArchiveManifest,
        records: List<KnowledgeArchiveRecord>,
    ) {
        whenever(readerPort.inspect(eq(archiveBytes), anyOrNull())).thenReturn(manifest)
        whenever(readerPort.read(eq(archiveBytes), anyOrNull())).thenReturn(records.asSequence())
    }

    private fun sourceRecord(stableId: String) =
        KnowledgeArchiveRecord.Source(
            stableId = stableId,
            name = "Quelle",
            type = "MANUAL",
            rootUrl = null,
            externalConfigName = null,
            externalReference = null,
            includeChildren = false,
            includeComments = false,
            maxDepth = 1,
            active = true,
        )

    private fun documentRecord(
        stableId: String,
        sourceStableId: String,
    ) = KnowledgeArchiveRecord.Document(
        stableId = stableId,
        sourceStableId = sourceStableId,
        externalId = null,
        title = "Dokument",
        sourceUri = null,
        mimeType = "text/plain",
        plainText = "inhalt",
        contentHash = "hash",
        metadata = emptyMap(),
    )

    private fun chunkRecord(
        stableId: String,
        documentStableId: String,
    ) = KnowledgeArchiveRecord.Chunk(
        stableId = stableId,
        documentStableId = documentStableId,
        ordinal = 0,
        sectionTitle = "kapitel",
        content = "chunk inhalt",
        contentHash = "hash",
        tokenEstimate = 3,
        vectorBytes = null,
        vectorDimension = null,
    )

    private fun stubSaves() {
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.findById(any())).thenAnswer {
            KnowledgeBase(id = 7L, stableId = "kb-stable", name = "Ziel", description = "", createdAt = now, updatedAt = now)
        }
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer {
            KnowledgeBase(id = 7L, stableId = "kb-stable", name = "Ziel", description = "", createdAt = now, updatedAt = now)
        }
        whenever(sourcePort.findByKnowledgeBaseId(any())).thenReturn(emptyList())
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer { invocation ->
            val src = invocation.arguments[0] as KnowledgeSource
            KnowledgeSource(
                id = 11L,
                stableId = src.stableId,
                knowledgeBaseId = src.knowledgeBaseId,
                name = src.name,
                type = src.type,
                createdAt = src.createdAt,
                updatedAt = src.updatedAt,
            )
        }
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer { invocation ->
            val doc = invocation.arguments[0] as KnowledgeDocument
            KnowledgeDocument(
                id = 21L,
                stableId = doc.stableId,
                sourceId = doc.sourceId,
                knowledgeBaseId = doc.knowledgeBaseId,
                externalId = doc.externalId,
                title = doc.title,
                sourceUri = doc.sourceUri,
                sourceType = doc.sourceType,
                mimeType = doc.mimeType,
                plainText = doc.plainText,
                contentHash = doc.contentHash,
                metadataJson = doc.metadataJson,
                status = doc.status,
                createdAt = doc.createdAt,
                updatedAt = doc.updatedAt,
                indexedAt = doc.indexedAt,
            )
        }
        whenever(chunkPort.save(any<KnowledgeChunk>())).thenAnswer { invocation ->
            val chunk = invocation.arguments[0] as KnowledgeChunk
            KnowledgeChunk(
                id = 31L,
                stableId = chunk.stableId,
                documentId = chunk.documentId,
                knowledgeBaseId = chunk.knowledgeBaseId,
                ordinal = chunk.ordinal,
                content = chunk.content,
                contentHash = chunk.contentHash,
            )
        }
    }

    @Test
    fun `records pointing at unknown owners are skipped and a broken index push is logged`() {
        stubSaves()
        stubRead(
            manifest("Frische Basis"),
            listOf(
                documentRecord("doc-orphan", sourceStableId = "unbekannt"),
                chunkRecord("chunk-orphan", documentStableId = "unbekannt"),
                sourceRecord("src-1"),
                documentRecord("doc-1", sourceStableId = "src-1"),
                chunkRecord("chunk-1", documentStableId = "doc-1"),
                chunkRecord("chunk-2", documentStableId = "doc-1"),
            ),
        )
        whenever(chunkPort.findByDocumentIdOrderByOrdinalAsc(any())).thenReturn(listOf(sampleChunk()))
        Mockito
            .doThrow(RuntimeException("lucene nicht erreichbar"))
            .`when`(indexPort)
            .appendChunks(any(), any(), any())

        val result =
            service.import(
                KnowledgeImportRequest(archiveBytes = archiveBytes, password = null, mode = KnowledgeImportMode.NEW_COPY),
            )

        assertEquals(1, result.documentsImported)
        assertEquals(2, result.chunksImported)
        verify(sourcePort, org.mockito.kotlin.atLeastOnce()).save(any<KnowledgeSource>())
    }

    private fun sampleChunk(): KnowledgeChunk =
        KnowledgeChunk(
            id = 41L,
            stableId = "chunk-live",
            documentId = 21L,
            knowledgeBaseId = 7L,
            ordinal = 0,
            sectionTitle = "kapitel",
            content = "chunk inhalt",
            contentHash = "hash",
            embeddingBlob = null,
            tokenEstimate = 3,
        )

    @Test
    fun `an archive whose base name is taken gets an import suffix`() {
        stubSaves()
        whenever(basePort.findAllByOrderByCreatedAtAsc())
            .thenReturn(listOf(KnowledgeBase(id = 3L, stableId = "andere", name = "Frische Basis", description = "", createdAt = now, updatedAt = now)))
        whenever(chunkPort.findByDocumentIdOrderByOrdinalAsc(any())).thenReturn(emptyList())
        stubRead(manifest("Frische Basis"), listOf(sourceRecord("src-9")))

        val result =
            service.import(
                KnowledgeImportRequest(archiveBytes = archiveBytes, password = null, mode = KnowledgeImportMode.NEW_COPY),
            )

        assertEquals(0, result.documentsImported)
        assertEquals(0, result.chunksImported)
    }

    @Test
    fun `imported chunks without vectors are still pushed to the index`() {
        stubSaves()
        stubRead(
            manifest("Vektor Basis"),
            listOf(sourceRecord("src-2"), documentRecord("doc-2", sourceStableId = "src-2"), chunkRecord("chunk-3", documentStableId = "doc-2")),
        )
        whenever(chunkPort.findByDocumentIdOrderByOrdinalAsc(any())).thenReturn(listOf(sampleChunk()))

        val result =
            service.import(
                KnowledgeImportRequest(archiveBytes = archiveBytes, password = null, mode = KnowledgeImportMode.NEW_COPY),
            )

        assertEquals(1, result.chunksImported)
        assertTrue(result.vectorsImported == 0)
        verify(indexPort).appendChunks(eq(7L), eq("g0"), any())
    }
}
