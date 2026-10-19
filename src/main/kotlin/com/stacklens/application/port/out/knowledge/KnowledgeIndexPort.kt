package com.stacklens.application.port.out.knowledge

interface KnowledgeIndexPort {
    fun searchByVector(
        knowledgeBaseId: Long,
        queryVector: FloatArray,
        limit: Int,
    ): List<IndexedKnowledgeChunk>

    fun searchByTicketKey(
        knowledgeBaseId: Long,
        ticketKey: String,
    ): List<IndexedKnowledgeChunk>

    fun replaceAllChunks(
        knowledgeBaseId: Long,
        generationId: String,
        chunks: List<IndexedKnowledgeChunk>,
    )

    fun appendChunks(
        knowledgeBaseId: Long,
        generationId: String,
        chunks: List<IndexedKnowledgeChunk>,
    )

    fun removeDocument(
        knowledgeBaseId: Long,
        documentId: Long,
    )

    fun clearKnowledgeBase(knowledgeBaseId: Long)

    fun activeGeneration(knowledgeBaseId: Long): String?
}

data class IndexedKnowledgeChunk(
    val chunkId: Long,
    val documentId: Long,
    val knowledgeBaseId: Long,
    val title: String,
    val sectionTitle: String?,
    val content: String,
    val sourceType: String,
    val sourceUri: String?,
    val embedding: FloatArray?,
    val score: Double,
    val citation: String,
    val ticketKey: String? = null,
    val sprintReference: String? = null,
)
