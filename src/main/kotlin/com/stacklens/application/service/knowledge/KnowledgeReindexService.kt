package com.stacklens.application.service.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeReindexUseCase
import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeHashingPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@ApplicationService
class KnowledgeReindexService(
    private val basePort: KnowledgeBasePort,
    private val chunkPort: KnowledgeChunkPort,
    private val documentPort: KnowledgeDocumentPort,
    private val indexPort: KnowledgeIndexPort,
    private val hashing: KnowledgeHashingPort,
    private val clock: ClockPort,
) : KnowledgeReindexUseCase {
    private val log = LoggerFactory.getLogger(KnowledgeReindexService::class.java)

    override fun reindexAll(): List<KnowledgeReindexUseCase.ReindexResult> = basePort.findAllByOrderByCreatedAtAsc().mapNotNull { reindexOne(it) }

    fun reindexBase(knowledgeBaseId: Long): KnowledgeReindexUseCase.ReindexResult? {
        val base = basePort.findById(knowledgeBaseId) ?: return null
        return reindexOne(base)
    }

    private fun reindexOne(base: KnowledgeBase): KnowledgeReindexUseCase.ReindexResult? {
        val chunks = chunkPort.findByKnowledgeBaseId(base.id)
        if (chunks.isEmpty()) {
            log.info("Reindex: base {} ({}) has no chunks, skipping", base.id, base.name)
            return KnowledgeReindexUseCase.ReindexResult(base.id, base.name, chunkCount = 0)
        }

        val documentsById: Map<Long, KnowledgeDocument> =
            chunks
                .asSequence()
                .map { it.documentId }
                .toSet()
                .mapNotNull { documentPort.findById(it) }
                .associateBy { it.id }
        val indexed =
            chunks.mapNotNull { chunk ->
                val doc = documentsById[chunk.documentId] ?: return@mapNotNull null
                IndexedKnowledgeChunk(
                    chunkId = chunk.id,
                    documentId = chunk.documentId,
                    knowledgeBaseId = base.id,
                    title = doc.title,
                    sectionTitle = chunk.sectionTitle,
                    content = chunk.content,
                    sourceType = doc.sourceType.name,
                    sourceUri = doc.sourceUri,
                    embedding = chunk.embeddingBlob?.let { hashing.bytesToFloatArray(it) },
                    score = 0.0,
                    citation = "KB:document-${chunk.documentId}#chunk-${chunk.ordinal}",
                    ticketKey = doc.metadataJson?.parseMetadataField("ticketKey"),
                    sprintReference = doc.metadataJson?.parseMetadataField("sprintReference"),
                )
            }

        val generationId = "g0-regen-${clock.epochMillis()}"
        indexPort.replaceAllChunks(base.id, generationId, indexed)
        log.info("Reindex: base {} ({}) re-emitted {} chunks", base.id, base.name, indexed.size)
        return KnowledgeReindexUseCase.ReindexResult(base.id, base.name, chunkCount = indexed.size)
    }
}

internal fun LocalDateTime.toInstant(): Instant = atZone(ZoneId.systemDefault()).toInstant()

private fun String.parseMetadataField(key: String): String? =
    split(',')
        .mapNotNull { entry ->
            val parts = entry.split('=', limit = 2)
            if (parts.size == 2 && parts[0] == key) parts[1] else null
        }.firstOrNull()
        ?.takeIf { it.isNotBlank() }
