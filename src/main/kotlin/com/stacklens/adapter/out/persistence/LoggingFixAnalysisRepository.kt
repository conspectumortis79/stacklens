package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.LoggingFixPort
import com.stacklens.domain.model.LoggingFixAnalysis
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface LoggingFixAnalysisRepository :
    JpaRepository<LoggingFixAnalysis, Long>,
    LoggingFixPort {
    @Query("SELECT e FROM LoggingFixAnalysis e WHERE e.id = :id")
    override fun findLoggingFixAnalysisById(
        @Param("id") id: Long,
    ): LoggingFixAnalysis?

    override fun saveLoggingFixAnalysis(analysis: LoggingFixAnalysis): LoggingFixAnalysis = save(analysis)

    override fun findAllByOrderByCreatedAtDesc(): List<LoggingFixAnalysis>

    override fun findRecentLoggingFixAnalyses(limit: Int): List<LoggingFixAnalysis> = findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).content

    override fun deleteLoggingFixAnalysisById(id: Long) = deleteById(id)

    override fun deleteAllLoggingFixAnalyses() = deleteAll()

    @Modifying
    @Query(
        "DELETE FROM LoggingFixAnalysis e WHERE e.id > 0 " +
            "AND e.status <> 'RUNNING' AND e.status <> 'PENDING'",
    )
    override fun deleteFinishedLoggingFixAnalyses(): Int
}
