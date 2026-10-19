package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.domain.model.ReviewAnalysisConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ReviewAnalysisConfigRepository :
    JpaRepository<ReviewAnalysisConfig, Long>,
    ReviewAnalysisConfigPort {
    @Query("SELECT e FROM ReviewAnalysisConfig e ORDER BY e.id ASC")
    override fun findReviewAnalysisConfig(): ReviewAnalysisConfig?

    override fun saveReviewAnalysisConfig(config: ReviewAnalysisConfig): ReviewAnalysisConfig = save(config)
}
