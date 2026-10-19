package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.domain.model.knowledge.KnowledgeChunk
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface KnowledgeChunkRepository :
    JpaRepository<KnowledgeChunk, Long>,
    KnowledgeChunkPort {
    override fun findByDocumentIdOrderByOrdinalAsc(documentId: Long): List<KnowledgeChunk>

    override fun findByKnowledgeBaseId(knowledgeBaseId: Long): List<KnowledgeChunk>

    @Modifying
    @Query("DELETE FROM KnowledgeChunk c WHERE c.documentId = :documentId")
    override fun deleteByDocumentId(
        @Param("documentId") documentId: Long,
    )

    @Modifying
    @Query("DELETE FROM KnowledgeChunk c WHERE c.knowledgeBaseId = :knowledgeBaseId")
    override fun deleteByKnowledgeBaseId(
        @Param("knowledgeBaseId") knowledgeBaseId: Long,
    )

    override fun deleteAll()

    @Query(
        "SELECT s.id, SUM(LENGTH(c.content)) " +
            "FROM KnowledgeSource s " +
            "JOIN KnowledgeDocument d ON d.sourceId = s.id " +
            "JOIN KnowledgeChunk c ON c.documentId = d.id " +
            "WHERE s.id IN :sourceIds " +
            "GROUP BY s.id",
    )
    fun aggregateContentLengthBySourceIdsRaw(
        @Param("sourceIds") sourceIds: Collection<Long>,
    ): List<Array<Any>>

    override fun sumContentLengthBySourceIds(sourceIds: Collection<Long>): Map<Long, Long> {
        if (sourceIds.isEmpty()) return emptyMap()
        val raw = aggregateContentLengthBySourceIdsRaw(sourceIds)
        val out = HashMap<Long, Long>(raw.size)
        for (row in raw) {
            val id = (row[0] as Number).toLong()
            val total = (row[1] as? Number)?.toLong() ?: 0L
            out[id] = total
        }
        return out
    }
}
