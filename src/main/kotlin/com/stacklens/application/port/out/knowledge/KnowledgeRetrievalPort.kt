package com.stacklens.application.port.out.knowledge

import com.stacklens.domain.model.knowledge.RetrievedKnowledge

interface KnowledgeRetrievalPort {
    fun retrieveForChat(
        query: String,
        limit: Int?,
    ): List<RetrievedKnowledge>
}
