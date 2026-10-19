package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeHashingPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.knowledge.KnowledgePublicationCandidate
import com.stacklens.application.service.knowledge.KnowledgePublicationReference
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ApplicationTransactional
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeChunk
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import org.slf4j.LoggerFactory

@ApplicationService
class KnowledgePublicationExecutor(
    private val basePort: KnowledgeBasePort,
    private val sourcePort: KnowledgeSourcePort,
    private val documentPort: KnowledgeDocumentPort,
    private val chunkPort: KnowledgeChunkPort,
    private val indexPort: KnowledgeIndexPort,
    private val chunker: KnowledgeChunker,
    private val dispatcher: KnowledgeEmbeddingDispatcher,
    private val configPort: KnowledgeEmbeddingConfigPort,
    private val hashing: KnowledgeHashingPort,
    private val clock: ClockPort,
) : KnowledgePublication {
    private val log = LoggerFactory.getLogger(KnowledgePublicationExecutor::class.java)

    @ApplicationTransactional
    override fun publish(candidate: KnowledgePublicationCandidate) {
        val config = configPort.get()
        val enabled = config.autoIndexFor(candidate.sourceType)
        if (!enabled) {
            log.debug("Knowledge publication skipped (auto-index disabled) for ${candidate.sourceType}")
            return
        }
        if (candidate.content.isBlank()) {
            log.debug("Knowledge publication skipped (empty content) for ${candidate.sourceType}")
            return
        }
        val baseId =
            candidate.knowledgeBaseId
                ?: basePort
                    .findAllByOrderByCreatedAtAsc()
                    .firstOrNull { it.systemManaged }
                    ?.id
                ?: basePort.findAllByOrderByCreatedAtAsc().firstOrNull()?.id
                ?: basePort
                    .save(
                        KnowledgeBase(
                            name = "StackLens-Analyseergebnisse",
                            systemManaged = true,
                        ),
                    ).id
        val existing =
            sourcePort.findFirstByTypeAndExternalReference(candidate.sourceType, candidate.externalId)
        val source =
            existing
                ?: sourcePort.save(
                    KnowledgeSource(
                        knowledgeBaseId = baseId,
                        name = candidate.title,
                        type = candidate.sourceType,
                        active = true,
                        externalReference = candidate.externalId,
                        autoManaged = true,
                        status = KnowledgeSourceStatus.PENDING,
                    ),
                )
        val documents = documentPort.findBySourceIdOrderByIdAsc(source.id)
        for (doc in documents) {
            chunkPort.deleteByDocumentId(doc.id)
            indexPort.removeDocument(doc.knowledgeBaseId, doc.id)
        }
        documentPort.deleteBySourceId(source.id)
        source.status = KnowledgeSourceStatus.PENDING
        source.errorMessage = null
        source.lastSyncAt = clock.localDateTime()
        sourcePort.save(source)

        val now = clock.localDateTime()
        val document =
            documentPort.save(
                KnowledgeDocument(
                    sourceId = source.id,
                    knowledgeBaseId = baseId,
                    externalId = candidate.externalId,
                    title = candidate.title,
                    sourceUri = candidate.sourceUri,
                    sourceType = candidate.sourceType,
                    mimeType = "text/markdown",
                    plainText = candidate.content,
                    contentHash = hashing.sha256Hex(candidate.content),
                    metadataJson = candidate.metadata.entries.joinToString(",") { "${it.key}=${it.value}" },
                    status = KnowledgeDocumentStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        val rawChunks = chunker.chunk(candidate.content, chunkSize = config.chunkSize, chunkOverlap = config.chunkOverlap)
        if (rawChunks.isEmpty()) {
            document.status = KnowledgeDocumentStatus.READY
            document.indexedAt = clock.localDateTime()
            documentPort.save(document)
            source.status = KnowledgeSourceStatus.READY
            sourcePort.save(source)
            return
        }
        val result = dispatcher.embed(rawChunks.map { it.content })
        val chunks =
            rawChunks.mapIndexed { index, raw ->
                KnowledgeChunk(
                    documentId = document.id,
                    knowledgeBaseId = baseId,
                    ordinal = raw.ordinal,
                    sectionTitle = raw.sectionTitle,
                    content = raw.content,
                    contentHash = hashing.sha256Hex(raw.content),
                    embeddingProfile = result.profile,
                    embeddingDimension = result.dimension,
                    embeddingBlob = hashing.floatArrayToBytes(result.vectors[index]),
                    embeddingNorm = hashing.norm(result.vectors[index]),
                    tokenEstimate = raw.content.length / 4,
                )
            }
        chunks.forEach { chunkPort.save(it) }
        runCatching {
            indexPort.appendChunks(
                knowledgeBaseId = baseId,
                generationId = "g0",
                chunks =
                    chunks.map { chunk ->
                        com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk(
                            chunkId = chunk.id,
                            documentId = chunk.documentId,
                            knowledgeBaseId = chunk.knowledgeBaseId,
                            title = document.title,
                            sectionTitle = chunk.sectionTitle,
                            content = chunk.content,
                            sourceType = document.sourceType.name,
                            sourceUri = document.sourceUri,
                            embedding = chunk.embeddingBlob?.let(hashing::bytesToFloatArray),
                            score = 0.0,
                            citation = "KB:document-${document.id}#chunk-${chunk.ordinal}",
                        )
                    },
            )
        }.onFailure { log.warn("Failed to push chunks into Lucene index for document {}: {}", document.id, it.message) }
        document.status = KnowledgeDocumentStatus.READY
        document.indexedAt = clock.localDateTime()
        documentPort.save(document)
        source.status = KnowledgeSourceStatus.READY
        source.lastSuccessfulSyncAt = clock.localDateTime()
        sourcePort.save(source)
    }

    @ApplicationTransactional
    override fun remove(reference: KnowledgePublicationReference) {
        val source =
            sourcePort.findFirstByTypeAndExternalReference(reference.sourceType, reference.externalId)
                ?: return
        val documents = documentPort.findBySourceIdOrderByIdAsc(source.id)
        for (doc in documents) {
            chunkPort.deleteByDocumentId(doc.id)
            indexPort.removeDocument(doc.knowledgeBaseId, doc.id)
        }
        documentPort.deleteBySourceId(source.id)
        sourcePort.deleteById(source.id)
    }
}
