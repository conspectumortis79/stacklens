package com.stacklens.application.port.out.knowledge

import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingProvider

data class EmbeddingListingOverrides(
    val baseUrl: String?,
    val apiKey: String?,
)

interface EmbeddingPort {
    fun provider(): KnowledgeEmbeddingProvider

    fun embed(
        config: KnowledgeEmbeddingConfig,
        texts: List<String>,
    ): List<FloatArray>

    fun availableModels(
        config: KnowledgeEmbeddingConfig,
        overrides: EmbeddingListingOverrides,
    ): List<String>

    fun resolvedBaseUrl(config: KnowledgeEmbeddingConfig): String
}
