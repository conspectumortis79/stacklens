package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.knowledge.KnowledgeIndexGenerationPort
import com.stacklens.domain.model.knowledge.KnowledgeIndexGeneration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface KnowledgeIndexGenerationRepository :
    JpaRepository<KnowledgeIndexGeneration, Long>,
    KnowledgeIndexGenerationPort {
    @Modifying
    @Query("DELETE FROM KnowledgeIndexGeneration g WHERE g.knowledgeBaseId = :knowledgeBaseId")
    override fun deleteByKnowledgeBaseId(
        @Param("knowledgeBaseId") knowledgeBaseId: Long,
    )

    override fun deleteAll()
}
