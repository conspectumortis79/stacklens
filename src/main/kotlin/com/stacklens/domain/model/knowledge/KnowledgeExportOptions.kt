package com.stacklens.domain.model.knowledge

data class KnowledgeExportOptions(
    val includeVectors: Boolean = true,
    val includeSourceDefinitions: Boolean = true,
    val includeEmbeddingProfile: Boolean = true,
)
