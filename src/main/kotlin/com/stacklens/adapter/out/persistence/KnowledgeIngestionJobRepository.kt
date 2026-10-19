package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort
import com.stacklens.domain.model.knowledge.KnowledgeIngestionJob
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface KnowledgeIngestionJobRepository :
    JpaRepository<KnowledgeIngestionJob, Long>,
    KnowledgeIngestionJobPort {
    override fun findAllByOrderByCreatedAtDesc(): List<KnowledgeIngestionJob>

    override fun deleteAll()
}
