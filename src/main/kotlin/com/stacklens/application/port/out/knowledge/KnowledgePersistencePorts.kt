package com.stacklens.application.port.out.knowledge

import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeChunk
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeIngestionJob
import com.stacklens.domain.model.knowledge.KnowledgeSource

interface KnowledgeBasePort {
    fun findAllByOrderByCreatedAtAsc(): List<KnowledgeBase>

    fun findAllByActiveTrue(): List<KnowledgeBase>

    fun findById(id: Long): KnowledgeBase?

    fun findByStableId(stableId: String): KnowledgeBase?

    fun save(entity: KnowledgeBase): KnowledgeBase

    fun deleteById(id: Long)

    fun deleteAll()
}

interface KnowledgeSourcePort {
    fun findAllByOrderByCreatedAtAsc(): List<KnowledgeSource>

    fun findById(id: Long): KnowledgeSource?

    fun findByStableId(stableId: String): KnowledgeSource?

    fun findByKnowledgeBaseId(knowledgeBaseId: Long): List<KnowledgeSource>

    fun findFirstByTypeAndExternalReference(
        type: com.stacklens.domain.model.knowledge.KnowledgeSourceType,
        externalReference: String,
    ): KnowledgeSource?

    fun save(entity: KnowledgeSource): KnowledgeSource

    fun deleteById(id: Long)

    fun deleteByKnowledgeBaseId(knowledgeBaseId: Long)

    fun deleteAll()
}

interface KnowledgeDocumentPort {
    fun findById(id: Long): KnowledgeDocument?

    fun findByStableId(stableId: String): KnowledgeDocument?

    fun findBySourceIdOrderByIdAsc(sourceId: Long): List<KnowledgeDocument>

    fun findByKnowledgeBaseId(knowledgeBaseId: Long): List<KnowledgeDocument>

    fun save(entity: KnowledgeDocument): KnowledgeDocument

    fun deleteById(id: Long)

    fun deleteBySourceId(sourceId: Long): Int

    fun deleteByKnowledgeBaseId(knowledgeBaseId: Long)

    fun deleteAll()
}

interface KnowledgeChunkPort {
    fun findByDocumentIdOrderByOrdinalAsc(documentId: Long): List<KnowledgeChunk>

    fun findByKnowledgeBaseId(knowledgeBaseId: Long): List<KnowledgeChunk>

    fun save(entity: KnowledgeChunk): KnowledgeChunk

    fun deleteById(id: Long)

    fun deleteByDocumentId(documentId: Long)

    fun deleteByKnowledgeBaseId(knowledgeBaseId: Long)

    fun deleteAll()

    fun sumContentLengthBySourceIds(sourceIds: Collection<Long>): Map<Long, Long>
}

interface KnowledgeEmbeddingConfigPort {
    fun get(): KnowledgeEmbeddingConfig

    fun save(entity: KnowledgeEmbeddingConfig): KnowledgeEmbeddingConfig

    fun delete()
}

interface KnowledgeIngestionJobPort {
    fun findAllByOrderByCreatedAtDesc(): List<KnowledgeIngestionJob>

    fun findById(id: Long): KnowledgeIngestionJob?

    fun save(entity: KnowledgeIngestionJob): KnowledgeIngestionJob

    fun deleteById(id: Long)

    fun deleteAll()
}

interface KnowledgeIndexGenerationPort {
    fun deleteByKnowledgeBaseId(knowledgeBaseId: Long)

    fun deleteAll()
}
