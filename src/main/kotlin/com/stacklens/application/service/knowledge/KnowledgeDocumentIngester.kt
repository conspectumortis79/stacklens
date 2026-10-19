package com.stacklens.application.service.knowledge

import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeHashingPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeChunk
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceStatus
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

@ApplicationService
class KnowledgeDocumentIngester(
    private val documentPort: KnowledgeDocumentPort,
    private val chunkPort: KnowledgeChunkPort,
    private val configPort: KnowledgeEmbeddingConfigPort,
    private val chunker: KnowledgeChunker,
    private val dispatcher: KnowledgeEmbeddingDispatcher,
    private val indexPort: KnowledgeIndexPort,
    private val hashing: KnowledgeHashingPort,
    private val clock: ClockPort,
) {
    private val log = LoggerFactory.getLogger(KnowledgeDocumentIngester::class.java)

    fun ingestDocument(
        source: KnowledgeSource,
        title: String,
        plainText: String,
        sourceUri: String?,
        sourceType: KnowledgeSourceType,
        mimeType: String?,
        metadata: Map<String, String>,
    ): KnowledgeDocumentStatus {
        val now = clock.localDateTime()
        val hash = hashing.sha256Hex(plainText)
        val document =
            documentPort.save(
                KnowledgeDocument(
                    sourceId = source.id,
                    knowledgeBaseId = source.knowledgeBaseId,
                    title = title,
                    sourceUri = sourceUri,
                    sourceType = sourceType,
                    mimeType = mimeType,
                    plainText = plainText,
                    contentHash = hash,
                    metadataJson = if (metadata.isEmpty()) null else metadata.entries.joinToString(",") { "${it.key}=${it.value}" },
                    status = KnowledgeDocumentStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        val config = configPort.get()
        val rawChunks = chunker.chunk(plainText, chunkSize = config.chunkSize, chunkOverlap = config.chunkOverlap)
        if (rawChunks.isEmpty()) {
            document.status = KnowledgeDocumentStatus.READY
            document.indexedAt = now
            documentPort.save(document)
            return document.status
        }
        val result =
            try {
                dispatcher.embed(rawChunks.map { it.content })
            } catch (e: Exception) {
                log.warn("Embedding failed for document {}: {}", document.id, e.message)
                document.status = KnowledgeDocumentStatus.FAILED
                documentPort.save(document)
                return document.status
            }
        val chunks =
            rawChunks.mapIndexed { index, raw ->
                KnowledgeChunk(
                    documentId = document.id,
                    knowledgeBaseId = document.knowledgeBaseId,
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
        chunkPort.deleteByDocumentId(document.id)
        chunks.forEach { chunkPort.save(it) }
        runCatching {
            indexPort.appendChunks(
                knowledgeBaseId = document.knowledgeBaseId,
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
                            ticketKey = metadata["ticketKey"],
                            sprintReference = metadata["sprintReference"],
                        )
                    },
            )
        }.onFailure { log.warn("Failed to push chunks into Lucene index for document {}: {}", document.id, it.message) }
        document.status = KnowledgeDocumentStatus.READY
        document.indexedAt = now
        documentPort.save(document)
        return document.status
    }

    fun applyOutcome(
        source: KnowledgeSource,
        now: LocalDateTime,
        anyDocFailed: Boolean,
    ) {
        if (anyDocFailed) {
            source.status = KnowledgeSourceStatus.FAILED
            source.errorMessage =
                if (source.errorMessage.isNullOrBlank()) {
                    "Embedding schlug für mindestens ein Dokument fehl (siehe Logs / Document-Status)."
                } else {
                    source.errorMessage
                }
            return
        }
        source.status = KnowledgeSourceStatus.READY
        source.errorMessage = null
        source.lastSuccessfulSyncAt = now
    }
}
