package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface KnowledgeDocumentRepository :
    JpaRepository<KnowledgeDocument, Long>,
    KnowledgeDocumentPort {
    override fun findByStableId(stableId: String): KnowledgeDocument?

    override fun findBySourceIdOrderByIdAsc(sourceId: Long): List<KnowledgeDocument>

    override fun findByKnowledgeBaseId(knowledgeBaseId: Long): List<KnowledgeDocument>

    @Modifying
    @Query("DELETE FROM KnowledgeDocument d WHERE d.knowledgeBaseId = :knowledgeBaseId")
    override fun deleteByKnowledgeBaseId(
        @Param("knowledgeBaseId") knowledgeBaseId: Long,
    )

    @Modifying
    @Query("DELETE FROM KnowledgeDocument d WHERE d.id = :id")
    override fun deleteById(
        @Param("id") id: Long,
    )

    @Modifying
    @Query("DELETE FROM KnowledgeDocument d WHERE d.sourceId = :sourceId")
    override fun deleteBySourceId(
        @Param("sourceId") sourceId: Long,
    ): Int

    override fun deleteAll()
}
