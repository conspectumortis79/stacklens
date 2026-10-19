package com.stacklens.application.service.knowledge

import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
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
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import com.stacklens.domain.model.knowledge.KnowledgeSourceType as DomainSourceType

class KnowledgeImportServiceFullTest {
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

    private fun savedSource(
        src: KnowledgeSource,
        id: Long,
    ): KnowledgeSource =
        KnowledgeSource(
            id = id,
            stableId = src.stableId,
            knowledgeBaseId = src.knowledgeBaseId,
            name = src.name,
            type = src.type,
            active = src.active,
            rootUrl = src.rootUrl,
            externalConfigId = src.externalConfigId,
            externalReference = src.externalReference,
            includeChildren = src.includeChildren,
            includeComments = src.includeComments,
            maxDepth = src.maxDepth,
            autoManaged = src.autoManaged,
            status = src.status,
            errorMessage = src.errorMessage,
            lastSyncAt = src.lastSyncAt,
            lastSuccessfulSyncAt = src.lastSuccessfulSyncAt,
            createdAt = src.createdAt,
            updatedAt = src.updatedAt,
        )

    private fun savedDocument(
        doc: KnowledgeDocument,
        id: Long,
    ): KnowledgeDocument =
        KnowledgeDocument(
            id = id,
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

    private fun savedChunk(
        chunk: KnowledgeChunk,
        id: Long,
    ): KnowledgeChunk =
        KnowledgeChunk(
            id = id,
            stableId = chunk.stableId,
            documentId = chunk.documentId,
            knowledgeBaseId = chunk.knowledgeBaseId,
            ordinal = chunk.ordinal,
            sectionTitle = chunk.sectionTitle,
            content = chunk.content,
            contentHash = chunk.contentHash,
            embeddingProfile = chunk.embeddingProfile,
            embeddingDimension = chunk.embeddingDimension,
            embeddingBlob = chunk.embeddingBlob,
            tokenEstimate = chunk.tokenEstimate,
        )

    private val now = LocalDateTime.of(2024, 1, 1, 0, 0)

    private fun manifest(
        embeddingProfile: String? = "openai:text-embedding-3-small",
        knowledgeBaseName: String = "Imported Base",
    ) = KnowledgeArchiveManifest(
        format = "stacklens-knowledge",
        version = 1,
        exportedAt = now.toString(),
        stacklensVersion = "1.0.0",
        knowledgeBaseStableId = "kb-stable",
        knowledgeBaseName = knowledgeBaseName,
        knowledgeBaseDescription = "from archive",
        sourceCount = 1,
        documentCount = 1,
        chunkCount = 1,
        containsVectors = true,
        embeddingProvider = "openai",
        embeddingModel = "text-embedding-3-small",
        embeddingDimension = 1536,
        embeddingProfile = embeddingProfile,
    )

    private fun archiveBytes(): ByteArray = byteArrayOf(1, 2, 3)

    private fun importRequest(mode: KnowledgeImportMode = KnowledgeImportMode.REPLACE) = KnowledgeImportRequest(archiveBytes = archiveBytes(), password = null, mode = mode)

    private fun existingBase(
        id: Long = 42L,
        name: String = "Imported Base",
    ) = KnowledgeBase(
        id = id,
        stableId = "kb-stable",
        name = name,
        description = "from archive",
        createdAt = now,
        updatedAt = now,
    )

    private fun stubInspectAndRead(
        manifest: KnowledgeArchiveManifest = manifest(),
        records: Sequence<KnowledgeArchiveRecord> = emptySequence(),
    ) {
        whenever(readerPort.inspect(eq(archiveBytes()), anyOrNull())).thenReturn(manifest)
        whenever(readerPort.read(eq(archiveBytes()), anyOrNull())).thenReturn(records)
    }

    private fun stubBaseFallback(
        byId: KnowledgeBase? = null,
        preferredName: String = "Imported Base",
        existingList: List<KnowledgeBase> = emptyList(),
    ): KnowledgeBase {
        val base =
            byId ?: KnowledgeBase(
                id = 7L,
                stableId = "kb-stable",
                name = preferredName,
                description = "from archive",
                createdAt = now,
                updatedAt = now,
            )
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(existingList)
        whenever(basePort.findById(7L)).thenReturn(base)
        whenever(basePort.findById(any())).thenReturn(base)
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer { invocation ->
            val src = invocation.arguments[0] as KnowledgeBase
            KnowledgeBase(
                id = 7L,
                stableId = src.stableId,
                name = src.name,
                description = src.description,
                createdAt = src.createdAt,
                updatedAt = src.updatedAt,
            )
        }
        whenever(sourcePort.findByKnowledgeBaseId(any())).thenReturn(emptyList())
        return base
    }

    @Test
    fun `REPLACE imports source, document and chunk with vector and indexes them`() {
        val base = stubBaseFallback()
        stubInspectAndRead(
            records =
                sequenceOf(
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-1",
                        name = "Source1",
                        type = "MANUAL",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 1,
                        active = true,
                    ),
                    KnowledgeArchiveRecord.Document(
                        stableId = "doc-1",
                        sourceStableId = "src-1",
                        externalId = null,
                        title = "Doc1",
                        sourceUri = null,
                        mimeType = "text/plain",
                        plainText = "hello",
                        contentHash = "h",
                        metadata = emptyMap(),
                    ),
                    KnowledgeArchiveRecord.Chunk(
                        stableId = "ch-1",
                        documentStableId = "doc-1",
                        ordinal = 0,
                        sectionTitle = "intro",
                        content = "hello world",
                        contentHash = "h",
                        tokenEstimate = 2,
                        vectorBytes = ByteArray(8) { 0 },
                        vectorDimension = 2,
                    ),
                ),
        )
        whenever(basePort.findById(7L)).thenReturn(base)
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer {
            savedSource(it.arguments[0] as KnowledgeSource, 11L)
        }
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer {
            savedDocument(it.arguments[0] as KnowledgeDocument, 21L)
        }
        whenever(chunkPort.save(any<KnowledgeChunk>())).thenAnswer {
            savedChunk(it.arguments[0] as KnowledgeChunk, 31L)
        }
        whenever(chunkPort.findByDocumentIdOrderByOrdinalAsc(any())).thenReturn(emptyList())

        val result = service.import(importRequest(KnowledgeImportMode.REPLACE))

        assertEquals(7L, result.knowledgeBaseId)
        assertEquals(1, result.documentsImported)
        assertEquals(1, result.chunksImported)
        assertEquals(1, result.vectorsImported)
        verify(chunkPort).deleteByKnowledgeBaseId(eq(7L))
        verify(documentPort).deleteByKnowledgeBaseId(eq(7L))
        verify(sourcePort).deleteByKnowledgeBaseId(eq(7L))
        verify(sourcePort, org.mockito.kotlin.atLeastOnce()).save(any<KnowledgeSource>())
        verify(documentPort).save(any<KnowledgeDocument>())
        verify(chunkPort).save(any<KnowledgeChunk>())
    }

