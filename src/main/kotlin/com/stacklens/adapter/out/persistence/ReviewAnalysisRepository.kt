package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.ReviewAnalysisPort
import com.stacklens.domain.model.ReviewAnalysis
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ReviewAnalysisRepository :
    JpaRepository<ReviewAnalysis, Long>,
    ReviewAnalysisPort {
    override fun findAllByOrderByCreatedAtDesc(): List<ReviewAnalysis>

    override fun findRecentReviewAnalyses(limit: Int): List<ReviewAnalysis> = findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).content

    @Query("SELECT e FROM ReviewAnalysis e WHERE e.id = :id")
    override fun findReviewAnalysisById(
        @Param("id") id: Long,
    ): ReviewAnalysis?

    override fun saveReviewAnalysis(analysis: ReviewAnalysis): ReviewAnalysis = save(analysis)

    override fun deleteReviewAnalysisById(id: Long) = deleteById(id)

    override fun deleteAllReviewAnalyses() = deleteAll()
}
