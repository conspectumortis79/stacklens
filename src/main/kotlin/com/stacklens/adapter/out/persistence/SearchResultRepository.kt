package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.SearchResult
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SearchResultRepository :
    JpaRepository<SearchResult, Long>,
    SearchResultPort {
    override fun findBySessionIdOrderBySourceTypeAscCreatedAtAsc(sessionId: Long): List<SearchResult>

    override fun findBySessionIdAndSourceType(
        sessionId: Long,
        sourceType: SourceType,
    ): List<SearchResult>

    override fun countBySessionId(sessionId: Long): Long

    override fun saveAllResults(results: Iterable<SearchResult>): List<SearchResult> = saveAll(results)

    override fun deleteAllResults(results: Iterable<SearchResult>) = deleteAll(results)

    @Modifying
    @Query("DELETE FROM SearchResult r WHERE r.sessionId IN :sessionIds")
    override fun deleteBySessionIdIn(
        @Param("sessionIds") sessionIds: List<Long>,
    ): Long

    @Query(
        "SELECT r FROM SearchResult r WHERE r.sessionId = :sessionId AND r.sourceType = :sourceType ORDER BY r.createdAt ASC",
    )
    fun findCappedBySessionIdAndSourceType(
        @Param("sessionId") sessionId: Long,
        @Param("sourceType") sourceType: SourceType,
        pageable: org.springframework.data.domain.Pageable,
    ): List<SearchResult>

    override fun findCappedBySessionId(
        sessionId: Long,
        perSourceLimit: Int,
    ): Map<SourceType, List<SearchResult>> {
        val pageable =
            org.springframework.data.domain.PageRequest
                .of(0, perSourceLimit)
        return SourceType.values().associateWith { src ->
            findCappedBySessionIdAndSourceType(sessionId, src, pageable)
        }
    }
}
