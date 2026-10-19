package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface KnowledgeBaseRepository :
    JpaRepository<KnowledgeBase, Long>,
    KnowledgeBasePort {
    override fun findAllByOrderByCreatedAtAsc(): List<KnowledgeBase>

    override fun findAllByActiveTrue(): List<KnowledgeBase>

    override fun findByStableId(stableId: String): KnowledgeBase?

    override fun deleteAll()
}
