package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeArchiveManifest
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveReaderPort
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveWriterPort
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeChunk
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class KnowledgeExportServiceFullTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val writerPort: KnowledgeArchiveWriterPort = mock()
    private val readerPort: KnowledgeArchiveReaderPort = mock()

    private val service =
        KnowledgeExportService(
            basePort = basePort,
            sourcePort = sourcePort,
            documentPort = documentPort,
            chunkPort = chunkPort,
            writerPort = writerPort,
            readerPort = readerPort,
        )

    private val now = LocalDateTime.of(2024, 1, 1, 0, 0)

    private fun base(id: Long = 7L) =
        KnowledgeBase(
            id = id,
            stableId = "kb-stable",
            name = "Test Base",
            description = "desc",
            createdAt = now,
            updatedAt = now,
        )

    private fun source(id: Long = 11L) =
        KnowledgeSource(
            id = id,
            stableId = "src-1",
            knowledgeBaseId = 7L,
            name = "Source1",
            type = KnowledgeSourceType.MANUAL,
            status = KnowledgeSourceStatus.READY,
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun `exportBase throws when the password is blank`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            service.exportBase(7L, KnowledgeExportOptions(), password = "")
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            service.exportBase(7L, KnowledgeExportOptions(), password = "   ")
        }
    }

    @Test
    fun `exportBase throws when the knowledge base is not found`() {
        whenever(basePort.findById(7L)).thenReturn(null)
        val ex =
            assertThrows(IllegalStateException::class.java) {
                service.exportBase(7L, KnowledgeExportOptions(), password = "p")
            }
        assertTrue(ex.message!!.contains("7"))
    }

    @Test
    fun `exportBase returns the writer output and includes vectors by default`() {
        val b = base()
        val s = source()
        whenever(basePort.findById(7L)).thenReturn(b)
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(listOf(s))
        whenever(sourcePort.findByStableId("src-1")).thenReturn(s)
        whenever(documentPort.findBySourceIdOrderByIdAsc(11L)).thenReturn(
            listOf(
                KnowledgeDocument(
                    id = 21L,
                    stableId = "doc-1",
                    sourceId = 11L,
                    knowledgeBaseId = 7L,
                    externalId = null,
                    title = "Doc",
                    sourceUri = null,
                    sourceType = KnowledgeSourceType.MANUAL,
                    mimeType = "text/plain",
                    plainText = "text",
                    contentHash = "h",
                    metadataJson = null,
                    status = com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus.READY,
                    createdAt = now,
                    updatedAt = now,
                    indexedAt = now,
                ),
            ),
        )
        whenever(documentPort.findByStableId("doc-1")).thenReturn(
            KnowledgeDocument(
                id = 21L,
                stableId = "doc-1",
                sourceId = 11L,
                knowledgeBaseId = 7L,
                externalId = null,
                title = "Doc",
                sourceUri = null,
                sourceType = KnowledgeSourceType.MANUAL,
                mimeType = "text/plain",
                plainText = "text",
                contentHash = "h",
                metadataJson = null,
                status = com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus.READY,
                createdAt = now,
                updatedAt = now,
                indexedAt = now,
            ),
        )
        whenever(chunkPort.findByDocumentIdOrderByOrdinalAsc(21L)).thenReturn(
            listOf(
                KnowledgeChunk(
                    id = 31L,
                    stableId = "ch-1",
                    documentId = 21L,
                    knowledgeBaseId = 7L,
                    ordinal = 0,
                    sectionTitle = "intro",
                    content = "y",
                    contentHash = "h",
                    embeddingProfile = null,
                    embeddingDimension = 2,
                    embeddingBlob = byteArrayOf(1, 2, 3),
                    tokenEstimate = 1,
                ),
            ),
        )
        whenever(writerPort.write(any(), any(), any())).thenAnswer { "EXPORT".toByteArray() }

        val bytes =
            service.exportBase(
                7L,
                KnowledgeExportOptions(includeVectors = true),
                password = "p",
            )
        assertEquals("EXPORT", String(bytes))

        verify(writerPort).write(
            argThat { snap -> snap.documents.size == 1 && snap.chunks.size == 1 },
            eq(KnowledgeExportOptions(includeVectors = true)),
            eq("p"),
        )
    }

    @Test
    fun `exportBase drops embeddingBlob when includeVectors is false`() {
        val b = base()
        val s = source()
        whenever(basePort.findById(7L)).thenReturn(b)
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(listOf(s))
        whenever(sourcePort.findByStableId("src-1")).thenReturn(s)
        whenever(documentPort.findBySourceIdOrderByIdAsc(11L)).thenReturn(
            listOf(
                KnowledgeDocument(
                    id = 21L,
                    stableId = "doc-1",
                    sourceId = 11L,
                    knowledgeBaseId = 7L,
                    externalId = null,
                    title = "Doc",
                    sourceUri = null,
                    sourceType = KnowledgeSourceType.MANUAL,
                    mimeType = "text/plain",
                    plainText = "text",
                    contentHash = "h",
                    metadataJson = null,
                    status = com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus.READY,
                    createdAt = now,
                    updatedAt = now,
                    indexedAt = now,
                ),
            ),
        )
        whenever(documentPort.findByStableId("doc-1")).thenReturn(
            KnowledgeDocument(
                id = 21L,
                stableId = "doc-1",
                sourceId = 11L,
                knowledgeBaseId = 7L,
                externalId = null,
                title = "Doc",
                sourceUri = null,
                sourceType = KnowledgeSourceType.MANUAL,
                mimeType = "text/plain",
                plainText = "text",
                contentHash = "h",
                metadataJson = null,
                status = com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus.READY,
                createdAt = now,
                updatedAt = now,
                indexedAt = now,
            ),
        )
        whenever(chunkPort.findByDocumentIdOrderByOrdinalAsc(21L)).thenReturn(
            listOf(
                KnowledgeChunk(
                    id = 31L,
                    stableId = "ch-1",
                    documentId = 21L,
                    knowledgeBaseId = 7L,
                    ordinal = 0,
                    sectionTitle = null,
                    content = "y",
                    contentHash = "h",
                    embeddingProfile = null,
                    embeddingDimension = 2,
                    embeddingBlob = byteArrayOf(1, 2, 3),
                    tokenEstimate = 1,
                ),
            ),
        )
        whenever(writerPort.write(any(), any(), any())).thenAnswer { "X".toByteArray() }

        service.exportBase(7L, KnowledgeExportOptions(includeVectors = false), password = "p")

        verify(writerPort).write(
            argThat { snap ->
                snap.chunks.first().vectorBytes == null
            },
            eq(KnowledgeExportOptions(includeVectors = false)),
            any(),
        )
    }

    @Test
    fun `exportBase skips documents for which the source cannot be resolved by stableId`() {
        val b = base()
        val s = source()
        whenever(basePort.findById(7L)).thenReturn(b)
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(listOf(s))
        whenever(sourcePort.findByStableId("src-1")).thenReturn(null)

        service.exportBase(7L, KnowledgeExportOptions(), password = "p")

        verify(writerPort).write(
            argThat { snap -> snap.documents.isEmpty() },
            any(),
            any(),
        )
    }

    @Test
    fun `exportBase skips chunks for documents that no longer exist`() {
        val b = base()
        val s = source()
        whenever(basePort.findById(7L)).thenReturn(b)
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(listOf(s))
        whenever(sourcePort.findByStableId("src-1")).thenReturn(s)
        whenever(documentPort.findBySourceIdOrderByIdAsc(11L)).thenReturn(
            listOf(
                KnowledgeDocument(
                    id = 21L,
                    stableId = "doc-1",
                    sourceId = 11L,
                    knowledgeBaseId = 7L,
                    externalId = null,
                    title = "Doc",
                    sourceUri = null,
                    sourceType = KnowledgeSourceType.MANUAL,
                    mimeType = "text/plain",
                    plainText = "text",
                    contentHash = "h",
                    metadataJson = null,
                    status = com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus.READY,
                    createdAt = now,
                    updatedAt = now,
                    indexedAt = now,
                ),
            ),
        )
        whenever(documentPort.findByStableId("doc-1")).thenReturn(null)
        whenever(writerPort.write(any(), any(), any())).thenAnswer { "X".toByteArray() }

        service.exportBase(7L, KnowledgeExportOptions(), password = "p")

        verify(writerPort).write(
            argThat { snap -> snap.chunks.isEmpty() },
            any(),
            any(),
        )
    }

    @Test
    fun `exportBase serialises metadata into a map`() {
        val b = base()
        val s = source()
        whenever(basePort.findById(7L)).thenReturn(b)
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(listOf(s))
        whenever(sourcePort.findByStableId("src-1")).thenReturn(s)
        whenever(documentPort.findBySourceIdOrderByIdAsc(11L)).thenReturn(
            listOf(
                KnowledgeDocument(
                    id = 21L,
                    stableId = "doc-1",
                    sourceId = 11L,
                    knowledgeBaseId = 7L,
                    externalId = null,
                    title = "Doc",
                    sourceUri = null,
                    sourceType = KnowledgeSourceType.MANUAL,
                    mimeType = "text/plain",
                    plainText = "text",
                    contentHash = "h",
                    metadataJson = "k1=v1,k2=v2",
                    status = com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus.READY,
                    createdAt = now,
                    updatedAt = now,
                    indexedAt = now,
                ),
            ),
        )
        whenever(documentPort.findByStableId("doc-1")).thenReturn(
            KnowledgeDocument(
                id = 21L,
                stableId = "doc-1",
                sourceId = 11L,
                knowledgeBaseId = 7L,
                externalId = null,
                title = "Doc",
                sourceUri = null,
                sourceType = KnowledgeSourceType.MANUAL,
                mimeType = "text/plain",
                plainText = "text",
                contentHash = "h",
                metadataJson = "k1=v1,k2=v2",
                status = com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus.READY,
                createdAt = now,
                updatedAt = now,
                indexedAt = now,
            ),
        )
        whenever(chunkPort.findByDocumentIdOrderByOrdinalAsc(21L)).thenReturn(emptyList())
        whenever(writerPort.write(any(), any(), any())).thenAnswer { "X".toByteArray() }

        service.exportBase(7L, KnowledgeExportOptions(), password = "p")

        verify(writerPort).write(
            argThat { snap -> snap.documents.first().metadata == mapOf("k1" to "v1", "k2" to "v2") },
            any(),
            any(),
        )
    }

    @Test
    fun `inspect returns the manifest-derived preview with a recognised embedding provider`() {
        whenever(readerPort.inspect(any(), any()))
            .thenReturn(
                KnowledgeArchiveManifest(
                    format = "x",
                    version = 1,
                    exportedAt = "now",
                    stacklensVersion = "1.0",
                    knowledgeBaseStableId = "kb-stable",
                    knowledgeBaseName = "n",
                    knowledgeBaseDescription = "d",
                    sourceCount = 2,
                    documentCount = 4,
                    chunkCount = 9,
                    containsVectors = true,
                    embeddingProvider = "OPENAI",
                    embeddingModel = "text-embedding-3-small",
                    embeddingDimension = 1536,
                    embeddingProfile = null,
                ),
            )
        val preview = service.inspect(byteArrayOf(1, 2, 3), password = "p")
        assertEquals("n", preview.knowledgeBaseName)
        assertEquals(2, preview.sourceCount)
        assertEquals(4, preview.documentCount)
        assertEquals(9, preview.chunkCount)
        assertNotNull(preview.embeddingProvider)
    }

    @Test
    fun `inspect returns a null embeddingProvider when the manifest exposes an unknown one`() {
        whenever(readerPort.inspect(any(), any()))
            .thenReturn(
                KnowledgeArchiveManifest(
                    format = "x",
                    version = 1,
                    exportedAt = "now",
                    stacklensVersion = "1.0",
                    knowledgeBaseStableId = "kb-stable",
                    knowledgeBaseName = "n",
                    knowledgeBaseDescription = "d",
                    sourceCount = 0,
                    documentCount = 0,
                    chunkCount = 0,
                    containsVectors = false,
                    embeddingProvider = "TOTALLY_UNKNOWN",
                    embeddingModel = null,
                    embeddingDimension = null,
                    embeddingProfile = null,
                ),
            )
        val preview = service.inspect(byteArrayOf(1), password = "p")
        assertNull(preview.embeddingProvider)
    }

    @Test
    fun `toArchiveRecord maps every KnowledgeSource field onto the archive record`() {
        val s =
            KnowledgeSource(
                id = 0L,
                stableId = "src-stable",
                knowledgeBaseId = 7L,
                name = "S",
                type = KnowledgeSourceType.MANUAL,
                active = true,
                rootUrl = "https://example.com",
                externalReference = "ext",
                includeChildren = true,
                includeComments = true,
                maxDepth = 9,
                autoManaged = true,
                status = KnowledgeSourceStatus.READY,
                createdAt = now,
                updatedAt = now,
            )
        val record = invokeToArchiveRecord(s)
        assertEquals("src-stable", record.stableId)
        assertEquals("S", record.name)
        assertEquals("MANUAL", record.type)
        assertEquals("https://example.com", record.rootUrl)
        assertEquals("ext", record.externalReference)
        assertEquals(null, record.externalConfigName)
        assertEquals(true, record.includeChildren)
        assertEquals(true, record.includeComments)
        assertEquals(9, record.maxDepth)
        assertEquals(true, record.active)
    }

    private fun invokeToArchiveRecord(src: KnowledgeSource): com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord.Source {
        val helperClass = Class.forName("com.stacklens.application.service.knowledge.KnowledgeExportServiceKt")
        val method =
            helperClass.declaredMethods.firstOrNull { it.name == "toArchiveRecord" }
                ?: helperClass.declaredMethods.first { it.name.contains("toArchiveRecord") }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, src) as com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord.Source
    }

    @Test
    fun `parseMetadata handles blank and null input via a null KnowledgeSource metadataJson`() {
        val b = base()
        val s = source()
        whenever(basePort.findById(7L)).thenReturn(b)
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(listOf(s))
        whenever(sourcePort.findByStableId("src-1")).thenReturn(s)
        whenever(documentPort.findBySourceIdOrderByIdAsc(11L)).thenReturn(
            listOf(
                KnowledgeDocument(
                    id = 21L,
                    stableId = "doc-1",
                    sourceId = 11L,
                    knowledgeBaseId = 7L,
                    externalId = null,
                    title = "Doc",
                    sourceUri = null,
                    sourceType = KnowledgeSourceType.MANUAL,
                    mimeType = "text/plain",
                    plainText = "text",
                    contentHash = "h",
                    metadataJson = "",
                    status = com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus.READY,
                    createdAt = now,
                    updatedAt = now,
                    indexedAt = now,
                ),
            ),
        )
        whenever(documentPort.findByStableId("doc-1")).thenReturn(
            KnowledgeDocument(
                id = 21L,
                stableId = "doc-1",
                sourceId = 11L,
                knowledgeBaseId = 7L,
                externalId = null,
                title = "Doc",
                sourceUri = null,
                sourceType = KnowledgeSourceType.MANUAL,
                mimeType = "text/plain",
                plainText = "text",
                contentHash = "h",
                metadataJson = "",
                status = com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus.READY,
                createdAt = now,
                updatedAt = now,
                indexedAt = now,
            ),
        )
        whenever(chunkPort.findByDocumentIdOrderByOrdinalAsc(21L)).thenReturn(emptyList())
        whenever(writerPort.write(any(), any(), any())).thenAnswer { "X".toByteArray() }

        service.exportBase(7L, KnowledgeExportOptions(), password = "p")

        verify(writerPort).write(
            argThat { snap ->
                snap.documents
                    .first()
                    .metadata
                    .isEmpty()
            },
            any(),
            any(),
        )
    }

    @Test
    fun `inspect returns a null embeddingProvider when the manifest has no provider at all`() {
        whenever(readerPort.inspect(any(), any()))
            .thenReturn(
                KnowledgeArchiveManifest(
                    format = "x",
                    version = 1,
                    exportedAt = "now",
                    stacklensVersion = "1.0",
                    knowledgeBaseStableId = "kb-stable",
                    knowledgeBaseName = "n",
                    knowledgeBaseDescription = "d",
                    sourceCount = 0,
                    documentCount = 0,
                    chunkCount = 0,
                    containsVectors = false,
                    embeddingProvider = null,
                    embeddingModel = null,
                    embeddingDimension = null,
                    embeddingProfile = null,
                ),
            )
        val preview = service.inspect(byteArrayOf(1), password = "p")
        assertNull(preview.embeddingProvider)
    }

    @Test
    fun `exportBase parses metadata entries with equals at index 0 as empty key`() {
        val b = base()
        val s = source()
        whenever(basePort.findById(7L)).thenReturn(b)
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(listOf(s))
        whenever(sourcePort.findByStableId("src-1")).thenReturn(s)
        whenever(documentPort.findBySourceIdOrderByIdAsc(11L)).thenReturn(
            listOf(
                KnowledgeDocument(
                    id = 21L,
                    stableId = "doc-1",
                    sourceId = 11L,
                    knowledgeBaseId = 7L,
                    externalId = null,
                    title = "Doc",
                    sourceUri = null,
                    sourceType = KnowledgeSourceType.MANUAL,
                    mimeType = "text/plain",
                    plainText = "text",
                    contentHash = "h",
                    metadataJson = "=v1",
                    status = com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus.READY,
                    createdAt = now,
                    updatedAt = now,
                    indexedAt = now,
                ),
            ),
        )
        whenever(documentPort.findByStableId("doc-1")).thenReturn(
            KnowledgeDocument(
                id = 21L,
                stableId = "doc-1",
                sourceId = 11L,
                knowledgeBaseId = 7L,
                externalId = null,
                title = "Doc",
                sourceUri = null,
                sourceType = KnowledgeSourceType.MANUAL,
                mimeType = "text/plain",
                plainText = "text",
                contentHash = "h",
                metadataJson = "=v1",
                status = com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus.READY,
                createdAt = now,
                updatedAt = now,
                indexedAt = now,
            ),
        )
        whenever(chunkPort.findByDocumentIdOrderByOrdinalAsc(21L)).thenReturn(emptyList())
        whenever(writerPort.write(any(), any(), any())).thenAnswer { "X".toByteArray() }

        service.exportBase(7L, KnowledgeExportOptions(), password = "p")

        verify(writerPort).write(
            argThat { snap ->
                snap.documents
                    .first()
                    .metadata
                    .isEmpty()
            },
            any(),
            any(),
        )
    }
}
