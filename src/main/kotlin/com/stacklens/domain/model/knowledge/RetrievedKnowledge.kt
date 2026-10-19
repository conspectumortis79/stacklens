package com.stacklens.domain.model.knowledge

data class RetrievedKnowledge(
    val chunkId: Long,
    val documentId: Long,
    val knowledgeBaseId: Long,
    val title: String,
    val sourceType: KnowledgeSourceType,
    val sourceUri: String?,
    val content: String,
    val score: Double,
    val citation: String,
)
