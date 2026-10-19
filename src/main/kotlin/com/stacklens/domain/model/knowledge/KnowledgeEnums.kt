package com.stacklens.domain.model.knowledge

enum class KnowledgeSourceType {
    MANUAL,
    FILE,
    CONFLUENCE,
    JIRA,
    JIRA_SPRINT,
    REVIEW_ANALYSIS,
    SPRINT_REVIEW,
    TICKET_CREATION,
    TICKET_REFINEMENT,
    TICKET_SUMMARY,
}

enum class KnowledgeSourceStatus {
    PENDING,
    READY,
    STALE,
    FAILED,
    DISABLED,
}

enum class KnowledgeDocumentStatus {
    PENDING,
    READY,
    STALE,
    FAILED,
}

enum class KnowledgeJobStatus {
    QUEUED,
    FETCHING,
    EXTRACTING,
    CHUNKING,
    EMBEDDING,
    INDEXING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class KnowledgeEmbeddingProvider {
    OPENAI,
    OPENROUTER,
    OLLAMA,
}
