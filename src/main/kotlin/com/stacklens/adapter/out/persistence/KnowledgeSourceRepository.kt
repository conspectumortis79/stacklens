package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface KnowledgeSourceRepository :
    JpaRepository<KnowledgeSource, Long>,
    KnowledgeSourcePort {
    override fun findAllByOrderByCreatedAtAsc(): List<KnowledgeSource>

    override fun findByStableId(stableId: String): KnowledgeSource?

    override fun findByKnowledgeBaseId(knowledgeBaseId: Long): List<KnowledgeSource>

    override fun findFirstByTypeAndExternalReference(
        type: KnowledgeSourceType,
        externalReference: String,
    ): KnowledgeSource?

    @Modifying
    @Query("DELETE FROM KnowledgeSource s WHERE s.knowledgeBaseId = :knowledgeBaseId")
    override fun deleteByKnowledgeBaseId(
        @Param("knowledgeBaseId") knowledgeBaseId: Long,
    )

    @Modifying
    @Query("DELETE FROM KnowledgeSource s WHERE s.id = :id")
    override fun deleteById(
        @Param("id") id: Long,
    )

    override fun deleteAll()
}
