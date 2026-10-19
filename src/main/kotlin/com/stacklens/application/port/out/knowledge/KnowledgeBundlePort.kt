package com.stacklens.application.port.out.knowledge

interface KnowledgeBundlePort {
    fun inspect(bundleBytes: ByteArray): List<KnowledgeBundleEntry>

    fun readEntry(
        bundleBytes: ByteArray,
        entryName: String,
    ): ByteArray

    fun write(archives: List<KnowledgeBundleArchive>): ByteArray
}

data class KnowledgeBundleArchive(
    val entryName: String,
    val displayName: String,
    val knowledgeBaseStableId: String,
    val knowledgeBaseName: String,
    val knowledgeBaseDescription: String,
    val sourceCount: Int,
    val documentCount: Int,
    val chunkCount: Int,
    val containsVectors: Boolean,
    val bytes: ByteArray,
) {
    init {
        require(entryName.isNotBlank()) { "Bundle entry name must not be blank" }
        require(bytes.isNotEmpty()) { "Bundle archive bytes must not be empty" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnowledgeBundleArchive) return false
        return entryName == other.entryName &&
            displayName == other.displayName &&
            knowledgeBaseStableId == other.knowledgeBaseStableId &&
            knowledgeBaseName == other.knowledgeBaseName &&
            knowledgeBaseDescription == other.knowledgeBaseDescription &&
            sourceCount == other.sourceCount &&
            documentCount == other.documentCount &&
            chunkCount == other.chunkCount &&
            containsVectors == other.containsVectors &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = entryName.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + knowledgeBaseStableId.hashCode()
        result = 31 * result + knowledgeBaseName.hashCode()
        result = 31 * result + knowledgeBaseDescription.hashCode()
        result = 31 * result + sourceCount
        result = 31 * result + documentCount
        result = 31 * result + chunkCount
        result = 31 * result + containsVectors.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

data class KnowledgeBundleEntry(
    val entryName: String,
    val displayName: String,
    val knowledgeBaseStableId: String,
    val knowledgeBaseName: String,
    val knowledgeBaseDescription: String,
    val sourceCount: Int,
    val documentCount: Int,
    val chunkCount: Int,
    val containsVectors: Boolean,
)
