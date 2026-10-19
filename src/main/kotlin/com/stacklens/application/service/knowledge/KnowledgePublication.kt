package com.stacklens.application.service.knowledge

import com.stacklens.domain.model.knowledge.KnowledgeSourceType

data class KnowledgePublicationCandidate(
    val sourceType: KnowledgeSourceType,
    val externalId: String,
    val title: String,
    val sourceUri: String?,
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
    val knowledgeBaseId: Long? = null,
)

data class KnowledgePublicationReference(
    val sourceType: KnowledgeSourceType,
    val externalId: String,
)

interface KnowledgePublication {
    fun publish(candidate: KnowledgePublicationCandidate)

    fun remove(reference: KnowledgePublicationReference)
}
