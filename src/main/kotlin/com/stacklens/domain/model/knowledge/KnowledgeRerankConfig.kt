package com.stacklens.domain.model.knowledge

import jakarta.persistence.Embeddable

@Embeddable
data class KnowledgeRerankConfig(
    var queryRewritingEnabled: Boolean = false,
    var queryRewriteMaxOutputTokens: Int = 256,
    var rerankEnabled: Boolean = false,
    var rerankCandidateCount: Int = 20,
    var rerankFinalCount: Int = 5,
    var rerankMaxOutputTokens: Int = 512,
    var rerankModel: String = "auto",
) {
    init {
        require(queryRewriteMaxOutputTokens in 32..2048) {
            "queryRewriteMaxOutputTokens must be in 32..2048, was $queryRewriteMaxOutputTokens"
        }
        require(rerankMaxOutputTokens in 32..2048) {
            "rerankMaxOutputTokens must be in 32..2048, was $rerankMaxOutputTokens"
        }
        require(rerankCandidateCount in 1..200) {
            "rerankCandidateCount must be in 1..200, was $rerankCandidateCount"
        }
        require(rerankFinalCount in 1..50) {
            "rerankFinalCount must be in 1..50, was $rerankFinalCount"
        }
        require(rerankFinalCount <= rerankCandidateCount) {
            "rerankFinalCount ($rerankFinalCount) must be <= rerankCandidateCount ($rerankCandidateCount)"
        }
    }

    constructor() : this(
        queryRewritingEnabled = false,
        queryRewriteMaxOutputTokens = 256,
        rerankEnabled = false,
        rerankCandidateCount = 20,
        rerankFinalCount = 5,
        rerankMaxOutputTokens = 512,
        rerankModel = "auto",
    )

    companion object {
        val DISABLED: KnowledgeRerankConfig =
            KnowledgeRerankConfig(
                queryRewritingEnabled = false,
                queryRewriteMaxOutputTokens = 256,
                rerankEnabled = false,
                rerankCandidateCount = 20,
                rerankFinalCount = 5,
                rerankMaxOutputTokens = 512,
                rerankModel = "auto",
            )
    }
}
