package com.stacklens.adapter.out.persistence

import com.stacklens.domain.model.AiTokenUsageEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface AiTokenUsageRepository : JpaRepository<AiTokenUsageEntity, Long> {
    fun findByAnalysisKindAndAnalysisIdOrderByCallIndexAsc(
        kind: String,
        analysisId: Long,
    ): List<AiTokenUsageEntity>

    @Modifying
    @Query("DELETE FROM AiTokenUsageEntity e WHERE e.analysisKind = :kind AND e.analysisId = :analysisId")
    fun deleteByKindAndAnalysisId(
        @Param("kind") kind: String,
        @Param("analysisId") analysisId: Long,
    ): Int
}
