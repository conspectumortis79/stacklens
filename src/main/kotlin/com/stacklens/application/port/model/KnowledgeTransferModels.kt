package com.stacklens.application.port.model

import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider

enum class KnowledgeImportMode {
    NEW_COPY,
    MERGE,
    REPLACE,
    SKIP_EXISTING,
}

data class KnowledgeImportRequest(
    val archiveBytes: ByteArray,
    val password: String?,
    val mode: KnowledgeImportMode = KnowledgeImportMode.NEW_COPY,
    val targetKnowledgeBaseId: Long? = null,
    val targetKnowledgeBaseName: String? = null,
    val includeVectors: Boolean = true,
)

data class KnowledgeImportPreview(
    val knowledgeBaseName: String,
    val sourceCount: Int,
    val documentCount: Int,
    val chunkCount: Int,
    val containsVectors: Boolean,
    val embeddingProvider: KnowledgeEmbeddingProvider?,
    val embeddingModel: String?,
    val embeddingDimension: Int?,
)

data class KnowledgeImportResult(
    val knowledgeBaseId: Long,
    val knowledgeBaseName: String,
    val documentsImported: Int,
    val chunksImported: Int,
    val vectorsImported: Int,
    val mode: KnowledgeImportMode,
)