    @Test
    fun `MERGE mode keeps existing source by stableId and does not wipe the base`() {
        val base = existingBase()
        stubInspectAndRead(
            records =
                sequenceOf(
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-1",
                        name = "Source1",
                        type = "MANUAL",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 1,
                        active = true,
                    ),
                ),
        )
        val existingSource =
            KnowledgeSource(
                id = 88L,
                stableId = "src-1",
                knowledgeBaseId = 42L,
                name = "old",
                type = DomainSourceType.MANUAL,
                status = KnowledgeSourceStatus.READY,
                createdAt = now,
                updatedAt = now,
            )
        whenever(sourcePort.findByKnowledgeBaseId(42L)).thenReturn(listOf(existingSource))
        whenever(basePort.findById(42L)).thenReturn(base)
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(base))
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer {
            savedDocument(it.arguments[0] as KnowledgeDocument, 1L)
        }

        service.import(importRequest(KnowledgeImportMode.MERGE))

        verify(chunkPort, never()).deleteByKnowledgeBaseId(any<Long>())
        verify(documentPort, never()).deleteByKnowledgeBaseId(any<Long>())
        verify(sourcePort, never()).deleteByKnowledgeBaseId(any<Long>())
    }

    @Test
    fun `SKIP_EXISTING mode honours the existing source when the stableId matches`() {
        val base = existingBase()
        stubInspectAndRead(
            records =
                sequenceOf(
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-1",
                        name = "Source1",
                        type = "MANUAL",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 1,
                        active = true,
                    ),
                ),
        )
        val existingSource =
            KnowledgeSource(
                id = 88L,
                stableId = "src-1",
                knowledgeBaseId = 42L,
                name = "old",
                type = DomainSourceType.MANUAL,
                status = KnowledgeSourceStatus.READY,
                createdAt = now,
                updatedAt = now,
            )
        whenever(sourcePort.findByKnowledgeBaseId(42L)).thenReturn(listOf(existingSource))
        whenever(basePort.findById(42L)).thenReturn(base)
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(base))
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer {
            savedDocument(it.arguments[0] as KnowledgeDocument, 1L)
        }

        service.import(importRequest(KnowledgeImportMode.SKIP_EXISTING))

        verify(chunkPort, never()).deleteByKnowledgeBaseId(any<Long>())
        verify(documentPort, never()).deleteByKnowledgeBaseId(any<Long>())
        verify(sourcePort, never()).deleteByKnowledgeBaseId(any<Long>())
    }

    @Test
    fun `NEW_COPY creates a new base when the manifest base name is unknown`() {
        stubInspectAndRead(manifest = manifest(knowledgeBaseName = "Brand New Base"))
        val saved =
            KnowledgeBase(
                id = 7L,
                stableId = "kb-stable",
                name = "Brand New Base",
                description = "from archive",
                createdAt = now,
                updatedAt = now,
            )
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenReturn(saved)
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(emptyList())

        val result = service.import(importRequest(KnowledgeImportMode.NEW_COPY))

        assertEquals(7L, result.knowledgeBaseId)
        assertEquals(KnowledgeImportMode.NEW_COPY, result.mode)
    }

    @Test
    fun `NEW_COPY with an existing manifest name and a blank target name in the request goes through resolveTargetBase with the manifest name`() {
        stubInspectAndRead(manifest = manifest(knowledgeBaseName = "ExistingName"))
        val existing = existingBase(name = "ExistingName")
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(existing))
        whenever(sourcePort.findByKnowledgeBaseId(42L)).thenReturn(emptyList())

        val result = service.import(importRequest(KnowledgeImportMode.NEW_COPY))

        assertEquals(KnowledgeImportMode.REPLACE, result.mode)
        verify(chunkPort).deleteByKnowledgeBaseId(eq(42L))
    }

    @Test
    fun `resolveTargetBase falls back to the first existing base when no id or name matches`() {
        stubInspectAndRead(records = emptySequence())
        val fallbackBase = existingBase(id = 99L, name = "Other")
        val firstBase = existingBase(id = 1L, name = "First")
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(firstBase, fallbackBase))
        whenever(sourcePort.findByKnowledgeBaseId(any())).thenReturn(emptyList())

        val result = service.import(importRequest(KnowledgeImportMode.MERGE))

        assertEquals(1L, result.knowledgeBaseId)
        assertEquals("First", result.knowledgeBaseName)
    }

    @Test
    fun `resolveTargetBase creates a fresh base when none exists and no preferred name is provided`() {
        stubInspectAndRead(manifest = manifest(knowledgeBaseName = ""))
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer {
            KnowledgeBase(
                id = 5L,
                stableId = "kb-stable",
                name = "",
                description = "from archive",
                createdAt = now,
                updatedAt = now,
            )
        }
        whenever(sourcePort.findByKnowledgeBaseId(any())).thenReturn(emptyList())

        service.import(importRequest(KnowledgeImportMode.REPLACE))

        verify(basePort).save(any<KnowledgeBase>())
    }

    @Test
    fun `resolveTargetBase honours the caller-supplied targetKnowledgeBaseId`() {
        stubInspectAndRead(records = emptySequence())
        val target = existingBase(id = 88L, name = "Picked")
        whenever(basePort.findById(88L)).thenReturn(target)
        whenever(sourcePort.findByKnowledgeBaseId(88L)).thenReturn(emptyList())

        val result =
            service.import(
                KnowledgeImportRequest(
                    archiveBytes = archiveBytes(),
                    password = null,
                    mode = KnowledgeImportMode.REPLACE,
                    targetKnowledgeBaseId = 88L,
                ),
            )

        assertEquals(88L, result.knowledgeBaseId)
        assertEquals("Picked", result.knowledgeBaseName)
    }

    @Test
    fun `resolveTargetBase honours the caller-supplied targetKnowledgeBaseName when no id is provided`() {
        stubInspectAndRead(records = emptySequence())
        val target = existingBase(id = 77L, name = "Chosen")
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(target))
        whenever(sourcePort.findByKnowledgeBaseId(77L)).thenReturn(emptyList())

        val result =
            service.import(
                KnowledgeImportRequest(
                    archiveBytes = archiveBytes(),
                    password = null,
                    mode = KnowledgeImportMode.MERGE,
                    targetKnowledgeBaseName = "Chosen",
                ),
            )

        assertEquals(77L, result.knowledgeBaseId)
    }

    @Test
    fun `Document record skips when its source has not been seen`() {
        stubInspectAndRead(
            records =
                sequenceOf(
                    KnowledgeArchiveRecord.Document(
                        stableId = "doc-1",
                        sourceStableId = "missing-src",
                        externalId = null,
                        title = "Doc",
                        sourceUri = null,
                        mimeType = null,
                        plainText = "x",
                        contentHash = "h",
                        metadata = emptyMap(),
                    ),
                ),
        )
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer {
            KnowledgeBase(
                id = 7L,
                stableId = "kb-stable",
                name = "x",
                description = "x",
                createdAt = now,
                updatedAt = now,
            )
        }
        whenever(sourcePort.findByKnowledgeBaseId(any())).thenReturn(emptyList())

        val result = service.import(importRequest(KnowledgeImportMode.NEW_COPY))

        assertEquals(0, result.documentsImported)
        verify(documentPort, never()).save(any<KnowledgeDocument>())
    }

    @Test
    fun `Chunk record skips when its document has not been seen`() {
        stubInspectAndRead(
            records =
                sequenceOf(
                    KnowledgeArchiveRecord.Chunk(
                        stableId = "ch-1",
                        documentStableId = "missing-doc",
                        ordinal = 0,
                        sectionTitle = null,
                        content = "x",
                        contentHash = "h",
                        tokenEstimate = 0,
                        vectorBytes = null,
                        vectorDimension = null,
                    ),
                ),
        )
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer {
            KnowledgeBase(
                id = 7L,
                stableId = "kb-stable",
                name = "x",
                description = "x",
                createdAt = now,
                updatedAt = now,
            )
        }
        whenever(sourcePort.findByKnowledgeBaseId(any())).thenReturn(emptyList())

        val result = service.import(importRequest(KnowledgeImportMode.NEW_COPY))

        assertEquals(0, result.chunksImported)
        verify(chunkPort, never()).save(any<KnowledgeChunk>())
    }

    @Test
    fun `document metadata is serialized to a comma-separated string`() {
        stubInspectAndRead(
            records =
                sequenceOf(
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-1",
                        name = "s",
                        type = "MANUAL",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 1,
                        active = true,
                    ),
                    KnowledgeArchiveRecord.Document(
                        stableId = "doc-1",
                        sourceStableId = "src-1",
                        externalId = null,
                        title = "t",
                        sourceUri = null,
                        mimeType = null,
                        plainText = "x",
                        contentHash = "h",
                        metadata = mapOf("a" to "1", "b" to "2"),
                    ),
                ),
        )
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer {
            KnowledgeBase(
                id = 7L,
                stableId = "kb-stable",
                name = "x",
                description = "x",
                createdAt = now,
                updatedAt = now,
            )
        }
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(emptyList())
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer {
            savedSource(it.arguments[0] as KnowledgeSource, 1L)
        }
        var captured: KnowledgeDocument? = null
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer {
            captured = it.arguments[0] as KnowledgeDocument
            savedDocument(it.arguments[0] as KnowledgeDocument, 1L)
        }

        service.import(importRequest(KnowledgeImportMode.NEW_COPY))

        val md = captured!!.metadataJson
        assertNotNull(md)
        assertEquals(true, md!!.contains("a=1") && md.contains("b=2"))
    }

    @Test
    fun `empty metadata leaves metadataJson as null`() {
        stubInspectAndRead(
            records =
                sequenceOf(
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-1",
                        name = "s",
                        type = "MANUAL",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 1,
                        active = true,
                    ),
                    KnowledgeArchiveRecord.Document(
                        stableId = "doc-1",
                        sourceStableId = "src-1",
                        externalId = null,
                        title = "t",
                        sourceUri = null,
                        mimeType = null,
                        plainText = "x",
                        contentHash = "h",
                        metadata = emptyMap(),
                    ),
                ),
        )
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer {
            KnowledgeBase(
                id = 7L,
                stableId = "kb-stable",
                name = "x",
                description = "x",
                createdAt = now,
                updatedAt = now,
            )
        }
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(emptyList())
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer {
            savedSource(it.arguments[0] as KnowledgeSource, 1L)
        }
        var captured: KnowledgeDocument? = null
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer {
            captured = it.arguments[0] as KnowledgeDocument
            savedDocument(it.arguments[0] as KnowledgeDocument, 1L)
        }

        service.import(importRequest(KnowledgeImportMode.NEW_COPY))

        assertEquals(null, captured!!.metadataJson)
    }

    @Test
    fun `chunk without vector bytes does not count as a vector import`() {
        stubInspectAndRead(
            records =
                sequenceOf(
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-1",
                        name = "s",
                        type = "MANUAL",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 1,
                        active = true,
                    ),
                    KnowledgeArchiveRecord.Document(
                        stableId = "doc-1",
                        sourceStableId = "src-1",
                        externalId = null,
                        title = "t",
                        sourceUri = null,
                        mimeType = null,
                        plainText = "x",
                        contentHash = "h",
                        metadata = emptyMap(),
                    ),
                    KnowledgeArchiveRecord.Chunk(
                        stableId = "ch-1",
                        documentStableId = "doc-1",
                        ordinal = 0,
                        sectionTitle = null,
                        content = "y",
                        contentHash = "h",
                        tokenEstimate = 1,
                        vectorBytes = null,
                        vectorDimension = null,
                    ),
                ),
        )
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer {
            KnowledgeBase(
                id = 7L,
                stableId = "kb-stable",
                name = "x",
                description = "x",
                createdAt = now,
                updatedAt = now,
            )
        }
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(emptyList())
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer {
            savedSource(it.arguments[0] as KnowledgeSource, 1L)
        }
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer {
            savedDocument(it.arguments[0] as KnowledgeDocument, 2L)
        }
        whenever(chunkPort.save(any<KnowledgeChunk>())).thenAnswer {
            savedChunk(it.arguments[0] as KnowledgeChunk, 3L)
        }

        val result = service.import(importRequest(KnowledgeImportMode.NEW_COPY))

        assertEquals(1, result.chunksImported)
        assertEquals(0, result.vectorsImported)
    }

    @Test
    fun `unknown source type in the archive falls back to MANUAL`() {
        stubInspectAndRead(
            records =
                sequenceOf(
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-1",
                        name = "s",
                        type = "TOTALLY_UNKNOWN_TYPE",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 1,
                        active = true,
                    ),
                ),
        )
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer {
            KnowledgeBase(
                id = 7L,
                stableId = "kb-stable",
                name = "x",
                description = "x",
                createdAt = now,
                updatedAt = now,
            )
        }
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(emptyList())
        var captured: KnowledgeSource? = null
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer {
            captured = it.arguments[0] as KnowledgeSource
            savedSource(it.arguments[0] as KnowledgeSource, 1L)
        }
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer {
            savedDocument(it.arguments[0] as KnowledgeDocument, 1L)
        }

        service.import(importRequest(KnowledgeImportMode.NEW_COPY))

        assertEquals(DomainSourceType.MANUAL, captured!!.type)
    }

    @Test
    fun `post-persist hook updates source status, pushes chunks to the index and recovers when the index throws`() {
        stubInspectAndRead(
            records =
                sequenceOf(
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-1",
                        name = "Source1",
                        type = "MANUAL",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 1,
                        active = true,
                    ),
                    KnowledgeArchiveRecord.Document(
                        stableId = "doc-1",
                        sourceStableId = "src-1",
                        externalId = null,
                        title = "Doc1",
                        sourceUri = null,
                        mimeType = "text/plain",
                        plainText = "hello",
                        contentHash = "h",
                        metadata = emptyMap(),
                    ),
                    KnowledgeArchiveRecord.Chunk(
                        stableId = "ch-1",
                        documentStableId = "doc-1",
                        ordinal = 0,
                        sectionTitle = "intro",
                        content = "hello world",
                        contentHash = "h",
                        tokenEstimate = 2,
                        vectorBytes = ByteArray(8) { 0 },
                        vectorDimension = 2,
                    ),
                ),
        )
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer {
            KnowledgeBase(
                id = 7L,
                stableId = "kb-stable",
                name = "x",
                description = "x",
                createdAt = now,
                updatedAt = now,
            )
        }
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(emptyList())
        var capturedSource: KnowledgeSource? = null
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer {
            capturedSource = it.arguments[0] as KnowledgeSource
            savedSource(it.arguments[0] as KnowledgeSource, 1L)
        }
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer {
            savedDocument(it.arguments[0] as KnowledgeDocument, 2L)
        }
        val chunk =
            KnowledgeChunk(
                stableId = "ch-1",
                documentId = 2L,
                knowledgeBaseId = 7L,
                ordinal = 0,
                sectionTitle = "intro",
                content = "x",
                contentHash = "h",
                embeddingProfile = "openai:text-embedding-3-small",
                embeddingDimension = 2,
                embeddingBlob = ByteArray(8) { 0 },
                tokenEstimate = 1,
            )
        whenever(chunkPort.save(any<KnowledgeChunk>())).thenReturn(chunk)
        whenever(chunkPort.findByDocumentIdOrderByOrdinalAsc(2L)).thenReturn(listOf(chunk))
        whenever(indexPort.appendChunks(any(), any(), any())).thenThrow(RuntimeException("boom"))

        val result = service.import(importRequest(KnowledgeImportMode.NEW_COPY))

        verify(indexPort).appendChunks(eq(7L), eq("g0"), any<List<IndexedKnowledgeChunk>>())
        assertEquals(1, result.documentsImported)
    }

    @Test
    fun `manifest without embeddingProfile passes null into chunks`() {
        stubInspectAndRead(
            manifest = manifest(embeddingProfile = null),
            records =
                sequenceOf(
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-1",
                        name = "s",
                        type = "MANUAL",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 1,
                        active = true,
                    ),
                    KnowledgeArchiveRecord.Document(
                        stableId = "doc-1",
                        sourceStableId = "src-1",
                        externalId = null,
                        title = "t",
                        sourceUri = null,
                        mimeType = null,
                        plainText = "x",
                        contentHash = "h",
                        metadata = emptyMap(),
                    ),
                    KnowledgeArchiveRecord.Chunk(
                        stableId = "ch-1",
                        documentStableId = "doc-1",
                        ordinal = 0,
                        sectionTitle = null,
                        content = "y",
                        contentHash = "h",
                        tokenEstimate = 1,
                        vectorBytes = null,
                        vectorDimension = null,
                    ),
                ),
        )
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenAnswer {
            KnowledgeBase(
                id = 7L,
                stableId = "kb-stable",
                name = "x",
                description = "x",
                createdAt = now,
                updatedAt = now,
            )
        }
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(emptyList())
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer {
            savedSource(it.arguments[0] as KnowledgeSource, 1L)
        }
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer {
            savedDocument(it.arguments[0] as KnowledgeDocument, 1L)
        }
        var captured: KnowledgeChunk? = null
        whenever(chunkPort.save(any<KnowledgeChunk>())).thenAnswer {
            captured = it.arguments[0] as KnowledgeChunk
            savedChunk(it.arguments[0] as KnowledgeChunk, 1L)
        }

        service.import(importRequest(KnowledgeImportMode.NEW_COPY))

        assertEquals(null, captured!!.embeddingProfile)
    }

    @Test
    fun `manifest with a blank knowledgeBaseName does not collide with an existing base`() {
        stubInspectAndRead(manifest = manifest(knowledgeBaseName = ""))
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(existingBase(name = "preexisting")))
        val saved =
            KnowledgeBase(
                id = 7L,
                stableId = "kb-stable",
                name = "preexisting",
                description = "from archive",
                createdAt = now,
                updatedAt = now,
            )
        whenever(basePort.save(any<KnowledgeBase>())).thenReturn(saved)
        whenever(sourcePort.findByKnowledgeBaseId(7L)).thenReturn(emptyList())

        val result = service.import(importRequest(KnowledgeImportMode.NEW_COPY))

        assertEquals(KnowledgeImportMode.NEW_COPY, result.mode)
    }

    @Test
    fun `every new source only indexes the documents that belong to it`() {
        stubBaseFallback()
        stubInspectAndRead(
            records =
                sequenceOf(
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-1",
                        name = "Source1",
                        type = "MANUAL",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 1,
                        active = true,
                    ),
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-2",
                        name = "Source2",
                        type = "MANUAL",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 1,
                        active = true,
                    ),
                    KnowledgeArchiveRecord.Document(
                        stableId = "doc-1",
                        sourceStableId = "src-1",
                        externalId = null,
                        title = "Doc1",
                        sourceUri = null,
                        mimeType = "text/plain",
                        plainText = "hello",
                        contentHash = "h",
                        metadata = emptyMap(),
                    ),
                    KnowledgeArchiveRecord.Document(
                        stableId = "doc-2",
                        sourceStableId = "src-2",
                        externalId = null,
                        title = "Doc2",
                        sourceUri = null,
                        mimeType = "text/plain",
                        plainText = "world",
                        contentHash = "h",
                        metadata = emptyMap(),
                    ),
                ),
        )
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer { invocation ->
            val source = invocation.arguments[0] as KnowledgeSource
            savedSource(source, if (source.stableId == "src-1") 11L else 12L)
        }
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer { invocation ->
            val document = invocation.arguments[0] as KnowledgeDocument
            savedDocument(document, if (document.sourceId == 11L) 21L else 22L)
        }
        whenever(chunkPort.findByDocumentIdOrderByOrdinalAsc(21L)).thenReturn(emptyList())
        whenever(chunkPort.findByDocumentIdOrderByOrdinalAsc(22L)).thenReturn(emptyList())

        val result = service.import(importRequest(KnowledgeImportMode.REPLACE))

        assertEquals(2, result.documentsImported)
        verify(sourcePort, org.mockito.kotlin.atLeast(2)).save(any<KnowledgeSource>())
        verify(indexPort, never()).appendChunks(any(), any(), any())
    }
}
