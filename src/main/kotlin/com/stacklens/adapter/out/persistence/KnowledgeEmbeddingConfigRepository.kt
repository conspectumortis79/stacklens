package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface KnowledgeEmbeddingConfigRepository :
    JpaRepository<KnowledgeEmbeddingConfig, Long>,
    KnowledgeEmbeddingConfigPort {
    override fun get(): KnowledgeEmbeddingConfig = findById(1L).orElse(KnowledgeEmbeddingConfig())

    override fun delete() = deleteById(1L)
}
