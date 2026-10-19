package com.stacklens.application.port.inbound.knowledge

interface KnowledgeReindexUseCase {
    data class ReindexResult(
        val knowledgeBaseId: Long,
        val knowledgeBaseName: String,
        val chunkCount: Int,
    )

    fun reindexAll(): List<ReindexResult>
}
