package com.stacklens.application.service.knowledge

import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportRequest
import com.stacklens.application.port.model.KnowledgeImportResult
import com.stacklens.application.port.out.IdGeneratorPort
import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveManifest
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveReaderPort
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeHashingPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeChunk
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import org.slf4j.LoggerFactory
import com.stacklens.domain.model.knowledge.KnowledgeSourceType as DomainSourceType

@ApplicationService
class KnowledgeArchivePersistence(
    private val basePort: KnowledgeBasePort,
    private val sourcePort: KnowledgeSourcePort,
    private val documentPort: KnowledgeDocumentPort,
    private val chunkPort: KnowledgeChunkPort,
    private val readerPort: KnowledgeArchiveReaderPort,
    private val indexPort: KnowledgeIndexPort,
    private val hashing: KnowledgeHashingPort,
    private val clock: ClockPort,
    private val idGenerator: IdGeneratorPort,
) {
    private val log = LoggerFactory.getLogger(KnowledgeArchivePersistence::class.java)

    fun import(request: KnowledgeImportRequest): KnowledgeImportResult {
        val manifest = readerPort.inspect(request.archiveBytes, request.password)
        val effectiveRequest = promoteDuplicateNameImport(request, manifest)
        val targetBase = resolveTargetBase(effectiveRequest, manifest)

        if (effectiveRequest.mode == KnowledgeImportMode.REPLACE) {
            wipeBase(targetBase)
        }
        val sourcesByStableId = sourcePort.findByKnowledgeBaseId(targetBase.id).associateBy { it.stableId }
        val (docs, chunks, vectors) =
            persist(
                targetBase = targetBase,
                request = effectiveRequest,
                manifest = manifest,
                existingSourcesByStableId = sourcesByStableId,
            )
        return KnowledgeImportResult(
            knowledgeBaseId = targetBase.id,
            knowledgeBaseName = targetBase.name,
            documentsImported = docs,
            chunksImported = chunks,
            vectorsImported = vectors,
            mode = effectiveRequest.mode,
        )
    }

    private fun promoteDuplicateNameImport(
        request: KnowledgeImportRequest,
        manifest: KnowledgeArchiveManifest,
    ): KnowledgeImportRequest {
        if (request.mode != KnowledgeImportMode.NEW_COPY) return request
        if (manifest.knowledgeBaseName.isBlank()) return request
        val exists = basePort.findAllByOrderByCreatedAtAsc().any { it.name == manifest.knowledgeBaseName }
        if (!exists) return request
        return request.copy(mode = KnowledgeImportMode.REPLACE)
    }

    private fun resolveTargetBase(
        request: KnowledgeImportRequest,
        manifest: KnowledgeArchiveManifest,
    ): KnowledgeBase =
        when (request.mode) {
            KnowledgeImportMode.NEW_COPY -> {
                val now = clock.localDateTime()
                val name =
                    if (basePort.findAllByOrderByCreatedAtAsc().any { it.name == manifest.knowledgeBaseName }) {
                        "${manifest.knowledgeBaseName} (Import)"
                    } else {
                        manifest.knowledgeBaseName
                    }
                basePort.save(
                    KnowledgeBase(
                        name = name,
                        description = manifest.knowledgeBaseDescription,
                        includeInGlobalChat = true,
                        systemManaged = false,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            KnowledgeImportMode.REPLACE,
            KnowledgeImportMode.MERGE,
            KnowledgeImportMode.SKIP_EXISTING,
            -> {
                val preferredName = request.targetKnowledgeBaseName ?: manifest.knowledgeBaseName
                val byId = request.targetKnowledgeBaseId?.let { basePort.findById(it) }
                val byName =
                    if (byId == null && preferredName.isNotBlank()) {
                        basePort.findAllByOrderByCreatedAtAsc().firstOrNull { it.name == preferredName }
                    } else {
                        null
                    }
                byId
                    ?: byName
                    ?: basePort.findAllByOrderByCreatedAtAsc().firstOrNull()
                    ?: basePort.save(
                        KnowledgeBase(
                            name = preferredName,
                            description = manifest.knowledgeBaseDescription,
                            includeInGlobalChat = true,
                            systemManaged = false,
                        ),
                    )
            }
        }

    private fun wipeBase(target: KnowledgeBase) {
        chunkPort.deleteByKnowledgeBaseId(target.id)
        documentPort.deleteByKnowledgeBaseId(target.id)
        sourcePort.deleteByKnowledgeBaseId(target.id)
    }

    private fun persist(
        targetBase: KnowledgeBase,
        request: KnowledgeImportRequest,
        manifest: KnowledgeArchiveManifest,
        existingSourcesByStableId: Map<String, KnowledgeSource>,
    ): Triple<Int, Int, Int> {
        val now = clock.localDateTime()
        val sourcesByStableId = HashMap<String, KnowledgeSource>()
        val newSources = mutableListOf<KnowledgeSource>()
        val documentsByStableId = HashMap<String, KnowledgeDocument>()
        var documentsImported = 0
        var chunksImported = 0
        var vectorsImported = 0
        val rewriteIds = request.mode == KnowledgeImportMode.NEW_COPY
        for (record in readerPort.read(request.archiveBytes, request.password)) {
            when (record) {
                is KnowledgeArchiveRecord.Source -> {
                    val newSourceStableId = if (rewriteIds) idGenerator.randomId() else record.stableId
                    val existing =
                        if (request.mode == KnowledgeImportMode.REPLACE) {
                            null
                        } else {
                            existingSourcesByStableId[record.stableId]
                        }
                    val source =
                        existing
                            ?: sourcePort
                                .save(
                                    KnowledgeSource(
                                        stableId = newSourceStableId,
                                        knowledgeBaseId = targetBase.id,
                                        name = record.name,
                                        type =
                                            runCatching { DomainSourceType.valueOf(record.type) }
                                                .getOrDefault(DomainSourceType.MANUAL),
                                        active = record.active,
                                        rootUrl = record.rootUrl,
                                        externalReference = record.externalReference,
                                        includeChildren = record.includeChildren,
                                        includeComments = record.includeComments,
                                        maxDepth = record.maxDepth,
                                        autoManaged = false,
                                        status = KnowledgeSourceStatus.PENDING,
                                        createdAt = now,
                                        updatedAt = now,
                                    ),
                                ).also { newSources += it }
                    sourcesByStableId[record.stableId] = source
                }
                is KnowledgeArchiveRecord.Document -> {
                    val source = sourcesByStableId[record.sourceStableId] ?: continue
                    val newDocumentStableId = if (rewriteIds) idGenerator.randomId() else record.stableId
                    val document =
                        documentPort.save(
                            KnowledgeDocument(
                                stableId = newDocumentStableId,
                                sourceId = source.id,
                                knowledgeBaseId = targetBase.id,
                                externalId = record.externalId,
                                title = record.title,
                                sourceUri = record.sourceUri,
                                sourceType = source.type,
                                mimeType = record.mimeType,
                                plainText = record.plainText,
                                contentHash = record.contentHash,
                                metadataJson = if (record.metadata.isEmpty()) null else record.metadata.entries.joinToString(",") { "${it.key}=${it.value}" },
                                status = KnowledgeDocumentStatus.READY,
                                createdAt = now,
                                updatedAt = now,
                                indexedAt = now,
                            ),
                        )
                    documentsByStableId[record.stableId] = document
                    documentsImported++
                }
                is KnowledgeArchiveRecord.Chunk -> {
                    val document = documentsByStableId[record.documentStableId] ?: continue
                    val newChunkStableId = if (rewriteIds) idGenerator.randomId() else record.stableId
                    val chunk =
                        chunkPort.save(
                            KnowledgeChunk(
                                stableId = newChunkStableId,
                                documentId = document.id,
                                knowledgeBaseId = targetBase.id,
                                ordinal = record.ordinal,
                                sectionTitle = record.sectionTitle,
                                content = record.content,
                                contentHash = record.contentHash,
                                embeddingProfile = manifest.embeddingProfile,
                                embeddingDimension = record.vectorDimension,
                                embeddingBlob = record.vectorBytes,
                                tokenEstimate = record.tokenEstimate,
                            ),
                        )
                    chunksImported++
                    if (record.vectorBytes != null) vectorsImported++
                }
            }
        }
        newSources.forEach { source ->
            source.status = KnowledgeSourceStatus.READY
            source.lastSyncAt = now
            source.lastSuccessfulSyncAt = now
            source.errorMessage = null
            sourcePort.save(source)
            val sourceChunks =
                documentsByStableId
                    .filterValues { it.sourceId == source.id }
                    .flatMap { (_, document) -> chunkPort.findByDocumentIdOrderByOrdinalAsc(document.id) }
            if (sourceChunks.isNotEmpty()) {
                runCatching {
                    indexPort.appendChunks(
                        knowledgeBaseId = targetBase.id,
                        generationId = "g0",
                        chunks =
                            sourceChunks.map { chunk ->
                                IndexedKnowledgeChunk(
                                    chunkId = chunk.id,
                                    documentId = chunk.documentId,
                                    knowledgeBaseId = chunk.knowledgeBaseId,
                                    title = source.name,
                                    sectionTitle = chunk.sectionTitle,
                                    content = chunk.content,
                                    sourceType = source.type.name,
                                    sourceUri = source.externalReference,
                                    embedding = chunk.embeddingBlob?.let(hashing::bytesToFloatArray),
                                    score = 0.0,
                                    citation = "KB:document-${chunk.documentId}#chunk-${chunk.ordinal}",
                                )
                            },
                    )
                }.onFailure { log.warn("Failed to push imported chunks into Lucene for source {}: {}", source.id, it.message) }
            }
        }
        log.info(
            "Imported knowledge archive '{}' into base {} ({} docs, {} chunks, {} vectors)",
            manifest.knowledgeBaseName,
            targetBase.id,
            documentsImported,
            chunksImported,
            vectorsImported,
        )
        return Triple(documentsImported, chunksImported, vectorsImported)
    }
}
