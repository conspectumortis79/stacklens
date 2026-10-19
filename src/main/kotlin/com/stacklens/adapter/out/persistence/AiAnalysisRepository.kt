package com.stacklens.adapter.out.persistence

import com.stacklens.application.port.out.AiAnalysisPort
import com.stacklens.domain.model.AiAnalysis
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface AiAnalysisRepository :
    JpaRepository<AiAnalysis, Long>,
    AiAnalysisPort {
    override fun findTopBySessionIdOrderByCreatedAtDesc(sessionId: Long): AiAnalysis?

    @Query("SELECT e FROM AiAnalysis e WHERE e.id = :id")
    override fun findAiAnalysisById(
        @Param("id") id: Long,
    ): AiAnalysis?

    override fun saveAiAnalysis(analysis: AiAnalysis): AiAnalysis = save(analysis)

    @Query("SELECT e FROM AiAnalysis e WHERE e.status IN :statuses")
    override fun findByStatusIn(
        @Param("statuses") statuses: List<String>,
    ): List<AiAnalysis>

    override fun deleteAllAiAnalyses() = deleteAll()
}
