package com.stacklens.application.port.out.knowledge

interface KnowledgeArchiveReaderPort {
    fun inspect(
        archiveBytes: ByteArray,
        password: String?,
    ): KnowledgeArchiveManifest

    fun read(
        archiveBytes: ByteArray,
        password: String?,
    ): Sequence<KnowledgeArchiveRecord>
}

data class KnowledgeArchiveManifest(
    val format: String,
    val version: Int,
    val exportedAt: String,
    val stacklensVersion: String,
    val knowledgeBaseStableId: String,
    val knowledgeBaseName: String,
    val knowledgeBaseDescription: String,
    val sourceCount: Int,
    val documentCount: Int,
    val chunkCount: Int,
    val containsVectors: Boolean,
    val embeddingProvider: String?,
    val embeddingModel: String?,
    val embeddingDimension: Int?,
    val embeddingProfile: String?,
)

sealed interface KnowledgeArchiveRecord {
    data class Source(
        val stableId: String,
        val name: String,
        val type: String,
        val rootUrl: String?,
        val externalConfigName: String?,
        val externalReference: String?,
        val includeChildren: Boolean,
        val includeComments: Boolean,
        val maxDepth: Int,
        val active: Boolean,
    ) : KnowledgeArchiveRecord

    data class Document(
        val stableId: String,
        val sourceStableId: String,
        val externalId: String?,
        val title: String,
        val sourceUri: String?,
        val mimeType: String?,
        val plainText: String,
        val contentHash: String,
        val metadata: Map<String, String>,
    ) : KnowledgeArchiveRecord

    data class Chunk(
        val stableId: String,
        val documentStableId: String,
        val ordinal: Int,
        val sectionTitle: String?,
        val content: String,
        val contentHash: String,
        val tokenEstimate: Int,
        val vectorBytes: ByteArray?,
        val vectorDimension: Int?,
    ) : KnowledgeArchiveRecord
}
