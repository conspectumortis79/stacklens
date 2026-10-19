package com.stacklens.application.service.knowledge

import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveManifest
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveReaderPort
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.OngoingStubbing
import java.time.LocalDateTime

class KnowledgeImportServiceSmartImportTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val readerPort: KnowledgeArchiveReaderPort = mock()
    private val indexPort: KnowledgeIndexPort = mock()

    private val service =
        knowledgeImportService(
            basePort = basePort,
            sourcePort = sourcePort,
            documentPort = documentPort,
            chunkPort = chunkPort,
            readerPort = readerPort,
            indexPort = indexPort,
            hashing =
                com.stacklens.adapter.out.knowledge
                    .DefaultKnowledgeHashing(),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
            fileReadWritePort = mock(),
            bundlePort = mock(),
        )

    private val now = LocalDateTime.of(2024, 1, 1, 0, 0)

    private val existingName = "Bug3-Test-Base"
    private val existingBase =
        KnowledgeBase(
            id = 42L,
            stableId = "existing-stable-id",
            name = existingName,
            description = "already there",
            createdAt = now,
            updatedAt = now,
        )

    private val manifest =
        KnowledgeArchiveManifest(
            format = "stacklens-knowledge",
            version = 1,
            exportedAt = now.toString(),
            stacklensVersion = "1.0.0",
            knowledgeBaseStableId = "archive-stable-id",
            knowledgeBaseName = existingName,
            knowledgeBaseDescription = "from archive",
            sourceCount = 0,
            documentCount = 0,
            chunkCount = 0,
            containsVectors = false,
            embeddingProvider = null,
            embeddingModel = null,
            embeddingDimension = null,
            embeddingProfile = null,
        )

    private val archiveBytes: ByteArray = byteArrayOf(1, 2, 3)

    private fun emptyImportRequest(mode: KnowledgeImportMode = KnowledgeImportMode.NEW_COPY) = KnowledgeImportRequest(archiveBytes = archiveBytes, password = null, mode = mode)

    private fun stubInspect(): OngoingStubbing<KnowledgeArchiveManifest> = whenever(readerPort.inspect(eq(archiveBytes), anyOrNull())).thenReturn(manifest)

    private fun stubExistingBaseFound() {
        stubInspect()
        whenever(readerPort.read(eq(archiveBytes), anyOrNull())).thenReturn(emptySequence())
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(existingBase))
        whenever(sourcePort.findByKnowledgeBaseId(any())).thenReturn(emptyList())
    }

    private fun stubNoExistingBase() {
        stubInspect()
        whenever(readerPort.read(eq(archiveBytes), anyOrNull())).thenReturn(emptySequence())
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer { invocation ->
            val src = invocation.arguments[0] as KnowledgeBase
            KnowledgeBase(
                id = 99L,
                stableId = src.stableId,
                name = src.name,
                description = src.description,
                createdAt = src.createdAt,
                updatedAt = src.updatedAt,
            )
        }
        whenever(sourcePort.findByKnowledgeBaseId(any())).thenReturn(emptyList())
    }

    @Test
    fun `NEW_COPY into an existing base name routes into the same base as REPLACE without creating a duplicate`() {
        stubExistingBaseFound()

        val result = service.import(emptyImportRequest())

        assertEquals(42L, result.knowledgeBaseId)
        assertEquals(existingName, result.knowledgeBaseName)
        assertEquals(KnowledgeImportMode.REPLACE, result.mode)
        verify(basePort, never()).save(any<KnowledgeBase>())
        verify(chunkPort).deleteByKnowledgeBaseId(eq(42L))
        verify(documentPort).deleteByKnowledgeBaseId(eq(42L))
        verify(sourcePort).deleteByKnowledgeBaseId(eq(42L))
    }

    @Test
    fun `NEW_COPY into an unknown base name still creates a fresh base`() {
        stubNoExistingBase()

        val result = service.import(emptyImportRequest())

        assertEquals(99L, result.knowledgeBaseId)
        assertEquals(existingName, result.knowledgeBaseName)
        assertEquals(KnowledgeImportMode.NEW_COPY, result.mode)
        verify(basePort).save(any<KnowledgeBase>())
    }

    @Test
    fun `caller-supplied REPLACE mode is honoured even when the name does not match any existing base`() {
        stubNoExistingBase()

        val result = service.import(emptyImportRequest(mode = KnowledgeImportMode.REPLACE))

        assertEquals(99L, result.knowledgeBaseId)
        assertEquals(KnowledgeImportMode.REPLACE, result.mode)
    }

    @Test
    fun `caller-supplied MERGE mode is honoured without auto-promotion`() {
        stubExistingBaseFound()

        val result = service.import(emptyImportRequest(mode = KnowledgeImportMode.MERGE))

        assertEquals(42L, result.knowledgeBaseId)
        assertEquals(existingName, result.knowledgeBaseName)
        assertEquals(KnowledgeImportMode.MERGE, result.mode)
        verify(basePort, never()).save(any<KnowledgeBase>())
    }
}
