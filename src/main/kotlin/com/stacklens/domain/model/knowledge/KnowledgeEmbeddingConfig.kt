package com.stacklens.domain.model.knowledge

import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "knowledge_embedding_config")
class KnowledgeEmbeddingConfig(
    @Id
    val id: Long = 1L,
    @Column(nullable = false)
    var enabled: Boolean = true,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var provider: KnowledgeEmbeddingProvider = KnowledgeEmbeddingProvider.OLLAMA,
    @Column(nullable = false, length = 300)
    var model: String = "nomic-embed-text",
    @Column(name = "ollama_url", nullable = false, length = 500)
    var ollamaUrl: String = "http://localhost:11434",
    @Column(name = "openai_api_key", length = 1000)
    var openAiApiKey: String? = null,
    @Column(name = "openrouter_api_key", length = 1000)
    var openRouterApiKey: String? = null,
    @Column(name = "chunk_size", nullable = false)
    var chunkSize: Int = 1600,
    @Column(name = "chunk_overlap", nullable = false)
    var chunkOverlap: Int = 200,
    @Column(name = "top_k", nullable = false)
    var topK: Int = 8,
    @Column(name = "minimum_bm25_score", nullable = false)
    var minimumBm25Score: Double = 0.0,
    @Column(name = "maximum_context_characters", nullable = false)
    var maximumContextCharacters: Int = 12000,
    @Column(name = "auto_index_review", nullable = false)
    var autoIndexReview: Boolean = true,
    @Column(name = "auto_index_sprint_review", nullable = false)
    var autoIndexSprintReview: Boolean = true,
    @Column(name = "auto_index_ticket_creation", nullable = false)
    var autoIndexTicketCreation: Boolean = true,
    @Column(name = "auto_index_ticket_refinement", nullable = false)
    var autoIndexTicketRefinement: Boolean = true,
    @Column(name = "auto_index_ticket_summary", nullable = false)
    var autoIndexTicketSummary: Boolean = true,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "queryRewritingEnabled", column = Column(name = "post_query_rewriting_enabled", nullable = false)),
        AttributeOverride(name = "queryRewriteMaxOutputTokens", column = Column(name = "post_query_rewrite_max_output_tokens", nullable = false)),
        AttributeOverride(name = "rerankEnabled", column = Column(name = "post_rerank_enabled", nullable = false)),
        AttributeOverride(name = "rerankCandidateCount", column = Column(name = "post_rerank_candidate_count", nullable = false)),
        AttributeOverride(name = "rerankFinalCount", column = Column(name = "post_rerank_final_count", nullable = false)),
        AttributeOverride(name = "rerankMaxOutputTokens", column = Column(name = "post_rerank_max_output_tokens", nullable = false)),
        AttributeOverride(name = "rerankModel", column = Column(name = "post_rerank_model", nullable = false, length = 200)),
    )
    var postRetrieval: KnowledgeRerankConfig = KnowledgeRerankConfig.DISABLED,
) {
    fun autoIndexFor(sourceType: KnowledgeSourceType): Boolean =
        when (sourceType) {
            KnowledgeSourceType.REVIEW_ANALYSIS -> autoIndexReview
            KnowledgeSourceType.SPRINT_REVIEW -> autoIndexSprintReview
            KnowledgeSourceType.TICKET_CREATION -> autoIndexTicketCreation
            KnowledgeSourceType.TICKET_REFINEMENT -> autoIndexTicketRefinement
            KnowledgeSourceType.TICKET_SUMMARY -> autoIndexTicketSummary
            KnowledgeSourceType.MANUAL,
            KnowledgeSourceType.FILE,
            KnowledgeSourceType.CONFLUENCE,
            KnowledgeSourceType.JIRA,
            KnowledgeSourceType.JIRA_SPRINT,
            -> true
        }

    constructor() : this(
        id = 1L,
        enabled = true,
        provider = KnowledgeEmbeddingProvider.OLLAMA,
        model = "nomic-embed-text",
        ollamaUrl = "http://localhost:11434",
        openAiApiKey = null,
        openRouterApiKey = null,
        chunkSize = 1600,
        chunkOverlap = 200,
        topK = 8,
        minimumBm25Score = 0.0,
        maximumContextCharacters = 12000,
        autoIndexReview = true,
        autoIndexSprintReview = true,
        autoIndexTicketCreation = true,
        autoIndexTicketRefinement = true,
        autoIndexTicketSummary = true,
        postRetrieval = KnowledgeRerankConfig.DISABLED,
    )
}
