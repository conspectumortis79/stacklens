package com.stacklens.application.port.out.ai

import com.stacklens.domain.model.knowledge.RetrievedKnowledge

interface KnowledgeRerankPort {
    fun rewriteQueryForSearch(
        originalQuestion: String,
        maxOutputTokens: Int,
    ): String?

    fun rerankChunks(
        question: String,
        candidates: List<RetrievedKnowledge>,
        finalCount: Int,
        maxOutputTokens: Int,
    ): List<RetrievedKnowledge>?
}
