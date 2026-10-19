package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.SqlAnalysisPort
import com.stacklens.domain.model.SqlAnalysisRecord
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SqlAnalysisRepository :
    JpaRepository<SqlAnalysisRecord, Long>,
    SqlAnalysisPort {
    override fun findAllByOrderByCreatedAtDesc(): List<SqlAnalysisRecord>

    override fun findRecentSqlAnalyses(limit: Int): List<SqlAnalysisRecord> = findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).content

    @Query("SELECT e FROM SqlAnalysisRecord e WHERE e.id = :id")
    override fun findSqlAnalysisById(
        @Param("id") id: Long,
    ): SqlAnalysisRecord?

    override fun saveSqlAnalysis(record: SqlAnalysisRecord): SqlAnalysisRecord = save(record)

    override fun deleteSqlAnalysisById(id: Long) = deleteById(id)

    override fun deleteAllSqlAnalyses() = deleteAll()
}
