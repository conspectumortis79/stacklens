package com.stacklens.application.port.out.knowledge

import com.stacklens.domain.model.knowledge.KnowledgeExportOptions

interface KnowledgeArchiveWriterPort {
    fun write(
        snapshot: KnowledgeBaseSnapshot,
        options: KnowledgeExportOptions,
        password: String,
    ): ByteArray
}

data class KnowledgeBaseSnapshot(
    val knowledgeBaseStableId: String,
    val knowledgeBaseName: String,
    val knowledgeBaseDescription: String,
    val sources: List<KnowledgeArchiveRecord.Source>,
    val documents: List<KnowledgeArchiveRecord.Document>,
    val chunks: List<KnowledgeArchiveRecord.Chunk>,
    val embeddingProvider: String?,
    val embeddingModel: String?,
    val embeddingDimension: Int?,
    val embeddingProfile: String?,
)
