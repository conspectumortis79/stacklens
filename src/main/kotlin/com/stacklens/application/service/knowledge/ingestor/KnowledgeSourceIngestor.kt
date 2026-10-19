package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType

interface KnowledgeSourceIngestor {
    fun supports(type: KnowledgeSourceType): Boolean

    fun ingest(source: KnowledgeSource): KnowledgeSourceIngestionOutcome
}

data class KnowledgeSourceIngestionOutcome(
    val ingestedCount: Int,
    val anyDocFailed: Boolean,
)
