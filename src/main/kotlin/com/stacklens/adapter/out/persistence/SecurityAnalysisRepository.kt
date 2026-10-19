package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.SecurityAnalysisPort
import com.stacklens.domain.EntityConcurrentlyModifiedException
import com.stacklens.domain.model.SecurityAnalysis
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Repository

@Repository
interface SecurityAnalysisRepository :
    JpaRepository<SecurityAnalysis, Long>,
    SecurityAnalysisPort {
    @Query("SELECT e FROM SecurityAnalysis e WHERE e.id = :id")
    override fun findSecurityAnalysisById(
        @Param("id") id: Long,
    ): SecurityAnalysis?

    override fun findAllSecurityAnalysesOrderByCreatedAtDesc(): List<SecurityAnalysis> =
        findAll(
            org.springframework.data.domain.Sort
                .by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"),
        )

    override fun findRecentSecurityAnalyses(limit: Int): List<SecurityAnalysis> =
        findAll(
            PageRequest.of(
                0,
                limit,
                org.springframework.data.domain.Sort
                    .by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"),
            ),
        ).content

    override fun saveSecurityAnalysis(analysis: SecurityAnalysis): SecurityAnalysis =
        try {
            save(analysis)
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw EntityConcurrentlyModifiedException("SecurityAnalysis ${analysis.id}")
        }

    override fun deleteSecurityAnalysisById(id: Long) = deleteById(id)

    override fun deleteAllSecurityAnalyses() = deleteAll()
}
